package com.banglakeys.app

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Local cache এবং SharedPreferences-এ secret message content, passphrase ও draft
 * সংরক্ষণের আগে AES-256-GCM দিয়ে encrypt করার ভল্ট।
 *
 * - আসল Android ডিভাইসে `AndroidKeyStore`-এ সুরক্ষিত 256-bit AES-GCM key ব্যবহার করে।
 * - টেস্ট এনভায়রনমেন্ট বা Keystore সীমাবদ্ধ ডিভাইসে লোকাল প্রাইভেট 256-bit র‍্যান্ডম কী
 *   ব্যবহার করে আসল `AES/GCM/NoPadding` এনক্রিপশন নিশ্চিত করে।
 */
object LocalAesVault {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "banglakeys_secret_vault_aes256"
    private const val CIPHER_PREFIX = "AESV1:"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12
    private const val TAG_LEN_BITS = 128

    private const val FALLBACK_PREFS = "secret_vault_kp"
    private const val KEY_FALLBACK_RAW = "vk"

    @Volatile
    private var cachedTestOrFallbackKey: SecretKey? = null

    private fun getOrCreateKey(ctx: Context): SecretKey {
        getKeyStoreKey()?.let { return it }
        return getOrCreateFallbackKey(ctx.applicationContext ?: ctx)
    }

    private fun getKeyStoreKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
            ks.load(null)
            val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            if (Build.VERSION.SDK_INT >= 28) {
                builder.setUnlockedDeviceRequired(false)
            }
            generator.init(builder.build())
            generator.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    @Synchronized
    private fun getOrCreateFallbackKey(ctx: Context): SecretKey {
        cachedTestOrFallbackKey?.let { return it }
        val prefs = ctx.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        val existingB64 = prefs.getString(KEY_FALLBACK_RAW, null)
        val keyBytes = if (!existingB64.isNullOrBlank()) {
            try {
                val decoded = Base64.decode(existingB64, Base64.NO_WRAP)
                if (decoded.size == 32) decoded else null
            } catch (_: Exception) {
                null
            }
        } else {
            null
        } ?: ByteArray(32).also { fresh ->
            SecureRandom().nextBytes(fresh)
            prefs.edit()
                .putString(KEY_FALLBACK_RAW, Base64.encodeToString(fresh, Base64.NO_WRAP))
                .apply()
        }
        return SecretKeySpec(keyBytes, "AES").also { cachedTestOrFallbackKey = it }
    }

    /** Plaintext string -> `"AESV1:" + Base64(iv[12] + ciphertext + GCM_tag)`. */
    fun encrypt(ctx: Context, plaintext: String): String {
        val key = getOrCreateKey(ctx)
        val cipher = Cipher.getInstance(AES_GCM)
        val isKeystore = try {
            key.encoded == null
        } catch (_: Throwable) {
            true
        }
        val iv: ByteArray
        if (isKeystore) {
            cipher.init(Cipher.ENCRYPT_MODE, key)
            iv = cipher.iv
        } else {
            iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(ciphertext, 0, packed, iv.size, ciphertext.size)
        return CIPHER_PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /**
     * `"AESV1:..."` পেলে AES-GCM দিয়ে decrypt করে মূল plaintext ফেরত দেয়।
     * ডাটা নষ্ট বা tampered হলে `null` দেয় (আন্দাজে ভুল লেখা দেয় না)।
     */
    fun decrypt(ctx: Context, payload: String?): String? {
        if (payload.isNullOrBlank() || !payload.startsWith(CIPHER_PREFIX)) return null
        return try {
            val packed = Base64.decode(payload.removePrefix(CIPHER_PREFIX), Base64.NO_WRAP)
            if (packed.size <= IV_LEN) return null
            val iv = packed.copyOfRange(0, IV_LEN)
            val ct = packed.copyOfRange(IV_LEN, packed.size)
            val key = getOrCreateKey(ctx)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    fun isEncryptedPayload(payload: String?): Boolean =
        payload != null && payload.startsWith(CIPHER_PREFIX)
}
