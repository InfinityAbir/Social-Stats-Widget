package com.abir.socialwidget

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts secrets (API key, Page token) before they are written to disk.
 *
 * Uses an AES-256-GCM key generated inside the Android Keystore. The key material is held by the system
 * (secure hardware where available) and cannot be read or exported by the app or by anything that copies the
 * app's files, so the stored ciphertext is useless without this device and this app.
 *
 * Format: `v1:<iv>:<ciphertext>` (both Base64, no wrapping). Never logs secret values.
 */
object Vault {
    private const val TAG = "Vault"
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "social_stats_vault_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val PREFIX = "v1:"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Returns the sealed form of [plain], or null if the device could not encrypt it. */
    fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + b64(cipher.iv) + ":" + b64(sealed)
    } catch (e: Exception) {
        Log.w(TAG, "encrypt failed: ${e.javaClass.simpleName}")
        null
    }

    /** Returns the original text, or null if [sealed] is malformed or the key is gone. */
    fun decrypt(sealed: String): String? = try {
        require(sealed.startsWith(PREFIX)) { "unknown format" }
        val (iv, data) = sealed.removePrefix(PREFIX).split(":")
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, unb64(iv)))
        String(cipher.doFinal(unb64(data)), Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w(TAG, "decrypt failed: ${e.javaClass.simpleName}")
        null
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)
}
