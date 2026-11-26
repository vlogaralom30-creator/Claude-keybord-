package com.example

import com.banglakeys.app.KeyType
import com.banglakeys.app.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbhatLayoutTest {
    private val rows = Layouts.probhat(bnDigits = true)
    private fun chars() = rows.flatten().filter { it.type == KeyType.CHAR }

    @Test fun `has five rows with bengali digits and latin hints`() {
        assertEquals(5, rows.size)
        assertEquals("১", rows[0][0].label)
        assertEquals("1", rows[0][0].alt)
        assertEquals(10, rows[0].size)
    }

    @Test fun `top row matches ridmik probhat`() {
        val got = rows[1].map { it.label + it.shiftLabel }
        assertEquals(listOf("দধ", "ূঊ", "ীঈ", "রড়", "টঠ", "এঐ", "ুউ", "িই", "ওঔ", "পফ", "েৈ", "োৌ"), got)
    }

    @Test fun `home and bottom letter rows`() {
        val home = rows[2].filter { it.type == KeyType.CHAR }.map { it.label + it.shiftLabel }
        assertEquals(listOf("াঅ", "সষ", "ডঢ", "তথ", "গঘ", "হঃ", "জঝ", "কখ", "লং"), home)
        val bottom = rows[3].filter { it.type == KeyType.CHAR }.map { it.label }
        assertEquals(listOf("য়", "শ", "চ", "আ", "ব", "ন", "ম", "্", Layouts.PROBHAT_KEY_DOT), bottom)
        assertEquals("য", rows[3][1].shiftLabel)
        assertEquals("ঢ়", rows[3][2].shiftLabel)
    }

    @Test fun `every letter key is bengali and shift label differs`() {
        for (k in chars()) {
            if (k.shiftLabel != null) {
                assertTrue(k.label.all { it.code in 0x0980..0x09FF })
                assertTrue(k.shiftLabel!!.all { it.code in 0x0980..0x09FF })
                assertFalse(k.label == k.shiftLabel)
                assertEquals(k.shiftLabel, k.alt) // দীর্ঘ চাপে শিফট-অক্ষর
            }
        }
    }

    @Test fun `bottom row keeps lang emoji mic enter`() {
        val types = rows[4].map { it.type }
        assertTrue(types.containsAll(listOf(KeyType.SYMBOLS, KeyType.LANG, KeyType.EMOJI, KeyType.SPACE, KeyType.MIC, KeyType.ENTER)))
    }

    @Test fun `phonetic layout is unchanged`() {
        val p = Layouts.phonetic(numberRow = false, bnDigits = true)
        assertEquals("q", p[0][0].label)
        assertEquals(null, p[0][0].shiftLabel)
    }
}
