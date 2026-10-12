package com.example.bloodpressurerecord.update

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateManifestTest {
    private val fixture = requireNotNull(javaClass.getResource("/update/stable-schema1.json")).readText()

    @Test fun `valid fixture round trips and minimum stays absent`() {
        val manifest = UpdateManifest.parse(fixture)
        assertEquals(44L, manifest.versionCode)
        assertNull(manifest.minSupportedVersionCode)
        assertEquals(manifest, UpdateManifest.parse(manifest.encode()))
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
            manifest.url.replace("/android/stable/", "/android/stable/../"),
            manifest.url.replace("/android/stable/", "/android/stable/%2e%2e/"),
            manifest.url + "?token=123", manifest.url + "#download",
            manifest.url.replace("/android/stable/", "/android/ stable/"),
            manifest.url.replace("/android/stable/", "/android/%GG/")
        ).forEach { invalid(fixture.replace(manifest.url, it)) }
        invalid(fixture.replace("/yy611185/blood-pressure-record/", "/another/repository/"))
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
