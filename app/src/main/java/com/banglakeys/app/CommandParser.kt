package com.banglakeys.app

enum class Dir { UP, DOWN, LEFT, RIGHT }

sealed class Command {
    data class OpenApp(val name: String) : Command()
    data class Call(val target: String) : Command()
    data class YoutubeSearch(val query: String, val mostViewed: Boolean) : Command()
    data class Swipe(val dir: Dir) : Command()
    data class ClickText(val text: String) : Command()
    data class TypeText(val text: String) : Command()
    object PlayPause : Command()
    object Like : Command()
    object DoubleTap : Command()
    object Tap : Command()
    object Enter : Command()
    object Back : Command()
    object Home : Command()
    object Recents : Command()
    object Notifications : Command()
    object QuickSettings : Command()
    object Lock : Command()
    object Screenshot : Command()
    object VolumeUp : Command()
    object VolumeDown : Command()
    object Sleep : Command()
    object StopAssistant : Command()
}

/**
 * বলা কথা (English / Banglish / বাংলা) -> Command।
 * নতুন কমান্ড যোগ করতে parse()-এর ভেতর একটা নতুন `if (has(...)) return ...` লাইন বসান।
 */
object CommandParser {

    private fun norm(s: String): String =
        " " + s.lowercase()
            .replace(Regex("[,.!?।\"'“”‘’]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim() + " "

    /** Wake word থাকলে (true, তার পরের অংশ), না থাকলে (false, পুরো লেখা)। */
    fun splitWake(raw: String): Pair<Boolean, String> {
        val n = norm(raw)
        for (w in AssistantConfig.WAKE_WORDS) {
            val i = n.indexOf(" $w ")
            if (i >= 0) return Pair(true, n.substring(i + w.length + 2).trim())
        }
        return Pair(false, raw)
    }

    private val OPEN_WORDS = setOf(
        "open", "khol", "kholo", "khulo", "launch", "start",
        "ওপেন", "খোলো", "খুলো", "খোল", "চালু"
    )
    private val FILLERS = setOf(
        "kor", "koro", "kore", "dao", "den", "plz", "please", "the", "hey", "bot", "app", "on", "in", "to",
        "করো", "কর", "করুন", "দাও", "দেন", "অ্যাপ", "এ্যাপ", "প্লিজ", "ke", "কে"
    )
    private val CALL_WORDS = setOf("call", "dial", "ring", "phone", "কল", "ফোন")
    private val YT_WORDS = setOf(
        "youtube", "you", "tube", "yt", "ইউটিউব", "ইউটিউবে", "iutub"
    )
    private val SEARCH_WORDS = setOf(
        "search", "searching", "khuj", "khoj", "খুঁজ", "খুঁজো", "খোঁজ", "সার্চ"
    )
    private val YT_DROP = setOf(
        "e", "te", "er", "ar", "for", "play", "most", "viewed", "watched", "views", "view", "top",
        "highest", "video", "videos", "beshi", "বেশি", "ভিউ", "সবচেয়ে", "এ", "তে", "থেকে", "ভিডিও",
        "চালাও", "দেখা", "porjonto"
    ) + YT_WORDS + SEARCH_WORDS + OPEN_WORDS + FILLERS
    private val CLICK_WORDS = setOf("click", "tap", "press", "ক্লিক", "ট্যাপ", "চাপো")
    private val TYPE_WORDS = setOf("type", "write", "likho", "lekho", "লেখো", "লিখো")

    fun parse(raw: String): Command? {
        val n = norm(raw)
        val tokens = n.trim().split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        fun has(vararg w: String) = w.any { n.contains(" $it ") }
        fun rest(drop: Set<String>) = tokens.filter { it !in drop }.joinToString(" ")

        // ---- assistant নিজে
        if (has("quit", "exit", "কুইট") ||
            (has("assistant", "bot", "বট") && has("off", "stop", "bondho", "বন্ধ"))
        ) return Command.StopAssistant
        if (has("sleep", "ghumao", "ঘুমাও")) return Command.Sleep

        // ---- Call: "call mim", "ammi ke call koro", "কল আম্মু"
        if (tokens.any { it in CALL_WORDS }) {
            val who = rest(CALL_WORDS + FILLERS)
            if (who.isNotBlank()) return Command.Call(who)
        }

        // ---- YouTube search (+ most viewed)
        val yt = has("youtube", "you tube", "yt", "ইউটিউব", "ইউটিউবে", "iutub")
        if (yt && (tokens.any { it in SEARCH_WORDS } || has("song", "gaan", "গান", "play", "চালাও"))) {
            val q = rest(YT_DROP)
            if (q.isNotBlank()) {
                val most = has(
                    "most viewed", "most watched", "most view", "top", "highest views",
                    "beshi view", "বেশি ভিউ", "সবচেয়ে বেশি"
                )
                return Command.YoutubeSearch(q, most)
            }
        }

        // ---- Open app
        if (tokens.any { it in OPEN_WORDS }) {
            val name = rest(OPEN_WORDS + FILLERS)
            if (name.isNotBlank()) return Command.OpenApp(name)
        }

        // ---- Tap / click
        if (has("double tap", "double", "dubble", "dabl", "ডাবল")) return Command.DoubleTap
        if (tokens.any { it in CLICK_WORDS }) {
            val target = rest(CLICK_WORDS + FILLERS + setOf("button", "বাটন"))
            return if (target.isBlank()) Command.Tap else Command.ClickText(target)
        }

        // ---- Type / enter
        val ti = tokens.indexOfFirst { it in TYPE_WORDS }
        if (ti >= 0 && ti < tokens.size - 1) {
            return Command.TypeText(tokens.drop(ti + 1).joinToString(" "))
        }
        if (has("enter", "submit", "এন্টার")) return Command.Enter

        // ---- Like / reaction (wow, nice, sundor ... সবই Like)
        if (has(
                "like", "love", "heart", "nice", "wow", "sundor", "sundar", "darun",
                "লাইক", "সুন্দর", "দারুণ", "বাহ"
            )
        ) return Command.Like

        // ---- Pause / play
        if (has(
                "pause", "puse", "pose", "play", "resume", "stop",
                "থামো", "থামাও", "চালাও", "পজ", "প্লে", "রিজিউম"
            )
        ) return Command.PlayPause

        // ---- System
        if (has("volume up", "louder", "ভলিউম বাড়াও", "আওয়াজ বাড়াও")) return Command.VolumeUp
        if (has("volume down", "quieter", "ভলিউম কমাও", "আওয়াজ কমাও")) return Command.VolumeDown
        if (has("back", "go back", "ব্যাক", "পেছনে", "পিছনে")) return Command.Back
        if (has("home", "হোম")) return Command.Home
        if (has("recent", "recents", "multitask", "রিসেন্ট")) return Command.Recents
        if (has("notification", "notifications", "নোটিফিকেশন")) return Command.Notifications
        if (has("quick settings")) return Command.QuickSettings
        if (has("screenshot", "স্ক্রিনশট")) return Command.Screenshot
        if (has("lock", "লক")) return Command.Lock

        // ---- Swipe / scroll
        if (has("up", "next", "upore", "uporer", "ওপরে", "উপরে", "আপ", "নেক্সট", "পরের")) return Command.Swipe(Dir.UP)
        if (has("down", "previous", "prev", "niche", "নিচে", "ডাউন", "আগের")) return Command.Swipe(Dir.DOWN)
        if (has("left", "বামে", "বাম", "লেফট")) return Command.Swipe(Dir.LEFT)
        if (has("right", "ডানে", "ডান", "রাইট")) return Command.Swipe(Dir.RIGHT)

        return null
    }
}
