package com.example.bloodpressurerecord.update

import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Only update metadata is represented here; this protocol never carries health records. */
data class UpdateManifest(
    val schema: Int = 1,
    val version: String,
    val versionCode: Long,
    val apk: String,
    val size: Long,
    val sha256: String,
    val url: String,
    val notes: String,
    val releasesUrl: String,
    val publishedAt: String,
    val minSupportedVersionCode: Long? = null,
    val mirrors: List<String> = emptyList()
) {
    val identity: String get() = "$versionCode|$url|$size|${sha256.lowercase()}"

    fun encode(): String = buildJsonObject {
        put("schema", schema)
        put("version", version)
        put("versionCode", versionCode)
        put("apk", apk)
        put("size", size)
        put("sha256", sha256)
        put("url", url)
        put("notes", notes)
        put("releasesUrl", releasesUrl)
        put("publishedAt", publishedAt)
        minSupportedVersionCode?.let { put("minSupportedVersionCode", it) }
        if (mirrors.isNotEmpty()) put("mirrors", JsonArray(mirrors.map { JsonPrimitive(it) }))
    }.toString()

    companion object {
        const val MAX_JSON_BYTES = 256 * 1024
        private val json = Json { isLenient = false; ignoreUnknownKeys = true }
        private val versionPattern = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")

        fun parse(text: String): UpdateManifest {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "更新清单过大" }
            val root = json.parseToJsonElement(text) as? JsonObject
                ?: throw IllegalArgumentException("更新清单必须为对象")
            fun string(name: String): String {
                val value = root[name] as? JsonPrimitive
                require(value != null && value.isString) { "更新清单字段 $name 格式错误" }
                return value.content
            }
            fun integer(name: String): Long {
                val value = root[name] as? JsonPrimitive
                require(value != null && !value.isString && Regex("0|[1-9][0-9]*").matches(value.content)) {
                    "更新清单字段 $name 必须为整数"
                }
                return value.content.toLongOrNull()
                    ?: throw IllegalArgumentException("更新清单字段 $name 超出范围")
            }
            require(integer("schema") == 1L) { "暂不支持此更新协议" }
            val version = string("version")
            require(versionPattern.matches(version)) { "更新版本格式错误" }
            val versionCode = integer("versionCode")
            require(versionCode > 0) { "更新版本编号无效" }
            val size = integer("size")
            require(size in 1..UpdateUrls.MAX_APK_BYTES) { "安装包大小无效" }
            val sha256 = string("sha256")
            require(Regex("[a-fA-F0-9]{64}").matches(sha256)) { "安装包摘要格式错误" }
            val apk = string("apk")
            val url = string("url")
            UpdateUrls.validateApk(url, version, apk)
            val mirrors = root["mirrors"]?.let { value ->
                val array = value as? JsonArray
                    ?: throw IllegalArgumentException("更新清单字段 mirrors 必须为字符串数组")
                array.map { item ->
                    val mirror = item as? JsonPrimitive
                    require(mirror != null && mirror.isString) { "更新清单字段 mirrors 必须为字符串数组" }
                    mirror.content.also { UpdateUrls.validateMirror(it, version, apk) }
                }
            }.orEmpty()
            val release = string("releasesUrl")
            UpdateUrls.validateRelease(release)
            val published = string("publishedAt")
            try { Instant.parse(published) } catch (error: java.time.DateTimeException) {
                throw IllegalArgumentException("发布时间格式错误", error)
            }
            val minimum = if (root["minSupportedVersionCode"] == null || root["minSupportedVersionCode"] == JsonNull) {
                null
            } else {
                integer("minSupportedVersionCode").also {
                    require(it in 1..versionCode) { "最低支持版本编号无效" }
                }
            }
            return UpdateManifest(
                version = version, versionCode = versionCode, apk = apk, size = size,
                sha256 = sha256.lowercase(), url = url, notes = string("notes"),
                releasesUrl = release, publishedAt = published, minSupportedVersionCode = minimum,
                mirrors = mirrors
            )
        }
    }
}

object UpdateUrls {
    const val HOST = "updates.0u0w0u0.com"
    const val DIRECT_HOST = "dl.0u0w0u0.com"
    const val MANIFEST_URL = "https://updates.0u0w0u0.com/android/stable/latest.json"
    const val RELEASES_URL = "https://github.com/yy611185/blood-pressure-record/releases"
    const val MAX_APK_BYTES = 256L * 1024 * 1024

    fun validateApk(url: String, version: String, apk: String) {
        validateApkEndpoint(url, version, apk, HOST)
    }

    fun validateDirectApk(url: String, version: String, apk: String) {
        validateApkEndpoint(url, version, apk, "$DIRECT_HOST:8443")
    }

    fun validateMirror(url: String, version: String, apk: String) {
        if (parseUri(url).host == DIRECT_HOST) validateDirectApk(url, version, apk)
        else validateApk(url, version, apk)
    }

    /** Only an explicitly advertised, independently validated direct mirror gets a separate action. */
    fun directDownload(manifest: UpdateManifest): String? = manifest.mirrors.firstOrNull { url ->
        runCatching { validateDirectApk(url, manifest.version, manifest.apk) }.isSuccess
    }

    private fun validateApkEndpoint(url: String, version: String, apk: String, authority: String) {
        val expected = "blood-pressure-record-v$version-release.apk"
        require(apk == expected && Regex("[A-Za-z0-9.-]+\\.apk").matches(apk)) { "安装包文件名无效" }
        val uri = parseUri(url)
        // Exact authority also rejects alternate port spellings, user info and encoded hosts.
        require(uri.scheme == "https" && uri.rawAuthority == authority &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "更新地址必须为可信 HTTPS 地址"
        }
        require(uri.rawPath == "/android/stable/$expected") { "安装包地址超出可信范围" }
    }

    fun validateRelease(url: String) {
        val uri = trustedUri(url, "github.com")
        require(Regex("/yy611185/blood-pressure-record/releases(?:/tag/[A-Za-z0-9._-]+)?").matches(uri.rawPath ?: "")) {
            "备用发布地址无效"
        }
    }

    private fun trustedUri(url: String, host: String): URI {
        val uri = parseUri(url)
        require(uri.scheme == "https" && uri.rawAuthority == host && uri.host == host && uri.port == -1 &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "更新地址必须为可信 HTTPS 地址"
        }
        return uri
    }

    private fun parseUri(url: String): URI = try { URI(url) } catch (error: URISyntaxException) {
        throw IllegalArgumentException("更新地址格式错误", error)
    }
}
