package com.banglakeys.app

/**
 * Roman → Bangla phonetic converter (Avro-style).
 * প্রতিবার পুরো roman buffer আবার convert করা হয়, তাই backspace-ও সহজ।
 *
 *  - ব্যঞ্জনের পর ব্যঞ্জন লিখলে আপনা-আপনি যুক্তাক্ষর (kt → ক্ত)
 *  - ব্যঞ্জনের পর 'o' = inherent vowel (যুক্তাক্ষর আটকায়, কিছু print হয় না)
 *  - ও-কার/ও: 'O'
 *  - ং = ng, ঙ = Ng, ঞ = NG, ঁ = ^, ৎ = t``
 */
object Phonetic {

    private val signs = hashMapOf(
        "ng" to "ং",
        "^" to "ঁ",
        "t``" to "ৎ",
        "``" to "",
    )

    // independent form, kar (matra) form
    private val vowels = hashMapOf(
        "rri" to Pair("ঋ", "ৃ"),
        "OI" to Pair("ঐ", "ৈ"),
        "OU" to Pair("ঔ", "ৌ"),
        "oo" to Pair("উ", "ু"),
        "ee" to Pair("ঈ", "ী"),
        "ii" to Pair("ঈ", "ী"),
        "a" to Pair("আ", "া"),
        "i" to Pair("ই", "ি"),
        "I" to Pair("ঈ", "ী"),
        "u" to Pair("উ", "ু"),
        "U" to Pair("ঊ", "ূ"),
        "e" to Pair("এ", "ে"),
        "O" to Pair("ও", "ো"),
    )

    private val consonants = hashMapOf(
        "kkh" to "ক্ষ",
        "kh" to "খ", "gh" to "ঘ", "Ng" to "ঙ", "ch" to "ছ", "jh" to "ঝ", "NG" to "ঞ",
        "Th" to "ঠ", "Dh" to "ঢ", "th" to "থ", "dh" to "ধ", "ph" to "ফ", "bh" to "ভ",
        "Rh" to "ঢ়", "Sh" to "ষ", "sh" to "শ",
        "k" to "ক", "g" to "গ", "c" to "চ", "j" to "জ", "T" to "ট", "D" to "ড", "N" to "ণ",
        "t" to "ত", "d" to "দ", "n" to "ন", "p" to "প", "f" to "ফ", "b" to "ব", "v" to "ভ",
        "m" to "ম", "z" to "য", "r" to "র", "l" to "ল", "S" to "শ", "s" to "স", "h" to "হ",
        "R" to "ড়", "y" to "য়", "Y" to "য়", "q" to "ক", "J" to "জ", "x" to "ক্স", "C" to "ছ",
    )

    fun convert(input: String): String {
        val src = StringBuilder(input)
        val sb = StringBuilder()
        var i = 0
        var cons = false   // শেষে বসানো অংশ ব্যঞ্জন (kar নিতে বা যুক্ত হতে পারে)
        var start = true   // এখনো কিছু বসেনি

        while (i < src.length) {
            var matched = false
            var len = minOf(3, src.length - i)
            while (len >= 1) {
                val key = src.substring(i, i + len)

                val sign = signs[key]
                if (sign != null) {
                    sb.append(sign)
                    cons = false; start = false
                    i += len; matched = true
                    break
                }

                val v = vowels[key]
                if (v != null) {
                    sb.append(if (cons) v.second else v.first)
                    cons = false; start = false
                    i += len; matched = true
                    break
                }

                if (key == "o") {
                    if (cons) {
                        cons = false              // inherent vowel: কিছু print হবে না
                    } else {
                        sb.append(if (start) "অ" else "ও")
                    }
                    start = false
                    i += 1; matched = true
                    break
                }

                if (key == "w") {
                    if (cons) {
                        sb.append("্ব")           // ব-ফলা (স্ব, দ্ব...)
                        cons = true
                    } else {
                        sb.append("ও")
                        cons = false
                    }
                    start = false
                    i += 1; matched = true
                    break
                }

                val c = consonants[key]
                if (c != null) {
                    if (cons) {
                        sb.append('্')
                        // ব্যঞ্জনের পর y = য-ফলা (ত্য), য় নয়
                        sb.append(if (key == "y" || key == "Y") "য" else c)
                    } else {
                        sb.append(c)
                    }
                    cons = true; start = false
                    i += len; matched = true
                    break
                }

                len--
            }

            if (!matched) {
                val ch = src[i]
                if (ch.isUpperCase()) {
                    // 'B', 'P' ইত্যাদি যেগুলোর আলাদা rule নেই, সেগুলো ছোট হাতের মতো ধরা হবে
                    src.setCharAt(i, ch.lowercaseChar())
                    continue
                }
                sb.append(ch)
                cons = false; start = false
                i++
            }
        }
        return sb.toString()
    }
}
