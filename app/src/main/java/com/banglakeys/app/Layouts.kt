package com.banglakeys.app

enum class KeyType {
    CHAR,
    SHIFT,
    DELETE,
    ENTER,
    SPACE,
    SYMBOLS,
    SYMBOLS2,
    ABC,
    LANG,
    SPACER,
    SNIPPET,
    NOTEPAD,
    TRANSLATE,
    MIC,
    ASSISTANT,
}

/**
 * label  : key-তে যা দেখায় (এবং CHAR key-র output)
 * alt    : উপরের ডানে ছোট করে দেখায়, long-press করলে এটাই type হয়
 * weight : row-এর মধ্যে key-র প্রস্থ (সব key-র weight যোগ = 10)
 */
class Key(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val weight: Float = 1f,
    val alt: String? = null,
)

/**
 * সব layout এখানে। Ridmik-এর মতো বদলাতে চাইলে শুধু এই file edit করলেই হবে।
 */
object Layouts {

    private fun letters(chars: String, alts: String? = null): List<Key> =
        chars.mapIndexed { i, c -> Key(c.toString(), alt = alts?.getOrNull(i)?.toString()) }

    private fun digitsOf(bangla: Boolean) = if (bangla) "১২৩৪৫৬৭৮৯০" else "1234567890"

    private fun spacer(w: Float) = Key("", KeyType.SPACER, w)

    private fun letterRows(numberRow: Boolean, bnDigits: Boolean, bangla: Boolean): List<List<Key>> {
        val d = digitsOf(bnDigits)
        val rows = ArrayList<List<Key>>()

        if (numberRow) rows.add(letters(d))

        // Row 1
        rows.add(letters("qwertyuiop", if (numberRow) null else d))

        // Row 2
        rows.add(listOf(spacer(0.5f)) + letters("asdfghjkl", "@#৳_&-+()") + spacer(0.5f))

        // Row 3
        rows.add(
            listOf(Key("⇧", KeyType.SHIFT, 1.5f)) +
                letters("zxcvbnm", "*\"':;!?") +
                Key("⌫", KeyType.DELETE, 1.5f)
        )

        // Row 4
        rows.add(
            listOf(
                Key("?123", KeyType.SYMBOLS, 1.5f),
                Key("LANG", KeyType.LANG, 1f),
                Key(","),
                Key(" ", KeyType.SPACE, 2f),
                if (bangla) Key("।", alt = ".") else Key("."),
                Key("NOTE", KeyType.NOTEPAD, 1f),
                Key("Tr", KeyType.TRANSLATE, 1f),
                Key("MIC", KeyType.MIC, 1f),
                Key("BOT", KeyType.ASSISTANT, 1f),
                Key("ENTER", KeyType.ENTER, 1.5f),
            )
        )
        return rows
    }

    fun phonetic(numberRow: Boolean, bnDigits: Boolean): List<List<Key>> =
        letterRows(numberRow, bnDigits, true)

    fun english(numberRow: Boolean): List<List<Key>> =
        letterRows(numberRow, false, false)

    fun symbols(bnDigits: Boolean, taka: Boolean): List<List<Key>> = listOf(
        letters(digitsOf(bnDigits)),
        letters(if (taka) "@#৳_&-+()/" else "@#\$_&-+()/"),
        listOf(Key("=\\<", KeyType.SYMBOLS2, 1.5f)) +
            letters("*\"':;!?") +
            Key("⌫", KeyType.DELETE, 1.5f),
        bottomSymbols(),
    )

    fun symbols2(): List<List<Key>> = listOf(
        letters("~`|•√π÷×¶∆"),
        letters("£¢€¥^°={}\\"),
        listOf(Key("?123", KeyType.SYMBOLS, 1.5f)) +
            letters("%©®™✓[]") +
            Key("⌫", KeyType.DELETE, 1.5f),
        listOf(
            Key("ABC", KeyType.ABC, 2f),
            Key("<"),
            Key(" ", KeyType.SPACE, 4f),
            Key(">"),
            Key("⏎", KeyType.ENTER, 2f),
        ),
    )

    /** Notepad panel: সেভ করা note + clipboard history-র button-গুলো দেখায়। */
    fun panel(items: List<String>): List<List<Key>> {
        val rows = ArrayList<List<Key>>()
        rows.add(listOf(Key("‹ কিবোর্ডে ফিরুন", KeyType.ABC, 10f)))
        if (items.isEmpty()) {
            rows.add(listOf(Key("কোনো note/clipboard নেই", KeyType.SPACER, 10f)))
            return rows
        }
        var row = ArrayList<Key>()
        var used = 0f
        for (raw in items) {
            val display = if (raw.length > 18) raw.take(16) + "…" else raw
            if (used + 5f > 10f) {
                rows.add(row)
                row = ArrayList()
                used = 0f
            }
            row.add(Key(display, KeyType.SNIPPET, 5f, alt = raw))
            used += 5f
        }
        if (row.isNotEmpty()) rows.add(row)
        return rows
    }

    private fun bottomSymbols(): List<Key> = listOf(
        Key("ABC", KeyType.ABC, 2f),
        Key(","),
        Key(" ", KeyType.SPACE, 4f),
        Key("."),
        Key("⏎", KeyType.ENTER, 2f),
    )
}
