package com.example

import com.banglakeys.app.GlyphAlphabet
import com.banglakeys.app.GlyphStyle
import com.banglakeys.app.SecretCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SecretCipherV2Test {
    private val pass = "K7QM-4TXD-PH2R-9WBN-ZC6E"

    @Test fun `round trip ascii bengali emoji mixed and long`() {
        val samples = listOf(
            "hello", "আমি ভালো আছি, তুমি কেমন আছো?", "র‍্যাব ও ক্ষ।", "মিটিং কাল ১০টায় 😊👍",
            "", "আজ " .repeat(80),
        )
        for (s in samples) {
            val enc = SecretCipher.encrypt(s, pass)
            assertEquals(s, SecretCipher.decrypt(enc, pass))
        }
    }

    @Test fun `bengali message is much shorter than v1`() {
        val msg = "আমি ভালো আছি, তুমি কেমন আছো?" // 28 chars
        val glyphs = GlyphAlphabet.encode(SecretCipher.encrypt(msg, pass), GlyphStyle.RUNIC)
        assertTrue("glyph count ${glyphs.length}", glyphs.length <= 100) // v1 ছিল ~183
        assertEquals(glyphs.length, SecretCipher.estimateGlyphCount(msg))
    }

    @Test fun `wrong passphrase and tamper give null`() {
        val enc = SecretCipher.encrypt("গোপন", pass)
        assertNull(SecretCipher.decrypt(enc, "AAAA-BBBB-CCCC-DDDD-EEEE"))
        val bad = enc.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        assertNull(SecretCipher.decrypt(bad, pass))
        val hdr = enc.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() } // codec বদল → AAD ধরবে
        assertNull(SecretCipher.decrypt(hdr, pass))
    }

    @Test fun `matching contact is found by key id`() {
        val contacts = linkedMapOf("রহিম" to "AAAA-1111-BBBB-2222-CCCC", "করিম" to pass, "সুমি" to "ZZZZ-9999-YYYY-8888-XXXX")
        val enc = SecretCipher.encrypt("হাই", pass)
        assertEquals("করিম" to "হাই", SecretCipher.decryptWithMatchingContact(enc, contacts))
        assertNull(SecretCipher.decryptWithMatchingContact(enc, mapOf("রহিম" to "AAAA-1111-BBBB-2222-CCCC")))
    }

    @Test fun `legacy v1 messages still decrypt`() {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val key = SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(pass.toCharArray(), salt, 100_000, 256)).encoded, "AES"
        )
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        val v1 = salt + iv + c.doFinal("পুরনো মেসেজ".toByteArray(Charsets.UTF_8))
        assertEquals("পুরনো মেসেজ", SecretCipher.decrypt(v1, pass))
        assertEquals("c" to "পুরনো মেসেজ", SecretCipher.decryptWithMatchingContact(v1, mapOf("c" to pass)))
    }

    @Test fun `glyph run is extracted from surrounding chat text`() {
        for (style in GlyphStyle.values()) {
            val g = GlyphAlphabet.encode(SecretCipher.encrypt("হ্যালো", pass), style)
            val noisy = "রহিম: " + g.chunked(30).joinToString("\n") + "  10:42 PM"
            assertTrue(GlyphAlphabet.looksLikeGlyphText(noisy))
            assertEquals(style, GlyphAlphabet.detectStyle(noisy))
            assertEquals("হ্যালো", SecretCipher.decrypt(GlyphAlphabet.decodeAny(noisy)!!, pass))
        }
    }

    @Test fun `short symbol strings are not treated as cipher`() {
        assertFalse(GlyphAlphabet.looksLikeGlyphText("▶ ■ ▲ Play ◆●"))
        assertFalse(GlyphAlphabet.looksLikeGlyphText("ᚠᚢᚦᚠᚢᚦ"))
    }

    @Test fun `variation selectors inside glyph run and key cache clear preserve decryption`() {
        val g = GlyphAlphabet.encode(SecretCipher.encrypt("হ্যালো", pass), GlyphStyle.SHAPES)
        val withVs = g.chunked(12).joinToString("\uFE0E\uFE0F ")
        assertTrue(GlyphAlphabet.looksLikeGlyphText(withVs))
        SecretCipher.clearKeyCache()
        assertEquals("হ্যালো", SecretCipher.decrypt(GlyphAlphabet.decodeAny(withVs)!!, pass))
    }
}
