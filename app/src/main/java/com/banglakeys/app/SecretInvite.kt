package com.banglakeys.app

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Secret code (passphrase) বানানো, পাঠানোর জন্য invite-text তৈরি, আর পাওয়া invite পড়া।
 *
 * ⚠️ নিরাপত্তা: code-টা যে chat-এ secret message পাঠাবেন সেখানেই পাঠালে, কেউ সেই chat দেখলে
 * দুটোই পাবে। তাই code একবার সামনাসামনি / ফোনে / আলাদা app-এ দিন — তারপর secret message
 * যেকোনো chat-এ পাঠান। যাচাইয়ের জন্য দুজনের "fingerprint" মিলিয়ে দেখুন (ফোনে বলে)।
 */
object SecretInvite {
    private const val PREFIX = "BKEY1:"

    // ০/O, ১/I/L বাদ — মুখে বলা বা হাতে লেখার সময় গুলিয়ে না যেতে
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** ২০টা অক্ষর (~১০০ bit) — যেমন "K7QM-4TXD-PH2R-9WBN-ZC6E"। */
    fun generatePassphrase(): String {
        val rnd = SecureRandom()
        val sb = StringBuilder()
        for (g in 0 until 5) {
            if (g > 0) sb.append('-')
            repeat(4) { sb.append(ALPHABET[rnd.nextInt(ALPHABET.length)]) }
        }
        return sb.toString()
    }

    /** দুজনে মিলিয়ে দেখার ছোট চিহ্ন, যেমন "A1B-2C3"। passphrase থেকে বের করা, উল্টে passphrase পাওয়া যায় না। */
    fun fingerprint(passphrase: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(("bk-fp:" + passphrase).toByteArray(Charsets.UTF_8))
        val hex = d.take(3).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        return hex.substring(0, 3) + "-" + hex.substring(3)
    }

    private fun encode(passphrase: String): String =
        Base64.encodeToString(
            passphrase.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    /** Share sheet-এ পাঠানোর পুরো লেখা। */
    fun shareText(passphrase: String): String =
        "🔐 EMO Keys secret code\n" +
            "${PREFIX}${encode(passphrase)}\n" +
            "যাচাই: ${fingerprint(passphrase)}\n\n" +
            "EMO Keys-এর 🔐 Secret Message screen-এ \"Code import\"-এ এই লেখা পেস্ট করুন। " +
            "এই code অন্য কাউকে দেবেন না।"

    /** পেস্ট করা যেকোনো লেখা থেকে code বের করে; না পেলে null। */
    fun parse(text: String): String? {
        val start = text.indexOf(PREFIX)
        if (start < 0) return null
        val token = text.substring(start + PREFIX.length)
            .takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (token.isEmpty()) return null
        return try {
            val raw = Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP)
            String(raw, Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
