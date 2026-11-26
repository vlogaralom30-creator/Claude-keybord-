package com.example

import com.banglakeys.app.Translator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TranslatorTest {

    @Test fun `auto direction follows script not keyboard mode`() {
        assertEquals("bn" to "en", Translator.autoDirection("আমি ভালো আছি"))
        assertEquals("en" to "bn", Translator.autoDirection("how are you"))
        assertEquals("bn" to "en", Translator.autoDirection("hello আমি"))
    }

    @Test fun `google response segments are joined and null segments skipped`() {
        val body = """[[["Hello. ","হ্যালো। ",null,null,10],["How are you?","কেমন আছো?",null,null,10],[null,null,"helo"]],null,"bn"]"""
        assertEquals("Hello. How are you?", Translator.parseGoogle(body))
        assertNull(Translator.parseGoogle("not json"))
        assertNull(Translator.parseGoogle("""[[],null,"bn"]"""))
    }

    @Test fun `mymemory ok is accepted`() {
        val ok = """{"responseData":{"translatedText":"Hello"},"responseStatus":200}"""
        assertEquals("Hello", Translator.parseMyMemory(ok))
    }

    @Test fun `mymemory quota warning is rejected not shown as translation`() {
        val warn = """{"responseData":{"translatedText":"MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY."},"responseStatus":200}"""
        try { Translator.parseMyMemory(warn); fail("should throw") } catch (_: Exception) { }
        val limit = """{"responseData":{"translatedText":"QUERY LENGTH LIMIT EXCEEDED. MAX ALLOWED QUERY : 500 CHARS"},"responseStatus":"403"}"""
        try { Translator.parseMyMemory(limit); fail("should throw") } catch (_: Exception) { }
    }

    @Test fun `long text is split at sentence ends within limit`() {
        val sentence = "আমি আজ বাজারে গিয়েছিলাম এবং অনেক কিছু কিনেছি। "
        val text = sentence.repeat(40)
        val chunks = Translator.splitChunks(text, 400)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 400 })
        assertEquals(text.replace(Regex("\\s+"), ""), chunks.joinToString("").replace(Regex("\\s+"), ""))
    }

    @Test fun `single huge sentence still splits on word boundary`() {
        val text = "word ".repeat(300).trim()
        val chunks = Translator.splitChunks(text, 100)
        assertTrue(chunks.all { it.length <= 100 })
        assertEquals(300, chunks.joinToString(" ").split(" ").size)
    }

    @Test fun `mymemory 429 with null responseData is rejected and splitChunks guards min limit`() {
        val rateLimited = """{"responseData":null,"responseStatus":429}"""
        try { Translator.parseMyMemory(rateLimited); fail("should throw") } catch (_: Exception) { }
        assertTrue(Translator.splitChunks("abc", 0).isNotEmpty())
    }

    @Test fun `lingva response is parsed`() {
        assertEquals("I am fine", Translator.parseLingva("""{"translation":"I am fine"}"""))
        assertNull(Translator.parseLingva("""{"translation":""}"""))
        assertNull(Translator.parseLingva("<html>blocked</html>"))
    }

    @Test fun `probe line shows status provider and detail`() {
        assertEquals("✅ google · 120ms · I am fine", Translator.Probe("google", true, 120, "I am fine").line())
        assertEquals("❌ mymemory · 40ms · 429", Translator.Probe("mymemory", false, 40, "429").line())
    }

    @Test fun `fail carries diagnostic detail and has default`() {
        val f = Translator.Outcome.Fail(Translator.Reason.OFFLINE, "google:dns")
        assertEquals("google:dns", f.detail)
        assertEquals("", Translator.Outcome.Fail(Translator.Reason.EMPTY).detail)
    }

    @Test fun `bengali text is split into smaller chunks than english`() {
        val bn = "আমি ভালো আছি। ".repeat(60)
        assertTrue(Translator.splitChunks(bn, 200).all { it.length <= 200 })
    }
}
