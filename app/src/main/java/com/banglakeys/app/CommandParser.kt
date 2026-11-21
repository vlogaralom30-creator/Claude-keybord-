package com.banglakeys.app

enum class Dir { UP, DOWN, LEFT, RIGHT }

enum class SettingsTarget { WIFI, BLUETOOTH, DISPLAY, SOUND }

sealed class Command {
    // Existing commands
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

    // Panic stop
    object PanicStop : Command()

    // Pack F — SAF Files & Folders
    data class CreateFolder(val name: String, val parentFolder: String? = null) : Command()
    data class CreateFile(val name: String, val ext: String = "txt", val content: String? = null) : Command()

    // Pack G — AI Chat / Current-app Type & Send
    data class AppChat(val appName: String?, val text: String, val send: Boolean) : Command()

    // Pack H — Messaging App Calls & Messages
    data class AppCall(val appName: String, val contact: String, val video: Boolean) : Command()
    data class AppMessage(val appName: String, val contact: String, val text: String, val send: Boolean) : Command()

    // Pack I — Everyday App Commands
    object OpenCamera : Command()
    object TakePhoto : Command()
    data class SetAlarm(val hour24: Int, val minute: Int, val label: String? = null) : Command()
    data class SetTimer(val seconds: Int, val label: String? = null) : Command()
    data class Navigate(val place: String) : Command()
    data class WebSearch(val query: String) : Command()
    data class PlaySong(val query: String) : Command()
    data class SaveNote(val text: String) : Command()
    data class WriteEmail(val recipient: String, val subject: String, val body: String) : Command()
    data class WriteSms(val recipient: String, val text: String) : Command()
    data class OpenSystemSettings(val target: SettingsTarget) : Command()
}

/**
 * বলা কথা (English / Banglish / বাংলা) -> Command।
 * নতুন কমান্ড যোগ করতে parse()-এর ভেতর সঠিক ক্রমে নিয়ম বসান এবং উপরের shared token table ব্যবহার করুন।
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

    // ---------------- Shared token tables (English / Banglish / Bangla) ----------------

    private val OPEN_WORDS = setOf(
        "open", "khol", "kholo", "khulo", "launch", "start",
        "ওপেন", "খোলো", "খুলো", "খোল", "চালু"
    )
    private val FILLERS = setOf(
        "kor", "koro", "kore", "dao", "den", "plz", "please", "the", "hey", "bot", "app", "on", "in", "to",
        "করো", "কর", "করুন", "দাও", "দেন", "অ্যাপ", "এ্যাপ", "প্লিজ", "ke", "কে", "for", "a", "an"
    )
    private val CALL_WORDS = setOf("call", "dial", "ring", "phone", "কল", "ফোন", "কলিং")
    private val VIDEO_WORDS = setOf("video", "ভিডিও")
    private val AUDIO_WORDS = setOf("audio", "voice", "অডিও", "ভয়েস", "ভয়েস")
    private val YT_WORDS = setOf(
        "youtube", "you", "tube", "yt", "ইউটিউব", "ইউটিউবে", "iutub"
    )
    private val SEARCH_WORDS = setOf(
        "search", "searching", "khuj", "khoj", "khojo", "khujo",
        "খুঁজ", "খুঁজো", "খোঁজ", "খোঁজো", "সার্চ"
    )
    private val YT_DROP = setOf(
        "e", "te", "er", "ar", "for", "play", "most", "viewed", "watched", "views", "view", "top",
        "highest", "video", "videos", "beshi", "বেশি", "ভিউ", "সবচেয়ে", "এ", "তে", "থেকে", "ভিডিও",
        "চালাও", "দেখা", "porjonto"
    ) + YT_WORDS + SEARCH_WORDS + OPEN_WORDS + FILLERS
    private val CLICK_WORDS = setOf("click", "tap", "press", "ক্লিক", "ট্যাপ", "চাপো")
    private val TYPE_WORDS = setOf("type", "write", "likho", "lekho", "likhe", "লেখো", "লিখো", "লিখে")
    private val SEND_WORDS = setOf("send", "pathao", "pathan", "পাঠাও", "পাঠান", "পাঠিয়ে")
    private val CONNECTOR_WORDS = setOf("and", "then", "tarpor", "আর", "এবং", "তারপর", "দিয়ে", "দিয়ে", "করে")

    // Pack F tokens
    private val CREATE_WORDS = setOf("create", "make", "new", "banao", "banaw", "toiri", "তৈরি", "বানাও", "নতুন")
    private val FOLDER_WORDS = setOf("folder", "directory", "ফোল্ডার")
    private val FILE_WORDS = setOf("file", "document", "ফাইল")
    private val INSIDE_WORDS = setOf("inside", "under", "within", "moddhe", "bhitore", "ভেতরে", "ভিতরে", "মধ্যে")
    private val DOT_WORDS = setOf("dot", "point", "ডট")
    private val EXT_ALIASES = mapOf(
        "txt" to "txt", "text" to "txt", "টিএক্সটি" to "txt", "টেক্সট" to "txt",
        "md" to "md", "markdown" to "md", "এমডি" to "md",
        "csv" to "csv", "সিএসভি" to "csv",
        "json" to "json", "জেসন" to "json"
    )

    // Pack I tokens
    private val CAMERA_WORDS = setOf("camera", "ক্যামেরা")
    private val PHOTO_WORDS = setOf("photo", "picture", "pic", "selfie", "ছবি", "ফটো", "সেলফি")
    private val TAKE_WORDS = setOf("take", "click", "capture", "snap", "tulo", "tolo", "তুলো", "তোলো", "তুলুন")
    private val ALARM_WORDS = setOf("alarm", "এলার্ম", "অ্যালার্ম")
    private val TIMER_WORDS = setOf("timer", "টাইমার")
    private val AM_WORDS = setOf("am", "a.m", "morning", "সকাল", "ভোর", "sokal", "bhor")
    private val PM_WORDS = setOf("pm", "p.m", "afternoon", "evening", "night", "দুপুর", "বিকাল", "বিকেল", "সন্ধ্যা", "রাত", "dupur", "bikal", "rat")
    private val SEC_WORDS = setOf("second", "seconds", "sec", "secs", "সেকেন্ড")
    private val MIN_WORDS = setOf("minute", "minutes", "min", "mins", "মিনিট")
    private val HOUR_WORDS = setOf("hour", "hours", "hr", "hrs", "ঘণ্টা", "ঘন্টা")
    private val SONG_WORDS = setOf("song", "music", "gaan", "gan", "গান", "মিউজিক")
    private val PLAY_WORDS = setOf("play", "chalao", "bajao", "shonao", "চালাও", "বাজাও", "শোনাও")
    private val NOTE_WORDS = setOf("note", "notes", "নোট")
    private val SAVE_NOTE_WORDS = setOf("save", "add", "likho", "lekho", "rakho", "সেভ", "যোগ", "লেখো", "লিখো", "রাখো")
    private val EMAIL_WORDS = setOf("email", "mail", "ইমেইল", "মেইল")
    private val SMS_WORDS = setOf("sms", "text", "message", "এসএমএস", "মেসেজ")
    private val SETTINGS_WORDS = setOf("settings", "setting", "সেটিংস", "সেটিং")
    private val GOOGLE_WORDS = setOf("google", "web", "গুগল", "গুগলে", "ওয়েব")
    private val NAV_PHRASES = listOf(
        "navigate to", "directions to", "route dekhao", "route to", "way to", "go to",
        "ম্যাপে দেখাও", "রাস্তা দেখাও", "নেভিগেট", "দিক দেখাও"
    )

    // Confirmation yes / no words (minimal T4 point 1)
    val CONFIRM_YES_WORDS = setOf("yes", "yeah", "yep", "ha", "haa", "han", "ji", "ok", "okay", "confirm", "হ্যাঁ", "হাঁ", "জি", "জ্বি", "ওকে", "ঠিক আছে")
    val CONFIRM_NO_WORDS = setOf("no", "nope", "na", "nah", "cancel", "stop", "don't", "dont", "না", "বাতিল", "থামো", "বন্ধ")

    private val NUMBER_WORDS = mapOf(
        "zero" to 0, "shunno" to 0, "শূন্য" to 0,
        "one" to 1, "ek" to 1, "এক" to 1,
        "two" to 2, "dui" to 2, "দুই" to 2,
        "three" to 3, "tin" to 3, "তিন" to 3,
        "four" to 4, "char" to 4, "চার" to 4,
        "five" to 5, "pach" to 5, "paanch" to 5, "পাঁচ" to 5,
        "six" to 6, "choy" to 6, "ছয়" to 6, "ছয়" to 6,
        "seven" to 7, "sat" to 7, "saat" to 7, "সাত" to 7,
        "eight" to 8, "at" to 8, "aat" to 8, "আট" to 8,
        "nine" to 9, "noy" to 9, "নয়" to 9, "নয়" to 9,
        "ten" to 10, "dosh" to 10, "দশ" to 10,
        "eleven" to 11, "egaro" to 11, "এগারো" to 11,
        "twelve" to 12, "baro" to 12, "বারো" to 12,
        "thirteen" to 13, "tero" to 13, "তেরো" to 13,
        "fourteen" to 14, "choddo" to 14, "চৌদ্দ" to 14,
        "fifteen" to 15, "ponero" to 15, "পনেরো" to 15,
        "sixteen" to 16, "sholo" to 16, "ষোলো" to 16,
        "seventeen" to 17, "shotero" to 17, "সতেরো" to 17,
        "eighteen" to 18, "atharo" to 18, "অঠারো" to 18, "আঠারো" to 18,
        "nineteen" to 19, "unish" to 19, "উনিশ" to 19,
        "twenty" to 20, "bish" to 20, "বিশ" to 20, "কুড়ি" to 20,
        "twentyfive" to 25, "pochish" to 25, "পঁচিশ" to 25,
        "thirty" to 30, "trish" to 30, "তিরিশ" to 30, "ত্রিশ" to 30,
        "forty" to 40, "chollish" to 40, "চল্লিশ" to 40,
        "fortyfive" to 45, "poytallish" to 45, "পঁয়তাল্লিশ" to 45,
        "fifty" to 50, "ponchash" to 50, "পঞ্চাশ" to 50,
        "sixty" to 60, "shat" to 60, "ষাট" to 60
    )

    fun toEnglishDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    '০' -> '0'
                    '১' -> '1'
                    '২' -> '2'
                    '৩' -> '3'
                    '৪' -> '4'
                    '৫' -> '5'
                    '৬' -> '6'
                    '৭' -> '7'
                    '৮' -> '8'
                    '৯' -> '9'
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    fun parseNumberToken(rawToken: String): Int? {
        val clean = toEnglishDigits(rawToken.trim().lowercase())
            .removeSuffix("টা")
            .removeSuffix("ta")
            .removeSuffix("টার")
            .removeSuffix("টার")
            .removeSuffix("baje")
            .removeSuffix("বাজে")
            .removeSuffix("মিনিট")
            .removeSuffix("সেকেন্ড")
            .trim()
        clean.toIntOrNull()?.let { return it }
        return NUMBER_WORDS[clean]
    }

    private fun stripEndSendWords(tokens: List<String>): Pair<List<String>, Boolean> {
        if (tokens.isEmpty()) return tokens to false
        var end = tokens.size
        var foundSend = false
        // Handle trailing "send koro" / "send করো"
        if (end >= 2 && tokens[end - 2] in SEND_WORDS && tokens[end - 1] in setOf("koro", "kor", "dao", "করো", "দাও", "দিন")) {
            end -= 2
            foundSend = true
        } else if (tokens[end - 1] in SEND_WORDS) {
            end -= 1
            foundSend = true
        }
        if (!foundSend) return tokens to false
        // Also drop connector word directly preceding the send phrase ("... and send", "... লিখে পাঠাও" keeps লিখে for type detection? No, only drop "and"/"then"/"tarpor"/"ও")
        if (end > 0 && tokens[end - 1] in setOf("and", "then", "tarpor", "এবং", "তারপর", "আর", "করে")) {
            end -= 1
        }
        return tokens.subList(0, end) to true
    }

    /**
     * বাংলা বিভক্তি ("ইমুতে", "ফাতেমাকে") সামলে AppFlow ও matched alias token index বের করে।
     */
    private fun findAppInTokens(tokens: List<String>, kindFilter: Kind? = null): Triple<AppFlow, Int, Int>? {
        val normalizedWithSuffixes = tokens.mapIndexed { idx, tok ->
            val base = tok
                .removeSuffix("তে")
                .removeSuffix("য়")
                .removeSuffix("ে")
            idx to listOf(tok, base).distinct()
        }
        val candidates = AppFlows.all
            .filter { kindFilter == null || it.kind == kindFilter }
            .flatMap { flow -> flow.names.map { alias -> flow to alias.lowercase().split(" ") } }
            .sortedByDescending { it.second.size * 100 + it.second.sumOf { w -> w.length } }

        for ((flow, aliasTokens) in candidates) {
            val len = aliasTokens.size
            for (start in 0..(tokens.size - len)) {
                var ok = true
                for (j in 0 until len) {
                    val forms = normalizedWithSuffixes[start + j].second
                    if (aliasTokens[j] !in forms) {
                        ok = false
                        break
                    }
                }
                if (ok) return Triple(flow, start, start + len)
            }
        }
        return null
    }

    private fun cleanContactToken(tok: String): String {
        val t = tok.trim()
        return when {
            t.endsWith("কে") && t.length > 3 -> t.dropLast(2)
            t.endsWith("ke") && t.length > 3 -> t.dropLast(2)
            else -> t
        }
    }

    fun parse(raw: String): Command? {
        val n = norm(raw)
        val tokens = n.trim().split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        fun has(vararg w: String) = w.any { n.contains(" $it ") }
        fun rest(drop: Set<String>) = tokens.filter { it !in drop }.joinToString(" ")

        // ---- 1. Panic stop / Quit / Sleep
        if (has("stop everything", "sob bondho", "shob bondho", "সব বন্ধ", "stop all", "cancel everything")) {
            return Command.PanicStop
        }
        if (has("quit", "exit", "কুইট") ||
            (has("assistant", "bot", "বট") && has("off", "stop", "bondho", "বন্ধ"))
        ) return Command.StopAssistant
        if (has("sleep", "ghumao", "ঘুমাও")) return Command.Sleep

        // ---- 2. Pack H (Messaging App Call & Message: imo, WhatsApp, Messenger, Telegram)
        val msgAppMatch = findAppInTokens(tokens, Kind.MESSENGER)
        if (msgAppMatch != null) {
            parsePackH(tokens, msgAppMatch)?.let { return it }
        }

        // ---- 3. Pack G (AppChat: open <app> type <text> [and send], বা current app-এ type <text> and send)
        parsePackG(tokens)?.let { return it }

        // ---- 4. Pack F (SAF Files & Folders)
        parsePackF(tokens)?.let { return it }

        // ---- 5. Pack I (Everyday App Commands)
        parsePackI(n, tokens)?.let { return it }

        // ---- 6. Existing rules (unchanged behaviour)
        // Call (plain phone call)
        if (tokens.any { it in CALL_WORDS }) {
            val who = rest(CALL_WORDS + FILLERS).split(" ").map { cleanContactToken(it) }.joinToString(" ").trim()
            if (who.isNotBlank()) return Command.Call(who)
        }

        // YouTube search (+ most viewed)
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

        // Open app
        if (tokens.any { it in OPEN_WORDS }) {
            val name = rest(OPEN_WORDS + FILLERS)
            if (name.isNotBlank()) return Command.OpenApp(name)
        }

        // Tap / click
        if (has("double tap", "double", "dubble", "dabl", "ডাবল")) return Command.DoubleTap
        if (tokens.any { it in CLICK_WORDS }) {
            val target = rest(CLICK_WORDS + FILLERS + setOf("button", "বাটন"))
            return if (target.isBlank()) Command.Tap else Command.ClickText(target)
        }

        // Type / enter (plain TypeText with NO app & NO trailing send)
        val ti = tokens.indexOfFirst { it in TYPE_WORDS }
        if (ti >= 0 && ti < tokens.size - 1) {
            return Command.TypeText(tokens.drop(ti + 1).joinToString(" "))
        }
        if (has("enter", "submit", "এন্টার")) return Command.Enter

        // Like / reaction
        if (has(
                "like", "love", "heart", "nice", "wow", "sundor", "sundar", "darun",
                "লাইক", "সুন্দর", "দারুণ", "বাহ"
            )
        ) return Command.Like

        // Pause / play
        if (has(
                "pause", "puse", "pose", "play", "resume", "stop",
                "থামো", "থামাও", "চালাও", "পজ", "প্লে", "রিজিউম"
            )
        ) return Command.PlayPause

        // System
        if (has("volume up", "louder", "ভলিউম বাড়াও", "আওয়াজ বাড়াও")) return Command.VolumeUp
        if (has("volume down", "quieter", "ভলিউম কমাও", "আওয়াজ কমাও")) return Command.VolumeDown
        if (has("back", "go back", "ব্যাক", "পেছনে", "পিছনে")) return Command.Back
        if (has("home", "হোম")) return Command.Home
        if (has("recent", "recents", "multitask", "রিসেন্ট")) return Command.Recents
        if (has("notification", "notifications", "নোটিফিকেশন")) return Command.Notifications
        if (has("quick settings")) return Command.QuickSettings
        if (has("screenshot", "স্ক্রিনশট")) return Command.Screenshot
        if (has("lock", "লক")) return Command.Lock

        // Swipe / scroll
        if (has("up", "next", "upore", "uporer", "ওপরে", "উপরে", "আপ", "নেক্সট", "পরের")) return Command.Swipe(Dir.UP)
        if (has("down", "previous", "prev", "niche", "নিচে", "ডাউন", "আগের")) return Command.Swipe(Dir.DOWN)
        if (has("left", "বামে", "বাম", "লেফট")) return Command.Swipe(Dir.LEFT)
        if (has("right", "ডানে", "ডান", "রাইট")) return Command.Swipe(Dir.RIGHT)

        return null
    }

    // ------------------------------------------------------------ Pack H parser

    private fun parsePackH(
        tokens: List<String>,
        appMatch: Triple<AppFlow, Int, Int>
    ): Command? {
        val (flow, appStart, appEnd) = appMatch
        val dropAppAndPost = setOf("e", "te", "এ", "তে")
        val withoutApp = tokens.filterIndexed { idx, _ -> idx < appStart || idx >= appEnd }
            .dropWhile { it in OPEN_WORDS || it in dropAppAndPost }

        // Case H1: Call ("open imo call fatema", "imo te fatema ke video call", "call fatema on imo video")
        val isCall = withoutApp.any { it in CALL_WORDS } ||
            (withoutApp.any { it in VIDEO_WORDS } && withoutApp.none { it in TYPE_WORDS || it in SEND_WORDS })
        if (isCall) {
            val video = withoutApp.any { it in VIDEO_WORDS } && !withoutApp.any { it in AUDIO_WORDS }
            val stripSet = CALL_WORDS + VIDEO_WORDS + AUDIO_WORDS + OPEN_WORDS + FILLERS + dropAppAndPost
            val contactTokens = withoutApp
                .filter { it !in stripSet }
                .map { cleanContactToken(it) }
                .filter { it.isNotBlank() }
            val contact = contactTokens.joinToString(" ").trim()
            if (contact.isNotBlank()) {
                return Command.AppCall(flow.key, contact, video)
            }
            return null
        }

        // Case H2: Message ("whatsapp e fatema ke hi pathao", "imo te ammi ke hello likho")
        val (beforeSend, hasSend) = stripEndSendWords(tokens)
        val hasType = beforeSend.any { it in TYPE_WORDS }
        val hasRecipientMarker = beforeSend.any { it == "ke" || it == "কে" || it == "to" || (it.endsWith("কে") && it.length > 3) }
        if (!hasSend && !hasType && !hasRecipientMarker) {
            return null // Plain "open imo" / "open whatsapp" falls through to OpenApp
        }

        // Clean tokens excluding app range and leading postpositions/open words
        val cleaned = ArrayList<String>()
        for (i in beforeSend.indices) {
            if (i in appStart until appEnd) continue
            val tok = beforeSend[i]
            if (cleaned.isEmpty() && (tok in OPEN_WORDS || tok in dropAppAndPost)) continue
            cleaned.add(tok)
        }
        if (cleaned.isEmpty()) return null

        // Split contact & text
        val typeIdx = cleaned.indexOfFirst { it in TYPE_WORDS }
        val keIdx = cleaned.indexOfFirst { it == "ke" || it == "কে" || it == "to" }
        val attachedKeIdx = cleaned.indexOfFirst { it.endsWith("কে") && it.length > 3 }

        var contact = ""
        var msg = ""

        when {
            keIdx >= 0 -> {
                val left = cleaned.subList(0, keIdx).filter { it !in OPEN_WORDS && it !in dropAppAndPost }
                contact = left.joinToString(" ") { cleanContactToken(it) }.trim()
                val right = cleaned.subList(keIdx + 1, cleaned.size).dropWhile { it in TYPE_WORDS }
                msg = stripTrailingTypeWord(right).joinToString(" ").trim()
            }
            attachedKeIdx >= 0 -> {
                val left = cleaned.subList(0, attachedKeIdx + 1).filter { it !in OPEN_WORDS && it !in dropAppAndPost }
                contact = left.joinToString(" ") { cleanContactToken(it) }.trim()
                val right = cleaned.subList(attachedKeIdx + 1, cleaned.size).dropWhile { it in TYPE_WORDS }
                msg = stripTrailingTypeWord(right).joinToString(" ").trim()
            }
            typeIdx > 0 -> {
                contact = cleaned.subList(0, typeIdx).filter { it !in OPEN_WORDS && it !in dropAppAndPost && it !in FILLERS }
                    .joinToString(" ") { cleanContactToken(it) }.trim()
                msg = cleaned.subList(typeIdx + 1, cleaned.size).joinToString(" ").trim()
            }
            cleaned.size >= 2 -> {
                contact = cleanContactToken(cleaned[0])
                msg = stripTrailingTypeWord(cleaned.subList(1, cleaned.size)).joinToString(" ").trim()
            }
        }

        if (contact.isNotBlank() && msg.isNotBlank()) {
            return Command.AppMessage(flow.key, contact, msg, hasSend)
        }
        return null
    }

    private fun stripTrailingTypeWord(tokens: List<String>): List<String> =
        if (tokens.isNotEmpty() && tokens.last() in TYPE_WORDS) tokens.dropLast(1) else tokens

    // ------------------------------------------------------------ Pack G parser

    private fun parsePackG(tokens: List<String>): Command.AppChat? {
        val (baseTokens, hasSend) = stripEndSendWords(tokens)
        if (baseTokens.isEmpty()) return null

        val appMatch = findAppInTokens(baseTokens)
        if (appMatch != null) {
            val (flow, appStart, appEnd) = appMatch
            val remaining = baseTokens.filterIndexed { i, _ -> i < appStart || i >= appEnd }
                .dropWhile { it in OPEN_WORDS || it in CONNECTOR_WORDS || it in setOf("e", "te", "এ", "তে") }
            if (remaining.isEmpty()) return null

            val typeIdx = remaining.indexOfFirst { it in TYPE_WORDS }
            val textTokens: List<String> = when {
                // "... type <TEXT>" (type comes before text)
                typeIdx >= 0 && typeIdx < remaining.size - 1 -> {
                    remaining.subList(typeIdx + 1, remaining.size)
                }
                // "<TEXT> likho" (Bangla/Banglish SOV order: type word at end)
                typeIdx == remaining.size - 1 && typeIdx > 0 -> {
                    remaining.subList(0, typeIdx)
                }
                // No type word, but explicit trailing send: "open claude hi send"
                hasSend -> remaining
                else -> return null
            }
            val text = textTokens.joinToString(" ").trim()
            if (text.isNotBlank()) {
                return Command.AppChat(flow.key, text, hasSend)
            }
            return null
        }

        // No app named: ONLY intercept if trailing send word is present AND a type word is present ("type hi and send")
        if (hasSend) {
            val ti = baseTokens.indexOfFirst { it in TYPE_WORDS }
            val textTokens = when {
                ti >= 0 && ti < baseTokens.size - 1 -> baseTokens.subList(ti + 1, baseTokens.size)
                ti == baseTokens.size - 1 && ti > 0 -> baseTokens.subList(0, ti)
                else -> emptyList()
            }
            val text = textTokens.joinToString(" ").trim()
            if (text.isNotBlank()) {
                return Command.AppChat(null, text, true)
            }
        }
        return null
    }

    // ------------------------------------------------------------ Pack F parser

    private fun parsePackF(tokens: List<String>): Command? {
        val hasCreate = tokens.any { it in CREATE_WORDS }
        val hasFolder = tokens.any { it in FOLDER_WORDS }
        val hasFile = tokens.any { it in FILE_WORDS }

        // Folder: "create folder photos", "folder banao photos", "create folder tour inside photos", "photos er moddhe folder banao tour"
        if (hasFolder && (hasCreate || tokens.any { it in INSIDE_WORDS })) {
            val insideIdx = tokens.indexOfFirst { it in INSIDE_WORDS }
            val folderIdx = tokens.indexOfFirst { it in FOLDER_WORDS }
            val dropF = CREATE_WORDS + FOLDER_WORDS + setOf("er", "ar", "এর", "a", "an", "the", "nicher", "notun")

            var parent: String? = null
            var name = ""

            if (insideIdx >= 0) {
                if (insideIdx < folderIdx) {
                    // "photos er moddhe folder banao tour"
                    val left = tokens.subList(0, insideIdx).filter { it !in dropF }
                    val right = tokens.subList(insideIdx + 1, tokens.size).filter { it !in dropF }
                    parent = left.joinToString(" ").trim().takeIf { it.isNotBlank() }
                    name = right.joinToString(" ").trim()
                } else {
                    // "create folder tour inside photos"
                    val left = tokens.subList(0, insideIdx).filter { it !in dropF }
                    val right = tokens.subList(insideIdx + 1, tokens.size).filter { it !in dropF }
                    name = left.joinToString(" ").trim()
                    parent = right.joinToString(" ").trim().takeIf { it.isNotBlank() }
                }
            } else {
                name = tokens.filter { it !in dropF }.joinToString(" ").trim()
            }
            if (name.isNotBlank()) {
                return Command.CreateFolder(name, parent)
            }
        }

        // File: "create file notes", "create file notes dot txt", "create file shopping with text milk eggs bread"
        if (hasFile && hasCreate) {
            val dropFile = CREATE_WORDS + FILE_WORDS + setOf("a", "an", "the", "ekta", "একটা")
            // Find "with text" / "with content" / "lekha"
            var splitIdx = -1
            var contentStart = -1
            for (i in tokens.indices) {
                if (i + 1 < tokens.size && tokens[i] == "with" && tokens[i + 1] in setOf("text", "content", "body")) {
                    splitIdx = i
                    contentStart = i + 2
                    break
                }
                if (i + 1 < tokens.size && tokens[i] in setOf("লেখা", "লিখা", "lekha") && tokens[i + 1] in setOf("সহ", "দিয়ে", "দিয়ে", "diye")) {
                    splitIdx = i
                    contentStart = i + 2
                    break
                }
                if (tokens[i] in setOf("with") && i + 1 < tokens.size && tokens[i + 1] !in dropFile && i > 1) {
                    splitIdx = i
                    contentStart = i + 1
                    break
                }
            }

            val namePart = if (splitIdx >= 0) tokens.subList(0, splitIdx) else tokens
            val content = if (contentStart >= 0 && contentStart <= tokens.size) {
                tokens.subList(contentStart, tokens.size).joinToString(" ").trim().takeIf { it.isNotEmpty() }
            } else null

            val cleanNameTokens = namePart.filter { it !in dropFile }.toMutableList()
            var ext = "txt"
            if (cleanNameTokens.size >= 3 && cleanNameTokens[cleanNameTokens.size - 2] in DOT_WORDS) {
                val spokenExt = cleanNameTokens.last()
                val mapped = EXT_ALIASES[spokenExt]
                if (mapped != null) {
                    ext = mapped
                    cleanNameTokens.removeAt(cleanNameTokens.size - 1)
                    cleanNameTokens.removeAt(cleanNameTokens.size - 1)
                }
            } else if (cleanNameTokens.size >= 2 && cleanNameTokens.last() in EXT_ALIASES) {
                ext = EXT_ALIASES.getValue(cleanNameTokens.last())
                cleanNameTokens.removeAt(cleanNameTokens.size - 1)
            }
            val fileName = cleanNameTokens.joinToString(" ").trim()
            if (fileName.isNotBlank()) {
                return Command.CreateFile(fileName, ext, content)
            }
        }
        return null
    }

    // ------------------------------------------------------------ Pack I parser

    private fun parsePackI(n: String, tokens: List<String>): Command? {
        fun has(vararg w: String) = w.any { n.contains(" $it ") }

        // 1. Open system settings (wifi / bluetooth / display / sound)
        if (tokens.any { it in SETTINGS_WORDS }) {
            when {
                has("wifi", "wi fi", "wi-fi", "ওয়াইফাই", "ওয়াই ফাই") -> return Command.OpenSystemSettings(SettingsTarget.WIFI)
                has("bluetooth", "ব্লুটুথ") -> return Command.OpenSystemSettings(SettingsTarget.BLUETOOTH)
                has("display", "screen", "brightness", "ডিসপ্লে", "স্ক্রিন") -> return Command.OpenSystemSettings(SettingsTarget.DISPLAY)
                has("sound", "volume", "audio", "সাউন্ড", "অডিও", "আওয়াজ") -> return Command.OpenSystemSettings(SettingsTarget.SOUND)
            }
        }

        // 2. Take photo / Open camera
        if (tokens.any { it in PHOTO_WORDS } && tokens.any { it in TAKE_WORDS }) {
            return Command.TakePhoto
        }
        if (tokens.any { it in CAMERA_WORDS } && (tokens.any { it in OPEN_WORDS } || tokens.size <= 2)) {
            return Command.OpenCamera
        }

        // 3. Set alarm
        if (tokens.any { it in ALARM_WORDS }) {
            parseAlarm(tokens)?.let { return it }
        }

        // 4. Set timer
        if (tokens.any { it in TIMER_WORDS }) {
            parseTimer(tokens)?.let { return it }
        }

        // 5. Navigate
        for (phrase in NAV_PHRASES) {
            val idx = n.indexOf(" $phrase ")
            if (idx >= 0) {
                val place = n.substring(idx + phrase.length + 2).trim()
                if (place.isNotBlank()) return Command.Navigate(place)
            }
        }
        if (has("navigate", "navigation", "directions")) {
            val place = tokens.filter { it !in setOf("navigate", "navigation", "directions", "to", "for", "on", "map", "maps", "open") }
                .joinToString(" ").trim()
            if (place.isNotBlank()) return Command.Navigate(place)
        }

        // 6. Google / Web search (keep youtube separate)
        if (tokens.any { it in GOOGLE_WORDS } && tokens.any { it in SEARCH_WORDS }) {
            val drop = GOOGLE_WORDS + SEARCH_WORDS + FILLERS + setOf("e", "te", "এ", "তে", "about")
            val q = tokens.filter { it !in drop }.joinToString(" ").trim()
            if (q.isNotBlank()) return Command.WebSearch(q)
        }

        // 7. Save note (keyboard Notes.kt)
        if (tokens.any { it in NOTE_WORDS } && tokens.any { it in SAVE_NOTE_WORDS }) {
            val idx = tokens.indexOfFirst { it in NOTE_WORDS || it in SAVE_NOTE_WORDS }
            // Find last anchor among save/note tokens so "save note buy milk" or "note likho buy milk" leaves "buy milk"
            var anchor = -1
            for (i in tokens.indices) {
                if (tokens[i] in NOTE_WORDS || tokens[i] in SAVE_NOTE_WORDS || tokens[i] in setOf("koro", "kor", "করো", "দাও", "dao")) {
                    anchor = i
                } else {
                    break
                }
            }
            val noteText = if (anchor >= 0 && anchor < tokens.size - 1) {
                tokens.subList(anchor + 1, tokens.size).joinToString(" ").trim()
            } else {
                tokens.filter { it !in NOTE_WORDS && it !in SAVE_NOTE_WORDS && it !in FILLERS }.joinToString(" ").trim()
            }
            if (noteText.isNotBlank()) return Command.SaveNote(noteText)
        }

        // 8. Write email (never auto-send)
        if (tokens.any { it in EMAIL_WORDS } && (tokens.any { it in TYPE_WORDS } || tokens.any { it in SEND_WORDS } || tokens.contains("to"))) {
            parseWriteEmail(tokens)?.let { return it }
        }

        // 9. Write SMS (never auto-send)
        if (tokens.any { it == "sms" || it == "এসএমএস" } &&
            (tokens.any { it in TYPE_WORDS } || tokens.any { it in SEND_WORDS } || tokens.contains("to") || tokens.size >= 3)
        ) {
            parseWriteSms(tokens)?.let { return it }
        }

        // 10. Play song (only if NOT youtube!)
        val isYt = has("youtube", "you tube", "yt", "ইউটিউব", "ইউটিউবে", "iutub")
        if (!isYt && tokens.any { it in SONG_WORDS } && tokens.any { it in PLAY_WORDS }) {
            val drop = SONG_WORDS + PLAY_WORDS + FILLERS + setOf("a", "the", "ekta", "একটা")
            val q = tokens.filter { it !in drop }.joinToString(" ").trim()
            if (q.isNotBlank()) return Command.PlaySong(q)
        }

        return null
    }

    private fun parseAlarm(tokens: List<String>): Command.SetAlarm? {
        val isAm = tokens.any { it in AM_WORDS }
        val isPm = tokens.any { it in PM_WORDS }
        val nums = ArrayList<Int>()
        for (tok in tokens) {
            val eng = toEnglishDigits(tok)
            if (eng.contains(':')) {
                val parts = eng.split(':')
                val h = parts.getOrNull(0)?.let { parseNumberToken(it) }
                val m = parts.getOrNull(1)?.let { parseNumberToken(it) }
                if (h != null) nums.add(h)
                if (m != null) nums.add(m)
                continue
            }
            parseNumberToken(tok)?.let { nums.add(it) }
        }
        if (nums.isEmpty()) return null
        var hour = nums[0]
        val minute = (nums.getOrNull(1) ?: 0).coerceIn(0, 59)
        if (hour !in 0..23) return null
        if (isPm && hour in 1..11) hour += 12
        else if (isAm && hour == 12) hour = 0
        return Command.SetAlarm(hour, minute)
    }

    private fun parseTimer(tokens: List<String>): Command.SetTimer? {
        var totalSeconds = 0
        var lastNum: Int? = null
        var foundUnit = false
        for (tok in tokens) {
            val num = parseNumberToken(tok)
            if (num != null) {
                lastNum = num
                continue
            }
            val n = lastNum
            if (n != null) {
                when {
                    tok in HOUR_WORDS -> {
                        totalSeconds += n * 3600
                        lastNum = null
                        foundUnit = true
                    }
                    tok in MIN_WORDS -> {
                        totalSeconds += n * 60
                        lastNum = null
                        foundUnit = true
                    }
                    tok in SEC_WORDS -> {
                        totalSeconds += n
                        lastNum = null
                        foundUnit = true
                    }
                }
            }
        }
        if (!foundUnit && lastNum != null && lastNum > 0) {
            // Default to minutes when user says "set timer 5" / "5 timer"
            totalSeconds = lastNum * 60
        }
        return if (totalSeconds > 0) Command.SetTimer(totalSeconds) else null
    }

    private fun parseWriteEmail(tokens: List<String>): Command.WriteEmail? {
        val drop = TYPE_WORDS + EMAIL_WORDS + SEND_WORDS + setOf("a", "an", "the", "to", "কে", "ke", "করো", "koro", "লেখো", "লিখো")
        val cleaned = tokens.dropWhile { it in drop }
        if (cleaned.isEmpty()) return null
        val subIdx = cleaned.indexOfFirst { it == "subject" || it == "বিষয়" || it == "সাবজেক্ট" }
        val bodyIdx = cleaned.indexOfFirst { it == "body" || it == "message" || it == "text" || it == "বডি" || it == "লেখা" }

        val recEnd = when {
            subIdx > 0 && bodyIdx > 0 -> minOf(subIdx, bodyIdx)
            subIdx > 0 -> subIdx
            bodyIdx > 0 -> bodyIdx
            else -> 1.coerceAtMost(cleaned.size)
        }
        val recipient = cleaned.subList(0, recEnd).map { cleanContactToken(it) }.joinToString(" ").trim()
        if (recipient.isBlank()) return null

        val subject = if (subIdx >= 0) {
            val end = if (bodyIdx > subIdx) bodyIdx else cleaned.size
            cleaned.subList(subIdx + 1, end).joinToString(" ").trim()
        } else ""

        val body = if (bodyIdx >= 0) {
            cleaned.subList(bodyIdx + 1, cleaned.size).joinToString(" ").trim()
        } else if (subIdx < 0 && cleaned.size > recEnd) {
            cleaned.subList(recEnd, cleaned.size).joinToString(" ").trim()
        } else ""

        return Command.WriteEmail(recipient, subject, body)
    }

    private fun parseWriteSms(tokens: List<String>): Command.WriteSms? {
        val drop = TYPE_WORDS + SMS_WORDS + SEND_WORDS + setOf("a", "an", "the", "to", "করো", "koro")
        val cleaned = tokens.dropWhile { it in drop }
        if (cleaned.isEmpty()) return null
        val textStart = cleaned.indexOfFirst { it == "text" || it == "message" || it == "saying" }
        val recipient: String
        val body: String
        if (textStart > 0) {
            recipient = cleaned.subList(0, textStart).map { cleanContactToken(it) }.joinToString(" ").trim()
            body = cleaned.subList(textStart + 1, cleaned.size).joinToString(" ").trim()
        } else {
            val firstKe = cleaned.indexOfFirst { it == "ke" || it == "কে" }
            if (firstKe > 0) {
                recipient = cleaned.subList(0, firstKe).map { cleanContactToken(it) }.joinToString(" ").trim()
                body = cleaned.subList(firstKe + 1, cleaned.size).joinToString(" ").trim()
            } else {
                recipient = cleanContactToken(cleaned[0])
                body = cleaned.drop(1).joinToString(" ").trim()
            }
        }
        if (recipient.isBlank()) return null
        return Command.WriteSms(recipient, body)
    }
}
