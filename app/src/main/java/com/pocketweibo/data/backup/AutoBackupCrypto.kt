package com.pocketweibo.data.backup

import android.content.Context
import android.provider.Settings
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM payloads keyed from [Settings.Secure.ANDROID_ID] + package name (see [keyFromDeviceStrings]).
 * Files are only recoverable on the same device profile that created them.
 */
object AutoBackupCrypto {

    private const val MAGIC = "PWB1"
    private val MAGIC_BYTES = MAGIC.toByteArray(StandardCharsets.US_ASCII)
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private val KDF_SALT = "PocketWeibo.AutoBackup.v1".toByteArray(StandardCharsets.UTF_8)

    internal fun keyFromDeviceStrings(androidId: String, packageName: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(KDF_SALT)
        digest.update("$androidId|$packageName".toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(digest.digest(), "AES")
    }

    private fun keyFor(context: Context): SecretKeySpec {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown"
        return keyFromDeviceStrings(androidId, context.packageName)
    }

    fun encrypt(plaintext: ByteArray, context: Context): ByteArray =
        encryptWithKey(plaintext, keyFor(context))

    fun decrypt(ciphertextFile: ByteArray, context: Context): ByteArray =
        decryptWithKey(ciphertextFile, keyFor(context))

    internal fun encryptWithKey(plaintext: ByteArray, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        val encrypted = cipher.doFinal(plaintext)
        return MAGIC_BYTES + iv + encrypted
    }

    internal fun decryptWithKey(fileBytes: ByteArray, key: SecretKeySpec): ByteArray {
        require(fileBytes.size >= MAGIC_BYTES.size + GCM_IV_LENGTH + 16) { "truncated" }
        var o = 0
        val magic = fileBytes.copyOfRange(o, o + MAGIC_BYTES.size)
        require(magic.contentEquals(MAGIC_BYTES)) { "bad magic" }
        o += MAGIC_BYTES.size
        val iv = fileBytes.copyOfRange(o, o + GCM_IV_LENGTH)
        o += GCM_IV_LENGTH
        val ct = fileBytes.copyOfRange(o, fileBytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }
}
