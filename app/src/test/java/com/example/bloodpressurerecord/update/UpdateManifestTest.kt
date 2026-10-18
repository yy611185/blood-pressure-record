package com.example.bloodpressurerecord.update

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Test

class UpdateManifestTest {
    private val fixture = requireNotNull(javaClass.getResource("/update/stable-schema1.json")).readText()

    @Test fun `valid fixture round trips and minimum stays absent`() {
        val manifest = UpdateManifest.parse(fixture)
        assertEquals(44L, manifest.versionCode)
        assertNull(manifest.minSupportedVersionCode)
        assertEquals(manifest, UpdateManifest.parse(manifest.encode()))
    }

    @Test fun `legacy manifest retains every field and omits mirrors when encoded`() {
        val manifest = UpdateManifest.parse(fixture)
        assertEquals(UpdateManifest(version = "2.3.3", versionCode = 44L,
            apk = "blood-pressure-record-v2.3.3-release.apk", size = 12345678L,
            sha256 = "0123456789abcdef".repeat(4),
            url = "https://updates.0u0w0u0.com/android/stable/blood-pressure-record-v2.3.3-release.apk",
            notes = "本地固定测试样本，仅用于客户端解析和策略测试。", releasesUrl = "https://github.com/yy611185/blood-pressure-record/releases/tag/v2.3.3",
            publishedAt = "2026-10-11T00:00:00Z"), manifest)
        assertFalse(manifest.encode().contains("mirrors"))
        assertEquals(manifest, UpdateManifest.parse(add("\"mirrors\": []")))
    }

    @Test fun `primary and direct download use separate exact HTTPS endpoints`() {
        val manifest = UpdateManifest.parse(fixture)
        val path = "/android/stable/${manifest.apk}"
        UpdateUrls.validateApk("https://${UpdateUrls.HOST}$path", manifest.version, manifest.apk)
        UpdateUrls.validateDirectApk("https://${UpdateUrls.DIRECT_HOST}:8443$path", manifest.version, manifest.apk)
        assertThrows(IllegalArgumentException::class.java) {
            UpdateUrls.validateApk("https://${UpdateUrls.DIRECT_HOST}:8443$path", manifest.version, manifest.apk)
        }
        assertThrows(IllegalArgumentException::class.java) {
            UpdateUrls.validateDirectApk(manifest.url, manifest.version, manifest.apk)
        }
        listOf(
            "http://${UpdateUrls.HOST}$path", "http://${UpdateUrls.DIRECT_HOST}:8443$path",
            "https://other.example$path", "https://${UpdateUrls.HOST}:443$path",
            "https://${UpdateUrls.HOST}:8443$path", "https://${UpdateUrls.DIRECT_HOST}$path",
            "https://${UpdateUrls.DIRECT_HOST}:443$path", "https://${UpdateUrls.DIRECT_HOST}:8444$path",
            "https://${UpdateUrls.DIRECT_HOST}:08443$path", "https://${UpdateUrls.DIRECT_HOST}:8443$path?x=1",
            "https://user@${UpdateUrls.DIRECT_HOST}:8443$path", "https://${UpdateUrls.DIRECT_HOST}:8443$path#x",
            "https://${UpdateUrls.DIRECT_HOST}:8443/android/stable/another.apk"
        ).forEach { url ->
            assertThrows("Must reject $url", IllegalArgumentException::class.java) {
                UpdateUrls.validateMirror(url, manifest.version, manifest.apk)
            }
        }
    }

    @Test fun `explicit direct mirror stays separate from the preferred manifest URL`() {
        val base = UpdateManifest.parse(fixture)
        val direct = base.url.replace(UpdateUrls.HOST, "${UpdateUrls.DIRECT_HOST}:8443")
        val manifest = UpdateManifest.parse(add("\"mirrors\": [\"$direct\", \"${base.url}\"]"))
        assertEquals(base.url, manifest.url)
        assertEquals(direct, UpdateUrls.directDownload(manifest))
        assertNull(UpdateUrls.directDownload(base))
        assertEquals(base.identity, manifest.identity)
        assertEquals(manifest, UpdateManifest.parse(manifest.encode()))
        invalid(fixture.replace(base.url, direct))
    }

    @Test fun `mirrors reject wrong field types non strings and untrusted URLs`() {
        listOf("null", "{}", "\"url\"", "[null]", "[1]", "[{}]", "[true]",
            "[\"https://evil.example/file.apk\"]").forEach { invalid(add("\"mirrors\": $it")) }
        val base = UpdateManifest.parse(fixture)
        val valid = base.url.replace(UpdateUrls.HOST, "${UpdateUrls.DIRECT_HOST}:8443")
        invalid(add("\"mirrors\": [\"$valid\", \"$valid?token=1\"]"))
    }

    @Test fun `unknown fields remain forward compatible`() {
        val extended = fixture.replace("\"schema\": 1", "\"schema\": 1, \"future\": {\"enabled\": true}")
        assertEquals(UpdateManifest.parse(fixture), UpdateManifest.parse(extended))
    }

    @Test fun `minimum accepts null or positive code but rejects malformed and above latest`() {
        assertNull(UpdateManifest.parse(add("\"minSupportedVersionCode\": null")).minSupportedVersionCode)
        assertEquals(43L, UpdateManifest.parse(add("\"minSupportedVersionCode\": 43")).minSupportedVersionCode)
        listOf("0", "-1", "45", "\"43\"", "43.5", "true").forEach {
            invalid(add("\"minSupportedVersionCode\": $it"))
        }
    }

    @Test fun `unsupported schema wrong types and missing mandatory field are rejected`() {
        invalid(fixture.replace("\"schema\": 1", "\"schema\": 2"))
        invalid(fixture.replace("\"schema\": 1", "\"schema\": \"1\""))
        listOf("0", "-1", "44.0", "4.4e1", "\"44\"", "true", "null", "9223372036854775808").forEach {
            invalid(fixture.replace("\"versionCode\": 44", "\"versionCode\": $it"))
        }
        invalid(fixture.replace("\"notes\"", "\"notNotes\""))
        invalid("[]")
        invalid("{ malformed")
    }

    @Test fun `APK maximum size and exact versioned filename are enforced`() {
        invalid(fixture.replace("12345678", "0"))
        invalid(fixture.replace("12345678", "${UpdateUrls.MAX_APK_BYTES + 1}"))
        invalid(fixture.replace("2.3.3", "2.3.3-debug"))
        invalid(fixture.replace("\"apk\": \"blood-pressure-record-v2.3.3-release.apk\"", "\"apk\": \"latest.apk\""))
        invalid(fixture.replace("0123456789abcdef", "G123456789abcdef"))
    }

    @Test fun `untrusted download or release URLs are rejected`() {
        val manifest = UpdateManifest.parse(fixture)
        listOf(
            manifest.url.replace("https:", "http:"),
            manifest.url.replace(UpdateUrls.HOST, "evil.example"),
            manifest.url.replace(UpdateUrls.HOST, "${UpdateUrls.HOST}:443"),
            manifest.url.replace(UpdateUrls.HOST, "user@${UpdateUrls.HOST}"),
            manifest.url.replace(UpdateUrls.HOST, "${UpdateUrls.HOST}.evil.example"),
            manifest.url.replace(UpdateUrls.HOST, "18.162.102.13"),
            manifest.url.replace("https:", "intent:"),
            manifest.url.replace("https:", "file:"),
            manifest.url.replace("https:", "javascript:"),
            manifest.url.replace("/android/stable/", "/android/beta/"),
            manifest.url.replace("blood-pressure", "%62lood-pressure"),
            manifest.url.replace("release.apk", "release.apk/extra"),
            manifest.url.replace("/android/stable/", "/android/stable/../"),
            manifest.url.replace("/android/stable/", "/android/stable/%2e%2e/"),
            manifest.url + "?token=123", manifest.url + "#download",
            manifest.url.replace("/android/stable/", "/android/ stable/"),
            manifest.url.replace("/android/stable/", "/android/%GG/")
        ).forEach { invalid(fixture.replace(manifest.url, it)) }
        invalid(fixture.replace("/yy611185/blood-pressure-record/", "/another/repository/"))
    }

    @Test fun `fixed GitHub fallback is valid without a manifest and rejects lookalikes`() {
        UpdateUrls.validateRelease(UpdateUrls.RELEASES_URL)
        listOf(
            UpdateUrls.RELEASES_URL.replace("https:", "http:"),
            UpdateUrls.RELEASES_URL.replace("github.com", "github.com.evil.example"),
            UpdateUrls.RELEASES_URL.replace("github.com", "user@github.com"),
            UpdateUrls.RELEASES_URL.replace("github.com", "github.com:443"),
            UpdateUrls.RELEASES_URL.replace("github.com", "github.com:"),
            UpdateUrls.RELEASES_URL + "?next=evil", UpdateUrls.RELEASES_URL + "#download",
            UpdateUrls.RELEASES_URL.replace("/releases", "/issues")
        ).forEach { url ->
            assertThrows("Must reject $url", IllegalArgumentException::class.java) { UpdateUrls.validateRelease(url) }
        }
    }

    @Test fun `invalid publication time and oversized JSON are rejected`() {
        invalid(fixture.replace("2026-10-11T00:00:00Z", "yesterday"))
        invalid(add("\"unknown\": \"${"a".repeat(UpdateManifest.MAX_JSON_BYTES)}\""))
    }

    @Test fun `stream limit is applied to actual bytes`() {
        assertEquals(16, readBounded(ByteArrayInputStream(ByteArray(16)), 16).size)
        assertThrows(UpdateRequestException::class.java) { readBounded(ByteArrayInputStream(ByteArray(17)), 16) }
    }

    private fun add(field: String) = fixture.replaceFirst("{", "{ $field,")
    private fun invalid(json: String) { assertThrows(IllegalArgumentException::class.java) { UpdateManifest.parse(json) } }
}
