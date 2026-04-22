// app/src/main/java/com/quickshare/network/security/AesEncryption.kt
package com.quickshare.network.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AesEncryption @Inject constructor() {

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val KEY_SIZE = 256
        private const val IV_SIZE = 16
    }

    private var secretKey: SecretKey? = null
    private var iv: ByteArray? = null

    fun generateKey(): String {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(KEY_SIZE)
        secretKey = keyGenerator.generateKey()

        // Generate random IV
        iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)

        // Return base64 encoded key for sharing
        return Base64.encodeToString(secretKey!!.encoded, Base64.NO_WRAP)
    }

    fun setKey(keyString: String) {
        val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
        secretKey = SecretKeySpec(keyBytes, ALGORITHM)
    }

    fun setIv(ivString: String) {
        iv = Base64.decode(ivString, Base64.NO_WRAP)
    }

    fun getIv(): String {
        return if (iv != null) {
            Base64.encodeToString(iv, Base64.NO_WRAP)
        } else {
            iv = ByteArray(IV_SIZE)
            SecureRandom().nextBytes(iv)
            Base64.encodeToString(iv, Base64.NO_WRAP)
        }
    }

    fun encrypt(data: ByteArray): ByteArray {
        if (secretKey == null) {
            generateKey()
        }
        if (iv == null) {
            iv = ByteArray(IV_SIZE)
            SecureRandom().nextBytes(iv)
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    fun decrypt(encryptedData: ByteArray): ByteArray {
        if (secretKey == null || iv == null) {
            throw IllegalStateException("Key and IV must be set before decryption")
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        return cipher.doFinal(encryptedData)
    }
}

// Checksum verification
class ChecksumVerifier {

    fun calculateMd5(data: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val digest = md.digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verify(data: ByteArray, expectedChecksum: String): Boolean {
        val actual = calculateMd5(data)
        return actual == expectedChecksum
    }
}