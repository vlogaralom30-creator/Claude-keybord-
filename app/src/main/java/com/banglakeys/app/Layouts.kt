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
    SECRET,
    EMOJI,
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
    /** Fixed layout (প্রভাত): Shift চাপলে এই অক্ষর বসে (null হলে label.uppercase())। */
    val shiftLabel: String? = null,
)

enum class KeyboardLanguageMode {
    AVRO,
    BANGLA,
    ENGLISH;

    fun next(): KeyboardLanguageMode = when (this) {
        AVRO -> BANGLA
        BANGLA -> ENGLISH
        ENGLISH -> AVRO
    }

    fun spaceLabel(): String = when (this) {
        AVRO -> "অভ্র"
        BANGLA -> "বাংলা"
        ENGLISH -> "English"
    }

    fun toastLabel(): String = when (this) {
        AVRO -> "অভ্র (Phonetic)"
        BANGLA -> "বাংলা (${Layouts.PROBHAT_NAME})"
        ENGLISH -> "English"
    }

    val isBangla: Boolean get() = this != ENGLISH
    val isProbhat: Boolean get() = this == BANGLA
    val isPhonetic: Boolean get() = this == AVRO

    fun prefLayoutValue(): String = if (this == BANGLA) "probhat" else "phonetic"

    companion object {
        fun from(bangla: Boolean, bnLayout: String?): KeyboardLanguageMode = when {
            !bangla -> ENGLISH
            bnLayout == "probhat" -> BANGLA
            else -> AVRO
        }
    }
}

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

        // Row 4 (৮টি key — Emoji + MIC সহজে নাগালে)
        rows.add(bottomRow(bangla))
        return rows
    }

    private fun bottomRow(bangla: Boolean): List<Key> = listOf(
        Key("?123", KeyType.SYMBOLS, 1.2f),
        Key("LANG", KeyType.LANG, 0.95f),
        Key("🙂", KeyType.EMOJI, 0.95f),
        Key(",", weight = 0.7f),
        Key(" ", KeyType.SPACE, 3.0f),
        if (bangla) Key("।", weight = 0.75f, alt = ".") else Key(".", weight = 0.75f),
        Key("MIC", KeyType.MIC, 1.0f),
        Key("ENTER", KeyType.ENTER, 1.45f),
    )

    // ------------------------------------------------------------------
    // প্রভাত (Probhat) — Ridmik-এর fixed Bangla layout। প্রতিটা key সরাসরি বাংলা অক্ষর দেয়।
    // Shift = বাঁদিকের নিচের ⇧; হিন্ট (ডানে-উপরে) = Shift-অক্ষর; দীর্ঘ চাপ = Shift-অক্ষর।
    // ------------------------------------------------------------------

    /** `.` key-র অক্ষর (স্ক্রিনশট থেকে অনুমান: নুক্তা ়; Shift = ঁ)। ভিন্ন হলে শুধু এখানে বদলান। */
    const val PROBHAT_KEY_DOT = "়"
    const val PROBHAT_KEY_DOT_SHIFT = "ঁ"
    const val PROBHAT_NAME = "প্রভাত"

    private fun pair(label: String, shift: String, w: Float = 1f) =
        Key(label, weight = w, alt = shift, shiftLabel = shift)

    fun probhat(bnDigits: Boolean): List<List<Key>> {
        val bn = "১২৩৪৫৬৭৮৯০"
        val en = "1234567890"
        val numbers = if (bnDigits) bn.mapIndexed { i, c -> Key(c.toString(), alt = en[i].toString()) }
        else letters(en)

        val w12 = 10f / 12f
        val top = listOf(
            pair("দ", "ধ", w12), pair("ূ", "ঊ", w12), pair("ী", "ঈ", w12), pair("র", "ড়", w12),
            pair("ট", "ঠ", w12), pair("এ", "ঐ", w12), pair("ু", "উ", w12), pair("ি", "ই", w12),
            pair("ও", "ঔ", w12), pair("প", "ফ", w12), pair("ে", "ৈ", w12), pair("ো", "ৌ", w12),
        )

        val home = listOf(spacer(0.5f)) + listOf(
            pair("া", "অ"), pair("স", "ষ"), pair("ড", "ঢ"), pair("ত", "থ"), pair("গ", "ঘ"),
            pair("হ", "ঃ"), pair("জ", "ঝ"), pair("ক", "খ"), pair("ল", "ং"),
        ) + spacer(0.5f)

        val w = 0.8f
        val bottom = listOf(Key("⇧", KeyType.SHIFT, 1.3f)) + listOf(
            pair("য়", "য", w), pair("শ", "ঢ়", w), pair("চ", "ছ", w), pair("আ", "ঋ", w),
            pair("ব", "ভ", w), pair("ন", "ণ", w), pair("ম", "ঙ", w),
            Key("্", weight = w),
            pair(PROBHAT_KEY_DOT, PROBHAT_KEY_DOT_SHIFT, w),
        ) + Key("⌫", KeyType.DELETE, 1.5f)

        return listOf(numbers, top, home, bottom, bottomRow(bangla = true))
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
            Key("ENTER", KeyType.ENTER, 2f),
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
        Key("ENTER", KeyType.ENTER, 2f),
    )
}
