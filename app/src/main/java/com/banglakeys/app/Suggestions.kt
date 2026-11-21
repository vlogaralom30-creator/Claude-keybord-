package com.banglakeys.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * টাইপ করার সময় শব্দ suggest করে, এবং ব্যবহারকারী কী টাইপ করছে তা থেকে শেখে।
 *
 * দুই রকম শেখা হয়:
 *  ১) roman prefix (যা বাফারে টাইপ হচ্ছে) -> কোন বাংলা/ইংরেজি শব্দ commit হলো
 *  ২) আগের শব্দ -> তার পরের শব্দ (bigram), যাতে space দেওয়ার পরপরই কিছু suggest করা যায়
 *
 * সবকিছু SharedPreferences-এ ছোট JSON আকারে সেভ থাকে, কোনো external library লাগে না।
 */
class Suggestions(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bk_suggest", Context.MODE_PRIVATE)

    private val learned: MutableMap<String, MutableMap<String, Int>> = HashMap()
    private val bigram: MutableMap<String, MutableMap<String, Int>> = HashMap()
    private var lastCommittedWord: String = ""

    init {
        load()
    }

    /**
     * [prefix] হলো এখন পর্যন্ত টাইপ করা roman text (বাফার)। খালি হলে আগের শব্দ
     * অনুযায়ী পরের শব্দ suggest করা হয়।
     */
    fun suggestionsFor(prefix: String, bangla: Boolean): List<String> {
        if (prefix.isBlank()) return nextWordSuggestions()

        val key = prefix.lowercase()
        val bucket = LinkedHashMap<String, Int>()

        for ((k, words) in learned) {
            if (k.startsWith(key)) {
                for ((w, f) in words) bucket[w] = (bucket[w] ?: 0) + f
            }
        }

        if (bangla) {
            for ((roman, bn) in SEED_ROMAN_BN) {
                if (roman.startsWith(key)) bucket[bn] = (bucket[bn] ?: 0) + 1
            }
        } else {
            for (w in SEED_EN) {
                if (w.startsWith(key)) bucket[w] = (bucket[w] ?: 0) + 1
            }
        }

        return bucket.entries.sortedByDescending { it.value }.take(MAX_SUGGESTIONS).map { it.key }
    }

    private fun nextWordSuggestions(): List<String> {
        val m = bigram[lastCommittedWord] ?: return emptyList()
        return m.entries.sortedByDescending { it.value }.take(MAX_SUGGESTIONS).map { it.key }
    }

    /** একটা শব্দ commit হওয়ার ঠিক আগে/পরে call করুন (space/enter/punctuation এর সময়)। */
    fun learn(romanPrefix: String, committedWord: String) {
        if (committedWord.isBlank()) return
        val key = romanPrefix.lowercase().ifBlank { committedWord.lowercase() }

        val bucket = learned.getOrPut(key) { HashMap() }
        bucket[committedWord] = (bucket[committedWord] ?: 0) + 1

        if (lastCommittedWord.isNotEmpty()) {
            val nb = bigram.getOrPut(lastCommittedWord) { HashMap() }
            nb[committedWord] = (nb[committedWord] ?: 0) + 1
        }
        lastCommittedWord = committedWord
        save()
    }

    /** বাক্য শেষ হলে (enter / । / .) call করুন, যাতে পরের বাক্যের শুরুতে ভুল next-word suggestion না আসে। */
    fun resetSentenceContext() {
        lastCommittedWord = ""
    }

    // ---------------- persistence ----------------

    private fun save() {
        val root = JSONObject()
        root.put("learned", toJson(learned))
        root.put("bigram", toJson(bigram))
        prefs.edit().putString("data", root.toString()).apply()
    }

    private fun toJson(map: Map<String, Map<String, Int>>): JSONObject {
        val out = JSONObject()
        for ((k, words) in map) {
            val wj = JSONObject()
            for ((w, f) in words) wj.put(w, f)
            out.put(k, wj)
        }
        return out
    }

    private fun load() {
        val s = prefs.getString("data", null) ?: return
        try {
            val root = JSONObject(s)
            loadInto(root.optJSONObject("learned"), learned)
            loadInto(root.optJSONObject("bigram"), bigram)
        } catch (_: Exception) {
            // পুরনো/নষ্ট data পেলে চুপচাপ খালি রাখি, crash করবে না
        }
    }

    private fun loadInto(obj: JSONObject?, target: MutableMap<String, MutableMap<String, Int>>) {
        if (obj == null) return
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val wj = obj.getJSONObject(k)
            val bucket = HashMap<String, Int>()
            val wKeys = wj.keys()
            while (wKeys.hasNext()) {
                val w = wKeys.next()
                bucket[w] = wj.getInt(w)
            }
            target[k] = bucket
        }
    }

    companion object {
        private const val MAX_SUGGESTIONS = 3

        // cold-start suggestion-এর জন্য কিছু common roman -> বাংলা শব্দ।
        // ব্যবহারকারী নিজে যা টাইপ করবে, সেটাই সময়ের সাথে প্রাধান্য পাবে।
        private val SEED_ROMAN_BN = mapOf(
            "ami" to "আমি", "tumi" to "তুমি", "apni" to "আপনি", "amra" to "আমরা",
            "tomra" to "তোমরা", "se" to "সে", "tara" to "তারা", "ki" to "কি",
            "kemon" to "কেমন", "kothay" to "কোথায়", "kobe" to "কবে", "keno" to "কেন",
            "valo" to "ভালো", "bhalo" to "ভালো", "khub" to "খুব", "onek" to "অনেক",
            "dhonnobad" to "ধন্যবাদ", "dhanyabad" to "ধন্যবাদ", "shuvo" to "শুভ",
            "ache" to "আছে", "achi" to "আছি", "achen" to "আছেন", "hobe" to "হবে",
            "korbo" to "করবো", "korchi" to "করছি", "korte" to "করতে", "hoyeche" to "হয়েছে",
            "bolo" to "বলো", "bolchi" to "বলছি", "dekho" to "দেখো", "jabo" to "যাবো",
            "asbo" to "আসবো", "ekhon" to "এখন", "age" to "আগে", "pore" to "পরে",
            "kal" to "কাল", "aj" to "আজ", "bari" to "বাড়ি", "kaj" to "কাজ",
            "somoy" to "সময়", "taka" to "টাকা", "manush" to "মানুষ", "bondhu" to "বন্ধু",
            "bhai" to "ভাই", "bon" to "বোন", "ma" to "মা", "baba" to "বাবা",
            "ha" to "হ্যাঁ", "na" to "না", "thik" to "ঠিক", "kintu" to "কিন্তু",
            "ebong" to "এবং", "keu" to "কেউ", "kichu" to "কিছু", "shob" to "সব",
        )

        private val SEED_EN = listOf(
            "the", "and", "you", "are", "for", "with", "this", "that", "have",
            "will", "can", "please", "thanks", "regards", "hello", "okay",
        )
    }
}
