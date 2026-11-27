package com.banglakeys.app.robot

/**
 * টাইপিং গতি (fast/slow), পরপর backspace চাপা, এবং বাংলা/ইংরেজি/ইমোজি মুড কীওয়ার্ড দেখে
 * [RobotEvent] তৈরি করে যাতে EMO কী-বোর্ড বাস্তবসম্মত প্রতিক্রিয়া দেখায়।
 */
class TypingMoodTracker {

    private var burstKeys = 0
    private var burstStartMs = 0L
    private var lastFastEmitMs = 0L

    private var backspaceBurst = 0
    private var lastBackspaceMs = 0L
    private var lastKeyMs = 0L

    fun onKeyTap(isBackspace: Boolean, nowMs: Long): RobotEvent? {
        if (isBackspace) {
            backspaceBurst = if (nowMs - lastBackspaceMs <= BACKSPACE_WINDOW_MS) backspaceBurst + 1 else 1
            lastBackspaceMs = nowMs
            lastKeyMs = nowMs
            if (backspaceBurst >= BACKSPACE_BURST_COUNT) {
                backspaceBurst = 0
                return RobotEvent.BackspaceBurst
            }
            return null
        }

        backspaceBurst = 0
        val prevKey = lastKeyMs
        lastKeyMs = nowMs

        if (nowMs - burstStartMs > FAST_WINDOW_MS) {
            burstStartMs = nowMs
            burstKeys = 1
        } else {
            burstKeys++
        }

        if (burstKeys >= FAST_KEY_THRESHOLD && nowMs - lastFastEmitMs >= FAST_COOLDOWN_MS) {
            lastFastEmitMs = nowMs
            burstKeys = 0
            return RobotEvent.TypingFast
        }

        if (prevKey > 0L && nowMs - prevKey in SLOW_MIN_GAP_MS..SLOW_MAX_GAP_MS &&
            nowMs - lastFastEmitMs >= FAST_COOLDOWN_MS
        ) {
            lastFastEmitMs = nowMs
            return RobotEvent.TypingSlow
        }
        return null
    }

    fun onWordCommitted(rawWord: String): RobotEvent? {
        val w = rawWord.trim().trimEnd('.', '।', '!', '?', ',', ';', ':').lowercase()
        if (w.isEmpty()) return null
        return when {
            w in LAUGH_KEYWORDS -> RobotEvent.KeywordLaugh
            w in KISS_KEYWORDS -> RobotEvent.KeywordKiss
            w in SHOCK_KEYWORDS -> RobotEvent.KeywordShock
            w in CRY_KEYWORDS -> RobotEvent.KeywordCry
            w in CUTE_KEYWORDS -> RobotEvent.KeywordCute
            w in POSITIVE_KEYWORDS -> RobotEvent.KeywordLove
            w in ANGER_KEYWORDS -> RobotEvent.KeywordAnger
            else -> null
        }
    }

    fun reset() {
        burstKeys = 0
        burstStartMs = 0L
        lastFastEmitMs = 0L
        backspaceBurst = 0
        lastBackspaceMs = 0L
        lastKeyMs = 0L
    }

    companion object {
        private const val BACKSPACE_WINDOW_MS = 1_800L
        private const val BACKSPACE_BURST_COUNT = 4
        private const val FAST_WINDOW_MS = 1_800L
        private const val FAST_KEY_THRESHOLD = 8
        private const val FAST_COOLDOWN_MS = 6_000L
        private const val SLOW_MIN_GAP_MS = 2_800L
        private const val SLOW_MAX_GAP_MS = 7_500L

        private val LAUGH_KEYWORDS = setOf(
            "haha", "hahaha", "lol", "lmao", "rofl", "hehe", "hehehe",
            "হাহা", "হাহাহা", "হাসি", "খিলখিল",
            "😂", "🤣", "😆", "😄", "😁",
        )

        private val KISS_KEYWORDS = setOf(
            "kiss", "mwah", "muah", "chumu",
            "উম্মাহ", "চুমু",
            "😘", "😗", "😙", "😚",
        )

        private val SHOCK_KEYWORDS = setOf(
            "omg", "wtf", "whoa", "wow", "shocked", "what",
            "অবাক", "বাপরে", "হায়",
            "😳", "😱", "🤯", "😲",
        )

        private val CRY_KEYWORDS = setOf(
            "cry", "crying", "sad", "sob", "tears", "t_t", "uff",
            "কান্না", "কাঁদছি", "দুঃখ", "কষ্ট",
            "😭", "😢", "🥺", "💔",
        )

        private val CUTE_KEYWORDS = setOf(
            "cute", "uwu", "owo", "kawaii", "sweet", "pookie",
            "কিউট", "মিষ্টি", "আদর",
            "🥰", "🤭", "😋", "😏",
        )

        private val POSITIVE_KEYWORDS = setOf(
            "love", "lovely", "happy", "yay", "thanks", "thank",
            "awesome", "great", "miss", "heart",
            "ভালোবাসি", "ভালবাসি", "ভালো", "খুশি", "আনন্দ", "ধন্যবাদ", "সুন্দর", "মিস",
            "bhalobashi", "valobashi", "bhalo", "valo", "khushi", "dhonnobad", "sundor",
            "❤️", "😍", "😊", "💖", "💕",
        )

        private val ANGER_KEYWORDS = setOf(
            "angry", "hate", "mad", "annoyed", "ugh", "bad", "sorry",
            "রাগ", "বিরক্ত", "ধ্যাত", "ধুর", "খারাপ", "দুঃখিত", "বাজে",
            "raag", "rag", "birokto", "kharap", "baje",
            "😡", "🤬", "😠", "😤",
        )
    }
}
