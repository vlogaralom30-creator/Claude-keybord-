package com.banglakeys.app

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase দিয়ে আসল encryption (AES-256-GCM)। এটাই আসল নিরাপত্তা — GlyphAlphabet.kt-এর
 * রুন-লিপি শুধু দেখতে কেমন লাগবে সেটা ঠিক করে, নিরাপত্তা দেয় না।
 *
 * গুরুত্বপূর্ণ: কোড public (GitHub-এ) থাকলেও, passphrase ছাড়া কেউ (কোনো AI বা মানুষ)
 * decode করতে পারবে না — algorithm public, key (passphrase) secret, এভাবেই আসল
 * encryption কাজ করে (Signal/WhatsApp-ও এই নীতিতেই চলে)।
 *
 * কোনো external library লাগেনি — javax.crypto Android-এর built-in অংশ।
 */
object SecretCipher {
    private const val ITERATIONS = 100_000
    private const val KEY_LEN_BITS = 256
    private const val IV_LEN = 12
    private const val SALT_LEN = 16
    private const val TAG_LEN_BITS = 128

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LEN_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    /** plaintext -> [salt][iv][ciphertext+tag] — সবকিছু একটা byte array-এ। */
    fun encrypt(plaintext: String, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return salt + iv + ct
    }

    /**
     * ভুল passphrase বা নষ্ট/অসম্পূর্ণ data হলে null ফেরত দেয় — আন্দাজ করা কোনো ভুল
     * লেখা দেখায় না (GCM নিজেই integrity check করে, tampered data ধরে ফেলে)।
     */
    fun decrypt(data: ByteArray, passphrase: String): String? {
        if (data.size < SALT_LEN + IV_LEN) return null
        return try {
            val salt = data.copyOfRange(0, SALT_LEN)
            val iv = data.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
            val ct = data.copyOfRange(SALT_LEN + IV_LEN, data.size)
            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LEN_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * একাধিক সেভ করা passphrase (per-contact) থেকে একে একে try করে — কোনটা দিয়ে
     * পাঠানো হয়েছে তা আগে থেকে জানা লাগে না। GCM নিজেই ভুল key ধরে ফেলে (exception),
     * তাই এটা নিরাপদ — ভুল key দিয়ে ভুল/আন্দাজ করা লেখা কখনো বেরোবে না, শুধু null।
     */
    fun decryptWithAny(data: ByteArray, passphrases: Collection<String>): String? {
        for (p in passphrases) {
            if (p.isBlank()) continue
            decrypt(data, p)?.let { return it }
        }
        return null
    }

    /**
     * Contact-নামসহ সেভ করা passphrase-গুলো try করে — মিলে গেলে `(contactName, plaintext)` দেয়।
     */
    fun decryptWithMatchingContact(
        data: ByteArray,
        contacts: Map<String, String>
    ): Pair<String, String>? {
        for ((name, pass) in contacts) {
            if (pass.isBlank()) continue
            decrypt(data, pass)?.let { plain -> return name to plain }
        }
        return null
    }
}
