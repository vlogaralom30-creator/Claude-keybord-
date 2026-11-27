package com.banglakeys.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * টাইপ করার সময় শব্দ suggest করে, এবং ব্যবহারকারী কী টাইপ করছে তা থেকে শেখে।
 *
 * - বাংলা ও ইংরেজি শব্দভাণ্ডার এবং bigram সম্পূর্ণ আলাদা রাখা হয় যাতে এক ভাষার শব্দ অন্য ভাষায় না আসে।
 * - খালি prefix-এ (idle state) আগের শব্দের bigram ও ঘনঘন ব্যবহৃত starter শব্দ দেওয়া হয়।
 * - long-press করে শেখা শব্দ ভুলে যাওয়া (forget) যায়।
 */
class Suggestions(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bk_suggest", Context.MODE_PRIVATE)

    private val learnedBn: MutableMap<String, MutableMap<String, Int>> = HashMap()
    private val learnedEn: MutableMap<String, MutableMap<String, Int>> = HashMap()
    private val bigramBn: MutableMap<String, MutableMap<String, Int>> = HashMap()
    private val bigramEn: MutableMap<String, MutableMap<String, Int>> = HashMap()

    private var lastCommittedBn: String = ""
    private var lastCommittedEn: String = ""

    init {
        load()
    }

    /**
     * [prefix] হলো এখন পর্যন্ত টাইপ করা অক্ষর। খালি হলে idle/next-word suggestion ফেরত দেয়।
     */
    fun suggestionsFor(prefix: String, bangla: Boolean): List<String> {
        if (prefix.isBlank()) return idleSuggestions(bangla)

        val key = prefix.lowercase()
        val scores = LinkedHashMap<String, Int>()
        val learnedMap = if (bangla) learnedBn else learnedEn

        // ১) ব্যবহারকারীর শেখা শব্দে সর্বোচ্চ অগ্রাধিকার
        for ((k, words) in learnedMap) {
            val matchBonus = when {
                k == key -> 80
                k.startsWith(key) -> 30 - (k.length - key.length).coerceAtMost(20)
                else -> -1
            }
            if (matchBonus >= 0) {
                for ((w, freq) in words) {
                    val out = if (bangla) w else applyCase(w, prefix)
                    val score = freq * 25 + matchBonus
                    scores[out] = maxOf(scores[out] ?: 0, score)
                }
            }
        }

        // ২) বিল্ট-ইন অভিধান ও ফনেটিক ম্যাচিং
        if (bangla) {
            val liveBn = Phonetic.convert(prefix)
            if (liveBn.isNotBlank() && isBanglaWord(liveBn)) {
                scores[liveBn] = maxOf(scores[liveBn] ?: 0, 42)
            }

            for (i in SEED_ROMAN_BN.indices) {
                val (roman, bn) = SEED_ROMAN_BN[i]
                val baseRank = (SEED_ROMAN_BN.size - i).coerceAtLeast(1)
                val matchScore = when {
                    roman == key -> 75 + baseRank
                    roman.startsWith(key) -> 28 + baseRank - (roman.length - key.length).coerceAtMost(18)
                    key.length >= 2 && fuzzyPhoneticMatch(roman, key) -> 12 + baseRank / 4
                    else -> -1
                }
                if (matchScore > 0) {
                    scores[bn] = maxOf(scores[bn] ?: 0, matchScore)
                }
            }
        } else {
            for (i in SEED_EN.indices) {
                val w = SEED_EN[i]
                val baseRank = (SEED_EN.size - i).coerceAtLeast(1)
                val matchScore = when {
                    w == key -> 75 + baseRank
                    w.startsWith(key) -> 26 + baseRank - (w.length - key.length).coerceAtMost(18)
                    else -> -1
                }
                if (matchScore > 0) {
                    val cased = applyCase(w, prefix)
                    scores[cased] = maxOf(scores[cased] ?: 0, matchScore)
                }
            }
        }

        return scores.entries
            .sortedByDescending { it.value }
            .take(MAX_SUGGESTIONS)
            .map { it.key }
    }

    private fun idleSuggestions(bangla: Boolean): List<String> {
        val result = ArrayList<String>(MAX_SUGGESTIONS)
        val seen = HashSet<String>()

        val bigramMap = if (bangla) bigramBn else bigramEn
        val lastWord = if (bangla) lastCommittedBn else lastCommittedEn

        if (lastWord.isNotEmpty()) {
            bigramMap[lastWord]
                ?.entries
                ?.sortedByDescending { it.value }
                ?.forEach { (w, _) ->
                    if (result.size < MAX_SUGGESTIONS && seen.add(w)) {
                        result.add(w)
                    }
                }
        }

        // পরের ধাপে ব্যবহারকারীর সবচেয়ে বেশি ব্যবহৃত শব্দ থেকে বাকি স্লট পূর্ণ করা হয়
        if (result.size < MAX_SUGGESTIONS) {
            val learnedMap = if (bangla) learnedBn else learnedEn
            val topLearned = HashMap<String, Int>()
            for ((_, bucket) in learnedMap) {
                for ((word, freq) in bucket) {
                    topLearned[word] = (topLearned[word] ?: 0) + freq
                }
            }
            topLearned.entries
                .sortedByDescending { it.value }
                .forEach { (w, _) ->
                    if (result.size < MAX_SUGGESTIONS && seen.add(w)) {
                        result.add(w)
                    }
                }
        }

        val starters = if (bangla) STARTER_BN else STARTER_EN
        for (w in starters) {
            if (result.size >= MAX_SUGGESTIONS) break
            if (seen.add(w)) result.add(w)
        }

        return result
    }

    /**
     * একটি শব্দ commit হওয়ার সময় call করুন যাতে ভবিষ্যতে আরও দ্রুত suggest করা যায়।
     */
    fun learn(
        romanPrefix: String,
        committedWord: String,
        bangla: Boolean = isBanglaWord(committedWord)
    ) {
        val word = committedWord.trim()
        if (word.isBlank()) return
        val normalizedWord = if (bangla) word else word
        val key = romanPrefix.trim().lowercase().ifBlank { normalizedWord.lowercase() }

        val learnedMap = if (bangla) learnedBn else learnedEn
        val storeWord = if (bangla) normalizedWord else normalizedWord.lowercase()
        val bucket = learnedMap.getOrPut(key) { HashMap() }
        bucket[storeWord] = (bucket[storeWord] ?: 0) + 1

        // ইংরেজি proper-case শব্দ হলে মূল বানানও রাখি যাতে প্রয়োজনে পাওয়া যায়
        if (!bangla && normalizedWord != storeWord && normalizedWord.firstOrNull()?.isUpperCase() == true) {
            bucket[normalizedWord] = (bucket[normalizedWord] ?: 0) + 1
        }

        val bigramMap = if (bangla) bigramBn else bigramEn
        val prev = if (bangla) lastCommittedBn else lastCommittedEn
        val bigramKey = if (bangla) normalizedWord else storeWord
        if (prev.isNotEmpty() && prev != bigramKey) {
            val nb = bigramMap.getOrPut(prev) { HashMap() }
            nb[bigramKey] = (nb[bigramKey] ?: 0) + 1
        }

        if (bangla) {
            lastCommittedBn = bigramKey
        } else {
            lastCommittedEn = bigramKey
        }
        save()
    }

    fun isLearned(word: String, bangla: Boolean): Boolean {
        val target = if (bangla) word.trim() else word.trim().lowercase()
        val map = if (bangla) learnedBn else learnedEn
        return map.values.any { bucket ->
            bucket.containsKey(word.trim()) || (!bangla && bucket.containsKey(target))
        }
    }

    /**
     * ব্যবহারকারী কোনো শেখা শব্দ long-press করে মুছে ফেলতে চাইলে সেটি store থেকে বাদ দেয়।
     */
    fun forget(word: String, bangla: Boolean): Boolean {
        val raw = word.trim()
        if (raw.isEmpty()) return false
        val low = raw.lowercase()
        val learnedMap = if (bangla) learnedBn else learnedEn
        val bigramMap = if (bangla) bigramBn else bigramEn

        var removed = false
        val lIt = learnedMap.entries.iterator()
        while (lIt.hasNext()) {
            val bucket = lIt.next().value
            if (bucket.remove(raw) != null) removed = true
            if (!bangla && bucket.remove(low) != null) removed = true
            if (bucket.isEmpty()) lIt.remove()
        }

        if (bigramMap.remove(raw) != null) removed = true
        if (!bangla && bigramMap.remove(low) != null) removed = true
        val bIt = bigramMap.entries.iterator()
        while (bIt.hasNext()) {
            val bucket = bIt.next().value
            if (bucket.remove(raw) != null) removed = true
            if (!bangla && bucket.remove(low) != null) removed = true
            if (bucket.isEmpty()) bIt.remove()
        }

        if (removed) save()
        return removed
    }

    /** বাক্য শেষ হলে (enter / । / . / ! / ?) call করুন। */
    fun resetSentenceContext() {
        lastCommittedBn = ""
        lastCommittedEn = ""
    }

    data class Stats(
        val learnedBnWords: Int,
        val learnedEnWords: Int,
        val bigramCount: Int,
        val totalFrequency: Int,
    ) {
        val totalWords: Int get() = learnedBnWords + learnedEnWords
    }

    fun stats(): Stats {
        val bnWords = HashSet<String>()
        var freq = 0
        for ((_, bucket) in learnedBn) {
            for ((w, count) in bucket) {
                bnWords.add(w)
                freq += count
            }
        }
        val enWords = HashSet<String>()
        for ((_, bucket) in learnedEn) {
            for ((w, count) in bucket) {
                enWords.add(w.lowercase())
                freq += count
            }
        }
        var bigrams = 0
        for ((_, bucket) in bigramBn) bigrams += bucket.size
        for ((_, bucket) in bigramEn) bigrams += bucket.size
        return Stats(
            learnedBnWords = bnWords.size,
            learnedEnWords = enWords.size,
            bigramCount = bigrams,
            totalFrequency = freq,
        )
    }

    fun clearLearned() {
        learnedBn.clear()
        learnedEn.clear()
        bigramBn.clear()
        bigramEn.clear()
        lastCommittedBn = ""
        lastCommittedEn = ""
        save()
    }

    private fun fuzzyPhoneticMatch(roman: String, query: String): Boolean {
        val normRoman = normalizePhoneticRoman(roman)
        val normQuery = normalizePhoneticRoman(query)
        return normQuery.length >= 2 && normRoman.startsWith(normQuery)
    }

    private fun normalizePhoneticRoman(s: String): String = s
        .lowercase()
        .replace("bh", "v")
        .replace("ph", "f")
        .replace("sh", "s")
        .replace("oo", "u")
        .replace("ee", "i")
        .replace("kh", "k")

    private fun applyCase(word: String, prefix: String): String {
        if (prefix.isEmpty() || word.isEmpty()) return word
        val letters = prefix.filter { it.isLetter() }
        if (letters.isEmpty()) return word
        return when {
            letters.length >= 2 && letters.all { it.isUpperCase() } -> word.uppercase()
            prefix.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }

    // ---------------- persistence ----------------

    private fun save() {
        val root = JSONObject()
        root.put("version", 2)
        root.put("learned_bn", toJson(learnedBn))
        root.put("learned_en", toJson(learnedEn))
        root.put("bigram_bn", toJson(bigramBn))
        root.put("bigram_en", toJson(bigramEn))
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
            if (root.optInt("version", 1) >= 2) {
                loadInto(root.optJSONObject("learned_bn"), learnedBn)
                loadInto(root.optJSONObject("learned_en"), learnedEn)
                loadInto(root.optJSONObject("bigram_bn"), bigramBn)
                loadInto(root.optJSONObject("bigram_en"), bigramEn)
            } else {
                // পুরনো unified store থেকে ভাষা-ভিত্তিক ভাগ করে নেওয়া
                val legacyLearned = HashMap<String, MutableMap<String, Int>>()
                val legacyBigram = HashMap<String, MutableMap<String, Int>>()
                loadInto(root.optJSONObject("learned"), legacyLearned)
                loadInto(root.optJSONObject("bigram"), legacyBigram)
                for ((k, bucket) in legacyLearned) {
                    for ((w, freq) in bucket) {
                        val dst = if (isBanglaWord(w)) learnedBn else learnedEn
                        val b = dst.getOrPut(k) { HashMap() }
                        b[w] = (b[w] ?: 0) + freq
                    }
                }
                for ((k, bucket) in legacyBigram) {
                    val dst = if (isBanglaWord(k)) bigramBn else bigramEn
                    val b = dst.getOrPut(k) { HashMap() }
                    for ((w, freq) in bucket) b[w] = (b[w] ?: 0) + freq
                }
            }
        } catch (_: Exception) {
            // নষ্ট data পেলে চুপচাপ খালি রাখি, crash করবে না
        }
    }

    private fun loadInto(obj: JSONObject?, target: MutableMap<String, MutableMap<String, Int>>) {
        if (obj == null) return
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val wj = obj.optJSONObject(k) ?: continue
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
        const val MAX_SUGGESTIONS = 3

        fun isBanglaWord(text: String): Boolean =
            text.any { it in '\u0980'..'\u09FF' }

        private val STARTER_BN = listOf("আমি", "কেমন", "আজ")
        private val STARTER_EN = listOf("I", "The", "Hello")

        // তালিকাটি জনপ্রিয়তার ক্রমানুসারে সাজানো: আগে থাকা শব্দ বেশি rank পাবে
        private val SEED_ROMAN_BN: List<Pair<String, String>> = listOf(
            "ami" to "আমি", "amar" to "আমার", "amake" to "আমাকে", "amader" to "আমাদের", "amra" to "আমরা",
            "tumi" to "তুমি", "tomar" to "তোমার", "tomake" to "তোমাকে", "tomra" to "তোমরা", "tomader" to "তোমাদের",
            "apni" to "আপনি", "apnar" to "আপনার", "apnake" to "আপনাকে", "apnara" to "আপনারা",
            "se" to "সে", "tar" to "তার", "take" to "তাকে", "tara" to "তারা", "tader" to "তাদের",
            "ei" to "এই", "oi" to "ওই", "ekta" to "একটা", "ekti" to "একটি", "onek" to "অনেক",
            "ki" to "কি", "kemon" to "কেমন", "kothay" to "কোথায়", "kobe" to "কবে", "keno" to "কেন", "kivabe" to "কিভাবে",
            "bhalo" to "ভালো", "valo" to "ভালো", "khub" to "খুব", "sundor" to "সুন্দর", "shundor" to "সুন্দর",
            "dhonnobad" to "ধন্যবাদ", "dhanyabad" to "ধন্যবাদ", "shuvo" to "শুভ", "shuveccha" to "শুভেচ্ছা",
            "sokal" to "সকাল", "shokal" to "সকাল", "dupur" to "দুপুর", "bikal" to "বিকাল", "rat" to "রাত", "ratri" to "রাত্রি",
            "ha" to "হ্যাঁ", "hya" to "হ্যাঁ", "na" to "না", "thik" to "ঠিক", "thak" to "থাক",
            "ache" to "আছে", "achi" to "আছি", "achen" to "আছেন", "chilo" to "ছিল", "hobe" to "হবে", "hoy" to "হয়",
            "hoyeche" to "হয়েছে", "hoche" to "হচ্ছে", "hocche" to "হচ্ছে", "parbo" to "পারবো", "pari" to "পারি",
            "korbo" to "করবো", "korchi" to "করছি", "korche" to "করছে", "korte" to "করতে", "koro" to "করো", "korun" to "করুন",
            "bolo" to "বলো", "bolchi" to "বলছি", "bolun" to "বলুন", "bolte" to "বলতে", "kotha" to "কথা",
            "dekho" to "দেখো", "dekhi" to "দেখি", "dekhbo" to "দেখবো", "dekhte" to "দেখতে",
            "jabo" to "যাবো", "jacchi" to "যাচ্ছি", "jao" to "যাও", "jete" to "যেতে",
            "asbo" to "আসবো", "aschi" to "আসছি", "aso" to "আসো", "ashun" to "আসুন", "aste" to "আসতে",
            "ekhon" to "এখন", "akhon" to "এখন", "age" to "আগে", "pore" to "পরে", "ekhoni" to "এখনি",
            "aj" to "আজ", "ajke" to "আজকে", "kal" to "কাল", "kalke" to "কালকে", "goto" to "গত",
            "bangla" to "বাংলা", "bangladesh" to "বাংলাদেশ", "dhaka" to "ঢাকা", "chittagong" to "চট্টগ্রাম", "sylhet" to "সিলেট",
            "bari" to "বাড়ি", "ghor" to "ঘর", "bajar" to "বাজার", "rasta" to "রাস্তা", "desh" to "দেশ",
            "kaj" to "কাজ", "somoy" to "সময়", "shomoy" to "সময়", "taka" to "টাকা", "poisa" to "পয়সা",
            "manush" to "মানুষ", "lok" to "লোক", "bondhu" to "বন্ধু", "sathi" to "সাথী",
            "bhai" to "ভাই", "vai" to "ভাই", "bon" to "বোন", "ma" to "মা", "baba" to "বাবা", "ammi" to "আম্মি", "abbu" to "আব্বু",
            "dada" to "দাদা", "dadi" to "দাদী", "nana" to "নানা", "nani" to "নানী", "mama" to "মামা", "chacha" to "চাচা",
            "kintu" to "কিন্তু", "ebong" to "এবং", "ar" to "আর", "ba" to "বা", "tobe" to "তবে", "jodi" to "যদি",
            "keu" to "কেউ", "kichu" to "কিছু", "shob" to "সব", "sob" to "সব", "sobai" to "সবাই", "shobai" to "সবাই",
            "khawa" to "খাওয়া", "khabo" to "খাবো", "kheyechi" to "খেয়েছি", "pani" to "পানি", "jol" to "জল", "bhat" to "ভাত", "cha" to "চা",
            "chai" to "চাই", "lagbe" to "লাগবে", "dorkar" to "দরকার", "proyojon" to "প্রয়োজন",
            "bujhte" to "বুঝতে", "bujhechi" to "বুঝেছি", "jani" to "জানি", "janina" to "জানিনা",
            "phone" to "ফোন", "mobile" to "মোবাইল", "call" to "কল", "message" to "মেসেজ", "video" to "ভিডিও",
            "photo" to "ফটো", "chobi" to "ছবি", "gaan" to "গান", "gan" to "গান", "golpo" to "গল্প",
            "school" to "স্কুল", "college" to "কলেজ", "office" to "অফিস", "hospital" to "হাসপাতাল",
            "khushi" to "খুশি", "anondo" to "আনন্দ", "kosto" to "কষ্ট", "chinta" to "চিন্তা", "bhabna" to "ভাবনা",
            "poth" to "পথ", "nodir" to "নদীর", "nodi" to "নদী", "akash" to "আকাশ", "megh" to "মেঘ", "brishti" to "বৃষ্টি",
            "surjo" to "সূর্য", "chand" to "চাঁদ", "tara" to "তারা", "ful" to "ফুল", "gach" to "গাছ",
            "boi" to "বই", "kagoj" to "কাগজ", "kolom" to "কলম", "naam" to "নাম", "nam" to "নাম",
            "mon" to "মন", "hridoy" to "হৃদয়", "prithibi" to "পৃথিবী", "jibon" to "জীবন", "shopno" to "স্বপ্ন",
            "ek" to "এক", "dui" to "দুই", "tin" to "তিন", "char" to "চার", "pach" to "পাঁচ",
            "sath" to "সাথ", "sathe" to "সাথে", "moddhe" to "মধ্যে", "upore" to "উপরে", "niche" to "নিচে",
            "samne" to "সামনে", "pichone" to "পিছনে", "pashe" to "পাশে", "bhitore" to "ভিতরে", "baire" to "বাইরে",
            "asole" to "আসলে", "sotti" to "সত্যি", "shotti" to "সত্যি", "mittha" to "মিথ্যা",
            "notun" to "নতুন", "purono" to "পুরোনো", "boro" to "বড়", "choto" to "ছোট", "beshi" to "বেশি", "kom" to "কম",
            "aste" to "আস্তে", "taratari" to "তাড়াতাড়ি", "druto" to "দ্রুত", "ekshathe" to "একসাথে",
            "doya" to "দয়া", "khoma" to "ক্ষমা", "maaf" to "মাফ", "shukriya" to "শুকরিয়া",
            "assalamu" to "আসসালামু", "alaikum" to "আলাইকুম", "walaikum" to "ওয়ালাইকুম", "salam" to "সালাম",
            "inshallah" to "ইনশাআল্লাহ", "alhamdulillah" to "আলহামদুলিল্লাহ", "mashallah" to "মাশাআল্লাহ",
            "biday" to "বিদায়", "dekha" to "দেখা", "abar" to "আবার", "nischoy" to "নিশ্চয়", "oboshsho" to "অবশ্য",
        )

        private val SEED_EN = listOf(
            "the", "to", "and", "you", "that", "it", "for", "with", "this", "have",
            "are", "from", "they", "what", "will", "your", "can", "about", "when", "there",
            "hello", "hi", "hey", "thanks", "thank", "please", "sorry", "okay", "yes", "yeah",
            "good", "morning", "afternoon", "evening", "night", "today", "tomorrow", "yesterday",
            "great", "nice", "fine", "happy", "love", "friend", "family", "home", "work", "office",
            "call", "message", "send", "open", "close", "help", "need", "want", "know", "think",
            "going", "coming", "doing", "making", "working", "looking", "waiting", "talking",
            "because", "really", "very", "much", "more", "some", "many", "also", "just", "only",
            "right", "here", "where", "why", "how", "who", "which", "would", "could", "should",
            "time", "day", "week", "month", "year", "minute", "minutes", "hour", "hours", "soon",
            "later", "now", "already", "always", "never", "sometimes", "maybe", "sure", "definitely",
            "phone", "mobile", "video", "audio", "photo", "camera", "search", "google", "youtube",
            "whatsapp", "facebook", "email", "address", "number", "name", "location", "meeting",
            "start", "stop", "check", "update", "save", "delete", "copy", "paste", "share", "read",
            "write", "watch", "listen", "play", "music", "song", "game", "book", "news", "weather",
            "food", "water", "coffee", "tea", "dinner", "lunch", "breakfast", "school", "college",
            "class", "exam", "project", "report", "file", "folder", "note", "notes", "list", "plan",
            "money", "price", "bank", "order", "delivery", "market", "shop", "house", "room", "road",
            "brother", "sister", "mother", "father", "teacher", "doctor", "manager", "team", "group",
            "people", "person", "everyone", "someone", "anyone", "nothing", "everything", "something",
            "beautiful", "awesome", "amazing", "wonderful", "perfect", "important", "possible",
            "different", "special", "little", "small", "large", "first", "second", "next", "last",
            "before", "after", "between", "under", "around", "through", "without", "within", "against",
            "regards", "welcome", "congratulations", "care", "safe", "well", "better", "best",
        )
    }
}
