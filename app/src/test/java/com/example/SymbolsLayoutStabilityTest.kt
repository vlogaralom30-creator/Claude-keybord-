package com.example

import com.banglakeys.app.KeyType
import com.banglakeys.app.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ?123 পেজে কিবোর্ড যেন না লাফায়/key না সরে — row সংখ্যা, সারিবদ্ধতা, নিচের সারি। */
class SymbolsLayoutStabilityTest {

    private fun total(row: List<com.banglakeys.app.Key>) = row.sumOf { it.weight.toDouble() }

    @Test fun `symbols and symbols2 have consistent four rows`() {
        assertEquals(4, Layouts.symbols(true, true).size)
        assertEquals(4, Layouts.symbols(false, false).size)
        assertEquals(4, Layouts.symbols2().size)
    }

    @Test fun `letter and symbols2 rows sum to ten`() {
        val pages = listOf(
            Layouts.phonetic(true, true),
            Layouts.phonetic(false, true),
            Layouts.english(true),
            Layouts.symbols2(),
            Layouts.probhat(true),
        )
        for (p in pages) for (row in p) assertEquals(10.0, total(row), 0.01)
    }

    @Test fun `symbols page keeps digits row and action shortcuts`() {
        val first = Layouts.symbols(true, true).first()
        assertEquals(14, first.size)
        assertTrue(first.take(10).all { it.type == KeyType.CHAR })
        assertTrue(first.map { it.type }.containsAll(listOf(KeyType.NOTEPAD, KeyType.TRANSLATE, KeyType.ASSISTANT, KeyType.SECRET)))
    }

    @Test fun `symbols page keeps space and enter`() {
        val last = Layouts.symbols(true, true).last().map { it.type }
        assertTrue(last.containsAll(listOf(KeyType.ABC, KeyType.SPACE, KeyType.ENTER)))
    }
}
