package com.banglakeys.app.robot

import android.content.Context
import android.content.SharedPreferences
import com.banglakeys.app.EmojiRepository
import com.banglakeys.app.Suggestions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.text.BreakIterator
import kotlin.math.roundToInt

enum class LearningStage(
    val titleBn: String,
    val titleEn: String,
    val subtitleBn: String,
    val face: FaceId,
) {
    JUST_MET(
        titleBn = "নতুন পরিচয়",
        titleEn = "Just Met",
        subtitleBn = "কিবোর্ডে টাইপ শুরু করলে EMO আপনার শব্দ ও ইমোজি পছন্দ শেখা শুরু করবে।",
        face = FaceId.CURIOUS,
    ),
    WARMING_UP(
        titleBn = "শুরু করছে",
        titleEn = "Warming Up",
        subtitleBn = "EMO আপনার প্রাথমিক শব্দভাণ্ডার ও টাইপিং গতি চিনতে শুরু করেছে।",
        face = FaceId.CURIOUS,
    ),
    ADAPTING(
        titleBn = "বুঝতে শিখছে",
        titleEn = "Adapting",
        subtitleBn = "আপনার বাংলা-ইংরেজি অভ্যাস ও পছন্দের ইমোজিগুলো EMO ধরতে পারছে।",
        face = FaceId.SMILE,
    ),
    IN_SYNC(
        titleBn = "ভালো চেনে",
        titleEn = "In Sync",
        subtitleBn = "আপনার লেখার ধরন, পরের শব্দ ও ইমোজি মুডের সাথে EMO দারুণভাবে মিশে গেছে।",
        face = FaceId.BEAMING,
    ),
    MIND_READER(
        titleBn = "সম্পূর্ণ অভ্যস্ত",
        titleEn = "Mind Reader",
        subtitleBn = "EMO এখন আপনার নিজস্ব টাইপিং প্যাটার্ন ও ইমোজি স্টাইল নিখুঁতভাবে বোঝে!",
        face = FaceId.JOY,
    ),
}

data class EmojiUsageItem(
    val emoji: String,
    val count: Int,
)

data class EmoLearningSnapshot(
    val enabled: Boolean = true,
    val overallProgress: Float = 0f,
    val typingPatternProgress: Float = 0f,
    val emojiPreferenceProgress: Float = 0f,
    val stage: LearningStage = LearningStage.JUST_MET,
    val learnedBnWords: Int = 0,
    val learnedEnWords: Int = 0,
    val bigramCount: Int = 0,
    val banglaCommits: Int = 0,
    val englishCommits: Int = 0,
    val suggestionAccepts: Int = 0,
    val backspaceCount: Int = 0,
    val totalEmojiUses: Int = 0,
    val uniqueEmojisCount: Int = 0,
    val skinTonesRemembered: Int = 0,
    val moodKeywordMatches: Int = 0,
    val topEmojis: List<EmojiUsageItem> = emptyList(),
    val languageStyleLabel: String = "এখনো পর্যাপ্ত ডেটা নেই",
    val emojiVibeLabel: String = "এখনো ইমোজি শেখা হয়নি",
    val lastUpdatedMs: Long = 0L,
) {
    val overallPercent: Int get() = (overallProgress * 100f).roundToInt().coerceIn(0, 100)
    val typingPatternPercent: Int get() = (typingPatternProgress * 100f).roundToInt().coerceIn(0, 100)
    val emojiPreferencePercent: Int get() = (emojiPreferenceProgress * 100f).roundToInt().coerceIn(0, 100)
    val totalLearnedWords: Int get() = learnedBnWords + learnedEnWords
    val totalWordCommits: Int get() = banglaCommits + englishCommits
}

/**
 * ব্যবহারকারীর আসল টাইপিং প্যাটার্ন (শব্দ, পরের শব্দের bigram, ভাষা পছন্দ, suggestion গ্রহণ)
 * এবং ইমোজি ব্যবহারের পছন্দ ট্র্যাক করে EMO-র "Learning Mode" প্রগ্রেস হিসাব করে।
 */
object EmoLearningStore {

    private const val PREFS_NAME = "bk_emo_learning"
    private const val KEY_ENABLED = "learning_enabled"
    private const val KEY_BN_COMMITS = "bn_commits"
    private const val KEY_EN_COMMITS = "en_commits"
    private const val KEY_SUGGEST_ACCEPTS = "suggest_accepts"
    private const val KEY_BACKSPACES = "backspaces"
    private const val KEY_EMOJI_TOTAL = "emoji_total"
    private const val KEY_EMOJI_MAP = "emoji_map"
    private const val KEY_MOOD_MAP = "mood_map"
    private const val KEY_LAST_UPDATED = "last_updated"

    private const val TARGET_LEARNED_WORDS = 60f
    private const val TARGET_BIGRAMS = 40f
    private const val TARGET_WORD_INTERACTIONS = 80f
    private const val TARGET_EMOJI_USES = 40f
    private const val TARGET_UNIQUE_EMOJIS = 15f
    private const val TARGET_MOOD_SIGNALS = 10f

    private val lock = Any()
    private val _state = MutableStateFlow(EmoLearningSnapshot())
    val state: StateFlow<EmoLearningSnapshot> = _state.asStateFlow()

    private val moodTracker = TypingMoodTracker()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, enabled: Boolean): EmoLearningSnapshot {
        synchronized(lock) {
            prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
            return refreshLocked(ctx)
        }
    }

    fun snapshot(ctx: Context): EmoLearningSnapshot {
        synchronized(lock) {
            return refreshLocked(ctx)
        }
    }

    fun recordWordCommitted(
        ctx: Context,
        rawWord: String,
        isBangla: Boolean,
        viaSuggestion: Boolean,
    ): EmoLearningSnapshot {
        val word = rawWord.trim()
        if (word.isEmpty()) return snapshot(ctx)
        synchronized(lock) {
            val p = prefs(ctx)
            if (!p.getBoolean(KEY_ENABLED, true)) return refreshLocked(ctx)

            val extractedEmojis = extractEmojis(word)
            val editor = p.edit()
            if (extractedEmojis.isNotEmpty()) {
                val map = loadCountMap(p.getString(KEY_EMOJI_MAP, null))
                for (em in extractedEmojis) {
                    map[em] = (map[em] ?: 0) + 1
                }
                editor.putString(KEY_EMOJI_MAP, encodeCountMap(map))
                editor.putInt(KEY_EMOJI_TOTAL, p.getInt(KEY_EMOJI_TOTAL, 0) + extractedEmojis.size)
            }

            if (containsLettersOrDigits(word)) {
                if (isBangla) {
                    editor.putInt(KEY_BN_COMMITS, p.getInt(KEY_BN_COMMITS, 0) + 1)
                } else {
                    editor.putInt(KEY_EN_COMMITS, p.getInt(KEY_EN_COMMITS, 0) + 1)
                }
                if (viaSuggestion) {
                    editor.putInt(KEY_SUGGEST_ACCEPTS, p.getInt(KEY_SUGGEST_ACCEPTS, 0) + 1)
                }
            }

            moodTracker.onWordCommitted(word)?.let { ev ->
                val moods = loadCountMap(p.getString(KEY_MOOD_MAP, null))
                val key = moodCategoryKey(ev)
                moods[key] = (moods[key] ?: 0) + 1
                editor.putString(KEY_MOOD_MAP, encodeCountMap(moods))
            }

            editor.putLong(KEY_LAST_UPDATED, System.currentTimeMillis()).apply()
            return refreshLocked(ctx)
        }
    }

    fun recordEmojiUsed(ctx: Context, rawEmoji: String): EmoLearningSnapshot {
        val emojis = extractEmojis(rawEmoji).ifEmpty {
            val trimmed = rawEmoji.trim()
            if (trimmed.isNotEmpty()) listOf(trimmed) else emptyList()
        }
        if (emojis.isEmpty()) return snapshot(ctx)
        synchronized(lock) {
            val p = prefs(ctx)
            if (!p.getBoolean(KEY_ENABLED, true)) return refreshLocked(ctx)

            val map = loadCountMap(p.getString(KEY_EMOJI_MAP, null))
            for (em in emojis) {
                map[em] = (map[em] ?: 0) + 1
            }
            val moods = loadCountMap(p.getString(KEY_MOOD_MAP, null))
            for (em in emojis) {
                moodTracker.onWordCommitted(em)?.let { ev ->
                    val key = moodCategoryKey(ev)
                    moods[key] = (moods[key] ?: 0) + 1
                }
            }

            p.edit()
                .putString(KEY_EMOJI_MAP, encodeCountMap(map))
                .putInt(KEY_EMOJI_TOTAL, p.getInt(KEY_EMOJI_TOTAL, 0) + emojis.size)
                .putString(KEY_MOOD_MAP, encodeCountMap(moods))
                .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
                .apply()
            return refreshLocked(ctx)
        }
    }

    fun recordBackspace(ctx: Context) {
        synchronized(lock) {
            val p = prefs(ctx)
            if (!p.getBoolean(KEY_ENABLED, true)) return
            p.edit().putInt(KEY_BACKSPACES, p.getInt(KEY_BACKSPACES, 0) + 1).apply()
            refreshLocked(ctx)
        }
    }

    /**
     * Learning Mode স্ক্রিনের লাইভ স্যান্ডবক্স থেকে ব্যবহারকারী যা টাইপ করবেন তা আসল শব্দ ও ইমোজি
     * হিসেবে শিখে EMO-র ফেস ও প্রগ্রেস বার সাথে সাথে আপডেট করে।
     */
    fun recordSandboxInput(ctx: Context, rawInput: String): EmoLearningSnapshot {
        val text = rawInput.trim()
        if (text.isEmpty()) return snapshot(ctx)

        val suggestions = Suggestions(ctx)
        val emojiRepo = EmojiRepository(ctx)
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }

        var lastMoodEvent: RobotEvent? = null
        for (token in tokens) {
            val emojis = extractEmojis(token)
            for (em in emojis) {
                emojiRepo.recordRecent(em)
                recordEmojiUsed(ctx, em)
                moodTracker.onWordCommitted(em)?.let { lastMoodEvent = it }
            }
            val cleanedWord = stripEmojis(token).trim('.', '।', '!', '?', ',', ';', ':')
            if (cleanedWord.isNotEmpty() && containsLettersOrDigits(cleanedWord)) {
                val isBn = Suggestions.isBanglaWord(cleanedWord)
                suggestions.learn("", cleanedWord, isBn)
                recordWordCommitted(ctx, cleanedWord, isBangla = isBn, viaSuggestion = false)
                moodTracker.onWordCommitted(cleanedWord)?.let { lastMoodEvent = it }
            }
        }

        val moodEv = lastMoodEvent
        if (moodEv != null) {
            EmoBrainHub.handleEvent(moodEv)
        } else {
            val updated = snapshot(ctx)
            EmoBrainHub.express(
                face = updated.stage.face,
                speech = "শিখে নিলাম! (${updated.overallPercent}%)",
                bondDelta = 2,
                funDelta = 2,
                forceSpeech = true,
            )
        }
        return snapshot(ctx)
    }

    fun reset(ctx: Context, clearVocabularyAndRecents: Boolean = true): EmoLearningSnapshot {
        synchronized(lock) {
            val enabled = prefs(ctx).getBoolean(KEY_ENABLED, true)
            prefs(ctx).edit()
                .clear()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
            ctx.applicationContext.getSharedPreferences("bk_suggest", Context.MODE_PRIVATE).edit().clear().apply()
            ctx.applicationContext.getSharedPreferences("bk_emoji", Context.MODE_PRIVATE).edit().clear().apply()
            if (clearVocabularyAndRecents) {
                Suggestions(ctx).clearLearned()
                EmojiRepository(ctx).clearRecents()
            }
            return refreshLocked(ctx)
        }
    }

    private fun refreshLocked(ctx: Context): EmoLearningSnapshot {
        val p = prefs(ctx)
        val enabled = p.getBoolean(KEY_ENABLED, true)
        val suggestStats = Suggestions(ctx).stats()
        val emojiRepo = EmojiRepository(ctx)

        val bnCommits = p.getInt(KEY_BN_COMMITS, 0)
        val enCommits = p.getInt(KEY_EN_COMMITS, 0)
        val suggestAccepts = p.getInt(KEY_SUGGEST_ACCEPTS, 0)
        val backspaces = p.getInt(KEY_BACKSPACES, 0)
        val lastUpdated = p.getLong(KEY_LAST_UPDATED, 0L)

        val emojiMap = loadCountMap(p.getString(KEY_EMOJI_MAP, null))
        // Fallback: যদি আগে থেকেই EmojiRepository-তে recent থাকে কিন্তু learning map খালি থাকে
        if (emojiMap.isEmpty()) {
            for (recent in emojiRepo.recents()) {
                emojiMap[recent] = 1
            }
        }
        val totalEmojiUses = maxOf(p.getInt(KEY_EMOJI_TOTAL, 0), emojiMap.values.sum())
        val uniqueEmojis = emojiMap.size
        val skinTones = emojiRepo.skinTonePreferenceCount()

        val moodMap = loadCountMap(p.getString(KEY_MOOD_MAP, null))
        val moodTotal = moodMap.values.sum()

        val wordBreadth = (suggestStats.totalWords / TARGET_LEARNED_WORDS).coerceIn(0f, 1f)
        val bigramBreadth = (suggestStats.bigramCount / TARGET_BIGRAMS).coerceIn(0f, 1f)
        val interactionDepth = ((bnCommits + enCommits + suggestAccepts * 2 + suggestStats.totalFrequency) / TARGET_WORD_INTERACTIONS)
            .coerceIn(0f, 1f)
        val typingProgress = (wordBreadth * 0.40f + bigramBreadth * 0.30f + interactionDepth * 0.30f)
            .coerceIn(0f, 1f)

        val emojiFreqScore = (totalEmojiUses / TARGET_EMOJI_USES).coerceIn(0f, 1f)
        val emojiVarietyScore = (uniqueEmojis / TARGET_UNIQUE_EMOJIS).coerceIn(0f, 1f)
        val moodAffinityScore = ((moodTotal + skinTones * 2) / TARGET_MOOD_SIGNALS).coerceIn(0f, 1f)
        val emojiProgress = (emojiFreqScore * 0.50f + emojiVarietyScore * 0.35f + moodAffinityScore * 0.15f)
            .coerceIn(0f, 1f)

        val overall = (typingProgress * 0.58f + emojiProgress * 0.42f).coerceIn(0f, 1f)
        val overallPct = (overall * 100f).roundToInt().coerceIn(0, 100)

        val stage = when {
            overallPct <= 0 -> LearningStage.JUST_MET
            overallPct < 25 -> LearningStage.WARMING_UP
            overallPct < 55 -> LearningStage.ADAPTING
            overallPct < 85 -> LearningStage.IN_SYNC
            else -> LearningStage.MIND_READER
        }

        val topEmojis = emojiMap.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(6)
            .map { EmojiUsageItem(it.key, it.value) }

        val languageStyle = resolveLanguageStyle(
            bnWords = suggestStats.learnedBnWords + bnCommits,
            enWords = suggestStats.learnedEnWords + enCommits,
        )
        val emojiVibe = resolveEmojiVibe(moodMap, uniqueEmojis)

        val snap = EmoLearningSnapshot(
            enabled = enabled,
            overallProgress = overall,
            typingPatternProgress = typingProgress,
            emojiPreferenceProgress = emojiProgress,
            stage = stage,
            learnedBnWords = suggestStats.learnedBnWords,
            learnedEnWords = suggestStats.learnedEnWords,
            bigramCount = suggestStats.bigramCount,
            banglaCommits = bnCommits,
            englishCommits = enCommits,
            suggestionAccepts = suggestAccepts,
            backspaceCount = backspaces,
            totalEmojiUses = totalEmojiUses,
            uniqueEmojisCount = uniqueEmojis,
            skinTonesRemembered = skinTones,
            moodKeywordMatches = moodTotal,
            topEmojis = topEmojis,
            languageStyleLabel = languageStyle,
            emojiVibeLabel = emojiVibe,
            lastUpdatedMs = lastUpdated,
        )
        _state.value = snap
        return snap
    }

    private fun resolveLanguageStyle(bnWords: Int, enWords: Int): String {
        val total = bnWords + enWords
        if (total == 0) return "এখনো টাইপিং ডেটা নেই"
        val bnRatio = bnWords.toFloat() / total.toFloat()
        return when {
            bnRatio >= 0.72f -> "প্রধানত বাংলা ও অভ্র ফনেটিক"
            bnRatio <= 0.28f -> "প্রধানত English টাইপিং"
            else -> "বাংলা + English দ্বিভাষিক"
        }
    }

    private fun resolveEmojiVibe(moodMap: Map<String, Int>, uniqueEmojis: Int): String {
        val topMood = moodMap.maxByOrNull { it.value }?.key
        return when (topMood) {
            "love" -> "ভালোবাসা ও আন্তরিক"
            "laugh" -> "হাসিখুশি ও রসিক"
            "kiss" -> "আদুরে ও স্নেহময়"
            "cute" -> "মিষ্টি ও কিউট"
            "shock" -> "উচ্ছ্বসিত ও চমকপ্রদ"
            "cry" -> "সংবেদনশীল ও আবেগী"
            "anger" -> "তীব্র ও সরাসরি"
            else -> if (uniqueEmojis > 0) "বৈচিত্র্যময় ও প্রাণবন্ত" else "এখনো ইমোজি শেখা হয়নি"
        }
    }

    private fun moodCategoryKey(event: RobotEvent): String = when (event) {
        RobotEvent.KeywordLove -> "love"
        RobotEvent.KeywordLaugh -> "laugh"
        RobotEvent.KeywordKiss -> "kiss"
        RobotEvent.KeywordCute -> "cute"
        RobotEvent.KeywordShock -> "shock"
        RobotEvent.KeywordCry -> "cry"
        RobotEvent.KeywordAnger -> "anger"
        else -> "other"
    }

    private fun loadCountMap(raw: String?): MutableMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        if (raw.isNullOrBlank()) return out
        try {
            val obj = JSONObject(raw)
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                val v = obj.optInt(k, 0)
                if (k.isNotEmpty() && v > 0) out[k] = v
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun encodeCountMap(map: Map<String, Int>): String {
        val obj = JSONObject()
        for ((k, v) in map) {
            if (v > 0) obj.put(k, v)
        }
        return obj.toString()
    }

    private fun containsLettersOrDigits(s: String): Boolean =
        s.any { Character.isLetterOrDigit(it) }

    internal fun extractEmojis(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            val grapheme = text.substring(start, end)
            if (isLikelyEmojiGrapheme(grapheme)) {
                out.add(grapheme)
            }
            start = end
            end = it.next()
        }
        return out
    }

    private fun stripEmojis(text: String): String {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text)
        val sb = StringBuilder()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            val grapheme = text.substring(start, end)
            if (!isLikelyEmojiGrapheme(grapheme)) sb.append(grapheme)
            start = end
            end = it.next()
        }
        return sb.toString()
    }

    private fun isLikelyEmojiGrapheme(g: String): Boolean {
        if (g.isBlank()) return false
        var idx = 0
        while (idx < g.length) {
            val cp = g.codePointAt(idx)
            val charCount = Character.charCount(cp)
            val type = Character.getType(cp)
            if (cp in 0x1F300..0x1FAFF ||
                cp in 0x2600..0x27BF ||
                cp == 0x2764 ||
                cp == 0x2B50 ||
                cp == 0x200D ||
                type == Character.OTHER_SYMBOL.toInt()
            ) {
                return true
            }
            idx += charCount
        }
        return false
    }
}
