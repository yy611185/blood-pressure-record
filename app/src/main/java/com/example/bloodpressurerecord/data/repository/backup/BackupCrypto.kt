package com.example.bloodpressurerecord.data.repository.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 加密备份容器口令错误或密文被篡改。 */
class BackupPassphraseException(message: String, cause: Throwable? = null) :
    GeneralSecurityException(message, cause)

/** 加密备份容器结构损坏、版本不支持或缺少口令。 */
open class BackupContainerFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** 文件是加密容器但调用方未提供口令，UI 应弹出输入框后重试。 */
class BackupPassphraseRequiredException(message: String) :
    BackupContainerFormatException(message)

/**
 * 加密备份容器（.bpx）编解码。
 *
 * 布局：magic(4) + formatVersion(1) + kdfId(1) + iterations(4 BE) +
 * salt(16) + nonce(12)，共 38 字节；随后是 AES-256-GCM 密文（含 128 位认证标签）。
 * 头部整体作为 GCM AAD，防止参数被替换。口令只经 PBKDF2 派生，不落盘。
 */
object BackupCrypto {

    const val FILE_EXTENSION = "bpx"
    const val FORMAT_VERSION = 1
    const val KDF_PBKDF2_HMAC_SHA256 = 1
    const val ITERATIONS = 310_000
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val KEY_BITS = 256
    const val GCM_TAG_BITS = 128

    private val MAGIC = byteArrayOf(0x42, 0x50, 0x52, 0x58) // "BPRX"
    const val HEADER_SIZE = 4 + 1 + 1 + 4 + SALT_BYTES + NONCE_BYTES

    private val random = SecureRandom()

    fun isEncryptedContainer(bytes: ByteArray): Boolean {
        if (bytes.size < HEADER_SIZE) return false
        for (index in MAGIC.indices) {
            if (bytes[index] != MAGIC[index]) return false
        }
        return true
    }

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "加密备份需要非空口令" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = buildHeader(FORMAT_VERSION, KDF_PBKDF2_HMAC_SHA256, ITERATIONS, salt, nonce)
        val key = deriveKey(passphrase, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(plaintext)
        return header + ciphertext
    }

    fun decrypt(container: ByteArray, passphrase: CharArray): ByteArray {
        val minimumContainerSize = HEADER_SIZE + GCM_TAG_BITS / 8
        if (container.size < minimumContainerSize || !isEncryptedContainer(container)) {
            throw BackupContainerFormatException("不是有效的加密备份文件")
        }
        val version = container[MAGIC.size].toInt() and 0xFF
        if (version != FORMAT_VERSION) {
            throw BackupContainerFormatException("不支持的加密备份格式版本：$version")
        }
        val kdfId = container[MAGIC.size + 1].toInt() and 0xFF
        if (kdfId != KDF_PBKDF2_HMAC_SHA256) {
            throw BackupContainerFormatException("不支持的密钥派生算法：$kdfId")
        }
        var offset = MAGIC.size + 2
        val iterations = readIntBe(container, offset)
        offset += 4
        if (iterations != ITERATIONS) {
            throw BackupContainerFormatException("加密备份的 KDF 参数不受支持")
        }
        val salt = container.copyOfRange(offset, offset + SALT_BYTES)
        offset += SALT_BYTES
        val nonce = container.copyOfRange(offset, offset + NONCE_BYTES)
        offset += NONCE_BYTES
        val header = container.copyOfRange(0, offset)
        val ciphertext = container.copyOfRange(offset, container.size)
        if (ciphertext.size < GCM_TAG_BITS / 8) {
            throw BackupContainerFormatException("加密备份内容已截断")
        }
        val key = try {
            deriveKey(passphrase, salt, iterations)
        } catch (throwable: GeneralSecurityException) {
            throw BackupContainerFormatException("解密备份失败", throwable)
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(header)
        return try {
            cipher.doFinal(ciphertext)
        } catch (throwable: GeneralSecurityException) {
            throw BackupPassphraseException(
                "备份口令错误或文件已损坏，请重新输入。",
                throwable
            )
        }
    }

    private fun buildHeader(
        version: Int,
        kdfId: Int,
        iterations: Int,
        salt: ByteArray,
        nonce: ByteArray
    ): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        MAGIC.copyInto(header)
        header[MAGIC.size] = version.toByte()
        header[MAGIC.size + 1] = kdfId.toByte()
        writeIntBe(header, MAGIC.size + 2, iterations)
        salt.copyInto(header, MAGIC.size + 6)
        nonce.copyInto(header, MAGIC.size + 6 + SALT_BYTES)
        return header
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int
    ): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }

    private fun readIntBe(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun writeIntBe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }
}
