package com.example.bloodpressurerecord.data.repository.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {

    private val passphrase = "正确口令-123456".toCharArray()

    @Test
    fun encryptDecrypt_roundTripsPlaintext() {
        val plaintext = "血压备份内容-blood-pressure-backup".toByteArray(Charsets.UTF_8)

        val container = BackupCrypto.encrypt(plaintext, passphrase)
        val decrypted = BackupCrypto.decrypt(container, passphrase)

        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun encrypt_producesRecognizableContainerWithHeader() {
        val container = BackupCrypto.encrypt("data".toByteArray(), passphrase)

        assertTrue(BackupCrypto.isEncryptedContainer(container))
        assertEquals(BackupCrypto.FORMAT_VERSION, container[4].toInt())
        assertEquals(
            BackupCrypto.KDF_PBKDF2_HMAC_SHA256,
            container[5].toInt()
        )
        assertEquals(BackupCrypto.HEADER_SIZE + 4 + 16, container.size)
    }

    @Test
    fun isEncryptedContainer_rejectsNonContainerBytes() {
        assertFalse(BackupCrypto.isEncryptedContainer(ByteArray(0)))
        assertFalse(BackupCrypto.isEncryptedContainer(ByteArray(BackupCrypto.HEADER_SIZE)))
        assertFalse(BackupCrypto.isEncryptedContainer("PK\u0003\u0004-xlsx-content".toByteArray()))
    }

    @Test
    fun decrypt_withWrongPassphraseFails() {
        val container = BackupCrypto.encrypt("secret".toByteArray(), passphrase)

        try {
            BackupCrypto.decrypt(container, "错误口令-654321".toCharArray())
            fail("expected BackupPassphraseException")
        } catch (expected: BackupPassphraseException) {
            assertEquals("备份口令错误或文件已损坏，请重新输入。", expected.message)
        }
    }

    @Test
    fun decrypt_withTamperedCiphertextFails() {
        val container = BackupCrypto.encrypt("secret".toByteArray(), passphrase)
        container[container.size - 1] = (container[container.size - 1].toInt() xor 0x01).toByte()

        try {
            BackupCrypto.decrypt(container, passphrase)
            fail("expected BackupPassphraseException")
        } catch (expected: BackupPassphraseException) {
            // GCM 认证标签校验失败
        }
    }

    @Test
    fun decrypt_withTamperedHeaderFails() {
        val container = BackupCrypto.encrypt("secret".toByteArray(), passphrase)
        // 篡改盐的最后一个字节：头部作为 AAD，必须导致认证失败而不是静默解密。
        container[BackupCrypto.HEADER_SIZE - BackupCrypto.NONCE_BYTES - 1] =
            (container[BackupCrypto.HEADER_SIZE - BackupCrypto.NONCE_BYTES - 1].toInt() xor 0x01)
                .toByte()

        try {
            BackupCrypto.decrypt(container, passphrase)
            fail("expected BackupPassphraseException")
        } catch (expected: BackupPassphraseException) {
            // AAD 绑定生效
        }
    }

    @Test
    fun decrypt_withUnsupportedVersionFails() {
        val container = BackupCrypto.encrypt("secret".toByteArray(), passphrase)
        container[4] = 99

        try {
            BackupCrypto.decrypt(container, passphrase)
            fail("expected BackupContainerFormatException")
        } catch (expected: BackupContainerFormatException) {
            assertEquals("不支持的加密备份格式版本：99", expected.message)
        }
    }

    @Test
    fun decrypt_withTruncatedContainerFails() {
        val container = BackupCrypto.encrypt("secret-data-to-truncate".toByteArray(), passphrase)

        try {
            BackupCrypto.decrypt(container.copyOfRange(0, 20), passphrase)
            fail("expected BackupContainerFormatException")
        } catch (expected: BackupContainerFormatException) {
            // 头部不完整
        }

        try {
            BackupCrypto.decrypt(
                container.copyOfRange(0, BackupCrypto.HEADER_SIZE),
                passphrase
            )
            fail("expected BackupContainerFormatException")
        } catch (expected: BackupContainerFormatException) {
            // 只有头部没有密文
        }
    }

    @Test
    fun encrypt_withEmptyPassphraseRejected() {
        try {
            BackupCrypto.encrypt("data".toByteArray(), CharArray(0))
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // 空口令不允许
        }
    }

    @Test
    fun encrypt_usesRandomSaltAndNonce() {
        val plaintext = "same-input".toByteArray()
        val first = BackupCrypto.encrypt(plaintext, passphrase)
        val second = BackupCrypto.encrypt(plaintext, passphrase)

        assertFalse(first.contentEquals(second))
        assertFalse(
            first.copyOfRange(BackupCrypto.HEADER_SIZE - BackupCrypto.NONCE_BYTES, BackupCrypto.HEADER_SIZE)
                .contentEquals(
                    second.copyOfRange(BackupCrypto.HEADER_SIZE - BackupCrypto.NONCE_BYTES, BackupCrypto.HEADER_SIZE)
                )
        )
    }

    @Test
    fun encryptedXlsx_roundTripsThroughReader() {
        val bytes = ByteArrayOutputStream().use { output ->
            BackupFileWriter().writeXlsx(samplePayload(), output)
            output.toByteArray()
        }
        val container = BackupCrypto.encrypt(bytes, passphrase)

        val document = BackupFileReader().readXlsx(
            ByteArrayInputStream(container),
            passphrase
        )

        assertEquals(listOf("record-1"), document.measurements.map { it.recordId })
        assertEquals(7, document.measurements.single().readings.size)
        assertEquals("3", document.meta["export_format_version"])
    }

    @Test
    fun reader_withoutPassphraseOnEncryptedFileAsksForPassphrase() {
        val bytes = ByteArrayOutputStream().use { output ->
            BackupFileWriter().writeXlsx(samplePayload(), output)
            output.toByteArray()
        }
        val container = BackupCrypto.encrypt(bytes, passphrase)

        try {
            BackupFileReader().readXlsx(ByteArrayInputStream(container))
            fail("expected BackupPassphraseRequiredException")
        } catch (expected: BackupPassphraseRequiredException) {
            assertEquals("该备份已加密，请输入备份口令。", expected.message)
        }
    }

    @Test
    fun reader_stillParsesLegacyPlaintextXlsx() {
        val bytes = ByteArrayOutputStream().use { output ->
            BackupFileWriter().writeXlsx(samplePayload(), output)
            output.toByteArray()
        }

        val document = BackupFileReader().readXlsx(ByteArrayInputStream(bytes))

        assertEquals(listOf("record-1"), document.measurements.map { it.recordId })
    }

    private fun samplePayload(): BackupExportPayload {
        return BackupExportPayload(
            instructions = listOf("文件用途" to "本地备份"),
            measurements = listOf(
                BackupMeasurementRow(
                    recordId = "record-1",
                    measuredAt = "2026-04-23 08:30:00",
                    date = "2026-04-23",
                    time = "08:30",
                    groupCount = 7,
                    readings = listOf(
                        BackupReadingValue(120, 80, 72),
                        BackupReadingValue(118, 78, null)
                    ),
                    avgSystolic = 119,
                    avgDiastolic = 79,
                    avgPulse = 72,
                    averageStrategy = "DISCARD_FIRST",
                    level = "NORMAL",
                    highAlert = false,
                    scene = "晨起",
                    symptomsJson = "[\"头晕\"]",
                    note = "morning",
                    createdAt = "2026-04-23 08:31:00",
                    updatedAt = "2026-04-23 08:31:00"
                )
            ),
            readings = (1..7).map { index ->
                BackupReadingRow(
                    recordId = "record-1",
                    orderIndex = index,
                    systolic = 118 + index,
                    diastolic = 78 + index,
                    pulse = 70 + index
                )
            },
            userProfile = listOf(BackupUserProfileItem("target_sys", "120")),
            meta = listOf(
                BackupMetaItem("export_format_version", "3"),
                BackupMetaItem("total_records", "1"),
                BackupMetaItem("measurement_sessions_count", "1"),
                BackupMetaItem("measurement_readings_count", "7")
            ),
            diagnostics = BackupExportDiagnostics(
                sessionCount = 1,
                readingCount = 7
            )
        )
    }
}
