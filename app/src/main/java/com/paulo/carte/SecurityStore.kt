package com.paulo.carte

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

class SecurityStore(private val context: Context) {
    private val authPrefs = context.getSharedPreferences("portefeuille_security", Context.MODE_PRIVATE)
    private val dataPrefs = context.getSharedPreferences("portefeuille_secure_data", Context.MODE_PRIVATE)

    fun hasPassword(): Boolean = authPrefs.contains(KEY_PASSWORD_HASH) && authPrefs.contains(KEY_PASSWORD_SALT)

    fun savePassword(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derivePassword(password, salt)
        authPrefs.edit()
            .putString(KEY_PASSWORD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PASSWORD_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    fun verifyPassword(password: String): Boolean {
        if (isLocked()) return false
        val saltText = authPrefs.getString(KEY_PASSWORD_SALT, null) ?: return false
        val hashText = authPrefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val expected = Base64.decode(hashText, Base64.NO_WRAP)
        val actual = derivePassword(password, salt)
        val ok = MessageDigest.isEqual(expected, actual)
        if (ok) {
            resetFailures()
        } else {
            registerFailure()
        }
        return ok
    }

    fun isLocked(now: Long = System.currentTimeMillis()): Boolean = lockUntil() > now

    fun lockUntil(): Long = authPrefs.getLong(KEY_LOCK_UNTIL, 0L)

    fun resetFailures() {
        authPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply()
    }

    private fun registerFailure() {
        val failures = authPrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val edit = authPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, failures)
        if (AuthPolicy.shouldLock(failures)) {
            edit.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + AuthPolicy.LOCKOUT_MS)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
        }
        edit.apply()
    }

    fun saveCardsJson(json: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateEncryptionKey())
        val encrypted = cipher.doFinal(json.toByteArray(StandardCharsets.UTF_8))
        dataPrefs.edit()
            .putString(KEY_CARDS_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CARDS_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun loadCardsJson(): String {
        val ivText = dataPrefs.getString(KEY_CARDS_IV, null)
        val dataText = dataPrefs.getString(KEY_CARDS_DATA, null)
        if (ivText != null && dataText != null) {
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = Base64.decode(ivText, Base64.NO_WRAP)
                val encrypted = Base64.decode(dataText, Base64.NO_WRAP)
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateEncryptionKey(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                "[]"
            }
        }

        val legacyPrefs = context.getSharedPreferences("portefeuille_carte", Context.MODE_PRIVATE)
        val legacy = legacyPrefs.getString("cards", "[]") ?: "[]"
        if (legacy != "[]") {
            saveCardsJson(legacy)
            legacyPrefs.edit().remove("cards").apply()
        }
        return legacy
    }

    private fun derivePassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun getOrCreateEncryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private const val KEY_CARDS_IV = "cards_iv"
        private const val KEY_CARDS_DATA = "cards_data"
        private const val KEYSTORE_ALIAS = "portefeuille_carte_aes_key"
    }
}
