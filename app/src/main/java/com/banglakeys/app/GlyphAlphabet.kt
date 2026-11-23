package com.banglakeys.app

/** এখন ৩টা glyph style আছে — প্রতিটার নিজের আলাদা (একে-অপরের থেকে disjoint) ৩২টা চিহ্ন। */
enum class GlyphStyle(val label: String) {
    RUNIC("ᚠᚢᚦ Runic"),
    DOTS("⠿⠟⠯ Dots"),
    SHAPES("◆▲● Shapes"),
}

/**
 * Encrypted byte data-কে কোনো একটা glyph style দিয়ে লেখা text-এ রূপান্তর করে, আর
 * ফিরিয়েও আনে। প্রতিটা style base-32-এর মতো কাজ করে (৩২টা চিহ্ন = ৫ বিট প্রতিটায়),
 * শুধু আলাদা দেখতে চিহ্ন ব্যবহার করে।
 *
 * style-গুলোর চিহ্ন-সেট ইচ্ছাকৃতভাবে একে অপরের সাথে ওভারল্যাপ করে না (disjoint), তাই
 * [decodeAny] কোনো text দেখেই বলতে পারে এটা কোন style-এ লেখা, sender কোন style
 * বেছেছিল তা আলাদা করে জানানো লাগে না।
 *
 * মনে রাখবেন: style শুধু ছদ্মবেশ — নিরাপত্তা আসে SecretCipher.kt-এর passphrase থেকে।
 */
object GlyphAlphabet {

    private fun runicGlyphs() = (0 until 32).map { (0x16A0 + it).toChar() } // Runic block
    private fun dotGlyphs() = (0 until 32).map { (0x2800 + it * 2).toChar() } // Braille block (স্পার্স, আলাদা থাকার জন্য)
    private fun shapeGlyphs(): List<Char> {
        // জ্যামিতিক আকৃতি (Geometric Shapes ইউনিকোড ব্লক U+25A0 থেকে) থেকে ৩২টা বাছা।
        return (0 until 32).map { (0x25A0 + it).toChar() }
    }

    private val SETS: Map<GlyphStyle, List<Char>> = mapOf(
        GlyphStyle.RUNIC to runicGlyphs(),
        GlyphStyle.DOTS to dotGlyphs(),
        GlyphStyle.SHAPES to shapeGlyphs(),
    )

    private val INDEXES: Map<GlyphStyle, Map<Char, Int>> =
        SETS.mapValues { (_, glyphs) -> glyphs.withIndex().associate { (i, c) -> c to i } }

    fun encode(data: ByteArray, style: GlyphStyle): String {
        val glyphs = SETS.getValue(style)
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(glyphs[(buffer shr bits) and 0x1F])
            }
        }
        if (bits > 0) sb.append(glyphs[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    private fun decode(text: String, style: GlyphStyle): ByteArray? {
        val index = INDEXES.getValue(style)
        val clean = text.filter { it in index }
        if (clean.isEmpty()) return null
        val out = ArrayList<Byte>()
        var buffer = 0
        var bits = 0
        for (ch in clean) {
            val v = index[ch] ?: return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    /** কোন style-এ লেখা তা নিজে বুঝে, decode করে — sender কোন style ব্যবহার করেছিল জানা লাগে না। */
    fun decodeAny(text: String): ByteArray? {
        val style = detectStyle(text) ?: return null
        return decode(text, style)
    }

    /** কোনো text এই যেকোনো glyph-লিপির মতো লাগছে কিনা — auto-detect overlay-র জন্য। */
    fun looksLikeGlyphText(text: String): Boolean = detectStyle(text) != null

    private fun detectStyle(text: String): GlyphStyle? {
        val letters = text.filter { !it.isWhitespace() }
        if (letters.length < 6) return null
        for ((style, index) in INDEXES) {
            val matching = letters.count { it in index }
            if (matching.toFloat() / letters.length > 0.9f) return style
        }
        return null
    }
}
