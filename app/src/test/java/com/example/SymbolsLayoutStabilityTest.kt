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

    @Test fun `letter, symbols, and symbols2 rows sum to ten`() {
        val pages = listOf(
            Layouts.phonetic(true, true),
            Layouts.phonetic(false, true),
            Layouts.english(true),
            Layouts.symbols(true, true),
            Layouts.symbols(false, false),
            Layouts.symbols2(),
            Layouts.probhat(true),
        )
        for (p in pages) for (row in p) assertEquals(10.0, total(row), 0.01)
    }

    @Test fun `symbols page keeps clean 10-digit row without extra icon shortcuts`() {
        val first = Layouts.symbols(true, true).first()
        assertEquals(10, first.size)
        assertTrue(first.all { it.type == KeyType.CHAR })
    }

    @Test fun `symbols page keeps space and enter`() {
        val last = Layouts.symbols(true, true).last().map { it.type }
        assertTrue(last.containsAll(listOf(KeyType.ABC, KeyType.SPACE, KeyType.ENTER)))
    }
}
