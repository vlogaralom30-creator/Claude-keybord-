package com.banglakeys.app

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase দিয়ে আসল encryption (AES-256-GCM)। Glyph লিপি শুধু ছদ্মবেশ, নিরাপত্তা এখান থেকেই।
 *
 * ── v2 (নতুন, ছোট ও দ্রুত) ─────────────────────────────────────────────
 *  [0xB0|codec 1B][keyId 2B][iv 12B][ciphertext + tag 16B]   (প্রথম ৩ বাইট AAD)
 *  • salt প্রতি মেসেজে নেই → PBKDF2 একবারই চলে, তারপর key cache-এ (আগে প্রতি মেসেজে ১০০k iteration,
 *    আর প্রতিটা contact-এ আলাদা করে!)।
 *  • keyId (২ বাইট) দেখে সরাসরি সঠিক contact বাছা হয় → N contact থাকলে N বার decrypt চেষ্টা নেই।
 *  • বাংলা-compact codec: বাংলা অক্ষর ৩ বাইটের বদলে ১ বাইট। বড় লেখায় Deflate।
 *  • header AAD-তে বাঁধা → codec/keyId বদলালে GCM ধরে ফেলে।
 *  • আগের (v1) মেসেজ এখনও পড়া যায় (backward compatible)।
 *
 * নোট: v2 পাঠালে প্রাপকের অ্যাপও নতুন সংস্করণে হতে হবে।
 */
object SecretCipher {
    // ---- v1 (legacy, শুধু পুরনো মেসেজ পড়ার জন্য) ----
    private const val V1_ITERATIONS = 100_000
    private const val SALT_LEN = 16

    // ---- v2 ----
    private const val V2_ITERATIONS = 200_000
    private const val V2_SALT = "BanglaKeys-secret-v2"
    private const val MAGIC_HI = 0xB0
    private const val CODEC_UTF8 = 0
    private const val CODEC_BN = 1
    private const val CODEC_DEFLATE = 2
    private const val HEADER_LEN = 3
    private const val IV_LEN = 12
    private const val TAG_LEN_BITS = 128
    private const val TAG_LEN = 16
    private const val KEY_LEN_BITS = 256
    private const val MAX_INFLATED = 64 * 1024
    private const val CACHE_LIMIT = 32

    private val rng = SecureRandom()

    private class KeyMaterial(val aes: SecretKeySpec, val id: ByteArray)

    private val keyCache = ConcurrentHashMap<String, KeyMaterial>()

    private fun cacheName(passphrase: String): String =
        MessageDigest.getInstance("SHA-256").digest(("bk-cache:" + passphrase).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun keyFor(passphrase: String): KeyMaterial {
        val name = cacheName(passphrase)
        keyCache[name]?.let { return it }
        val spec = PBEKeySpec(passphrase.toCharArray(), V2_SALT.toByteArray(Charsets.UTF_8), V2_ITERATIONS, KEY_LEN_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val kid = MessageDigest.getInstance("SHA-256").digest("bk-kid".toByteArray(Charsets.UTF_8) + raw)
        val km = KeyMaterial(SecretKeySpec(raw, "AES"), byteArrayOf(kid[0], kid[1]))
        if (keyCache.size >= CACHE_LIMIT) keyCache.clear()
        keyCache[name] = km
        return km
    }

    /** ভারী PBKDF2 আগে থেকে background-এ সেরে রাখে — প্রথম send/receive-এ UI আটকাবে না। */
    fun warmUp(passphrases: Collection<String>) {
        for (p in passphrases) if (p.isNotBlank()) keyFor(p)
    }

    /** নিরাপত্তার জন্য memory-র cache মুছে ফেলা (যেমন contact মুছলে)। */
    fun clearKeyCache() = keyCache.clear()

    // ------------------------------------------------------------------ text codecs

    /**
     * ASCII + বাংলা ব্লক (U+0980–09FF) + ZWNJ/ZWJ/দাঁড়ি → ১ বাইট/অক্ষর।
     * অন্য যেকোনো অক্ষর (emoji ইত্যাদি) → 0x00 + তার UTF-8 (self-delimiting), তাই মিশ্র লেখাও ছোট থাকে।
     */
    private fun packBengali(s: String): ByteArray {
        val out = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp in 0x04..0x7F -> out.write(cp)
                cp in 0x0980..0x09FF -> out.write(0x80 + (cp - 0x0980))
                cp == 0x200C -> out.write(0x01)
                cp == 0x200D -> out.write(0x02)
                cp == 0x0964 -> out.write(0x03)
                else -> {
                    out.write(0x00)
                    out.write(String(Character.toChars(cp)).toByteArray(Charsets.UTF_8))
                }
            }
        }
        return out.toByteArray()
    }

    private fun unpackBengali(b: ByteArray): String? {
        val sb = StringBuilder(b.size)
        var i = 0
        while (i < b.size) {
            val v = b[i].toInt() and 0xFF
            i++
            when {
                v >= 0x80 -> sb.append((0x0980 + (v - 0x80)).toChar())
                v == 0x01 -> sb.append('\u200C')
                v == 0x02 -> sb.append('\u200D')
                v == 0x03 -> sb.append('\u0964')
                v == 0x00 -> {
                    if (i >= b.size) return null
                    val lead = b[i].toInt() and 0xFF
                    val n = when {
                        lead < 0x80 -> 1
                        (lead and 0xE0) == 0xC0 -> 2
                        (lead and 0xF0) == 0xE0 -> 3
                        (lead and 0xF8) == 0xF0 -> 4
                        else -> return null
                    }
                    if (i + n > b.size) return null
                    sb.append(String(b, i, n, Charsets.UTF_8))
                    i += n
                }
                else -> sb.append(v.toChar())
            }
        }
        return sb.toString()
    }

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        d.setInput(data)
        d.finish()
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(512)
        while (!d.finished()) bos.write(buf, 0, d.deflate(buf))
        d.end()
        return bos.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inf = Inflater(true)
        return try {
            inf.setInput(data)
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(512)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                bos.write(buf, 0, n)
                if (bos.size() > MAX_INFLATED) return null
            }
            if (inf.finished()) bos.toByteArray() else null
        } catch (_: Exception) {
            null
        } finally {
            inf.end()
        }
    }

    /** সবচেয়ে ছোট representation বাছে। */
    private fun pack(text: String): Pair<Int, ByteArray> {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        var best = CODEC_UTF8 to utf8
        val bn = packBengali(text)
        if (bn.size < best.second.size) best = CODEC_BN to bn
        if (utf8.size > 48) {
            val z = deflate(utf8)
            if (z.size < best.second.size) best = CODEC_DEFLATE to z
        }
        return best
    }

    private fun unpack(codec: Int, b: ByteArray): String? = when (codec) {
        CODEC_UTF8 -> String(b, Charsets.UTF_8)
        CODEC_BN -> unpackBengali(b)
        CODEC_DEFLATE -> inflate(b)?.let { String(it, Charsets.UTF_8) }
        else -> null
    }

    /** UI-র জন্য: এই লেখা পাঠালে কতগুলো glyph চিহ্ন লাগবে (chat limit দেখাতে)। */
    fun estimateGlyphCount(plaintext: String): Int {
        val bytes = HEADER_LEN + IV_LEN + TAG_LEN + pack(plaintext).second.size
        return (bytes * 8 + 4) / 5
    }

    // ------------------------------------------------------------------ encrypt / decrypt

    /** plaintext -> v2 bytes। */
    fun encrypt(plaintext: String, passphrase: String): ByteArray {
        val km = keyFor(passphrase)
        val (codec, packed) = pack(plaintext)
        val header = byteArrayOf((MAGIC_HI or codec).toByte(), km.id[0], km.id[1])
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, km.aes, GCMParameterSpec(TAG_LEN_BITS, iv))
        cipher.updateAAD(header)
        return header + iv + cipher.doFinal(packed)
    }

    private fun looksV2(data: ByteArray) =
        data.size >= HEADER_LEN + IV_LEN + TAG_LEN && (data[0].toInt() and 0xF0) == MAGIC_HI

    private fun keyIdMatches(data: ByteArray, km: KeyMaterial) = data[1] == km.id[0] && data[2] == km.id[1]

    private fun decryptV2(data: ByteArray, km: KeyMaterial): String? {
        if (!looksV2(data) || !keyIdMatches(data, km)) return null
        return try {
            val header = data.copyOfRange(0, HEADER_LEN)
            val iv = data.copyOfRange(HEADER_LEN, HEADER_LEN + IV_LEN)
            val ct = data.copyOfRange(HEADER_LEN + IV_LEN, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, km.aes, GCMParameterSpec(TAG_LEN_BITS, iv))
            cipher.updateAAD(header)
            unpack(data[0].toInt() and 0x0F, cipher.doFinal(ct))
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveV1(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, V1_ITERATIONS, KEY_LEN_BITS)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    /** পুরনো [salt16][iv12][ct+tag] ফরম্যাট। */
    private fun decryptV1(data: ByteArray, passphrase: String): String? {
        if (data.size < SALT_LEN + IV_LEN + TAG_LEN) return null
        return try {
            val salt = data.copyOfRange(0, SALT_LEN)
            val iv = data.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
            val ct = data.copyOfRange(SALT_LEN + IV_LEN, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveV1(passphrase, salt), GCMParameterSpec(TAG_LEN_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** ভুল passphrase / নষ্ট data হলে null — আন্দাজ করা লেখা কখনো না। */
    fun decrypt(data: ByteArray, passphrase: String): String? {
        if (passphrase.isBlank()) return null
        val km = keyFor(passphrase)
        decryptV2(data, km)?.let { return it }
        // v2 হেডার + keyId মিলেও GCM ফেল = নষ্ট মেসেজ; ধীর v1 চেষ্টা বৃথা
        if (looksV2(data) && keyIdMatches(data, km)) return null
        return decryptV1(data, passphrase)
    }

    fun decryptWithAny(data: ByteArray, passphrases: Collection<String>): String? {
        for ((_, plain) in matchAll(data, passphrases.associateBy { it })) return plain
        return null
    }

    /** `(contactName, plaintext)` — v2-তে keyId দিয়ে O(1)-এর মতো, v1 শুধু শেষ ভরসা। */
    fun decryptWithMatchingContact(data: ByteArray, contacts: Map<String, String>): Pair<String, String>? =
        matchAll(data, contacts).firstOrNull()

    private fun matchAll(data: ByteArray, contacts: Map<String, String>): Sequence<Pair<String, String>> = sequence {
        var keyIdHit = false
        for ((name, pass) in contacts) {
            if (pass.isBlank()) continue
            val km = keyFor(pass)
            if (looksV2(data) && keyIdMatches(data, km)) keyIdHit = true
            decryptV2(data, km)?.let { yield(name to it) }
        }
        // কোনো contact-এর keyId না মিললে (বা v2 নয়) তবেই legacy v1 চেষ্টা
        if (!keyIdHit) {
            for ((name, pass) in contacts) {
                if (pass.isBlank()) continue
                decryptV1(data, pass)?.let { yield(name to it) }
            }
        }
    }
}
