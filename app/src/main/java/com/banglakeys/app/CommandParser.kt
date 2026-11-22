package com.banglakeys.app

enum class Dir { UP, DOWN, LEFT, RIGHT }

enum class SettingsTarget { WIFI, BLUETOOTH, DISPLAY, SOUND }

sealed class Command {
    data class OpenApp(val name: String) : Command()
    data class Call(val target: String) : Command()
    data class YoutubeSearch(val query: String, val mostViewed: Boolean) : Command()
    data class Swipe(val dir: Dir) : Command()
    data class ClickText(val text: String) : Command()
    data class TypeText(val text: String) : Command()
    data class VolumeUp(val steps: Int = 1) : Command()
    data class VolumeDown(val steps: Int = 1) : Command()
    object Mute : Command()
    object Unmute : Command()
    object CloseAllApps : Command()
    object CloseApp : Command()
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
    object Sleep : Command()
    object StopAssistant : Command()
    object PanicStop : Command()

    // Pack F — Storage / File & Folder creation (via SAF)
    data class CreateFolder(val name: String, val parentFolder: String? = null) : Command()
    data class CreateFile(val name: String, val ext: String = "txt", val text: String? = null) : Command()

    // Pack G — AI Chat / Current-app type & send
    data class AppChat(val appName: String?, val text: String, val send: Boolean) : Command()

    // Pack H — Messaging app call & message
    data class AppCall(val appName: String, val contact: String, val video: Boolean) : Command()
    data class AppMessage(
        val appName: String,
        val contact: String,
        val text: String,
        val send: Boolean = true
    ) : Command()

    // Pack I — Everyday mobile utilities
    object OpenCamera : Command()
    object TakePhoto : Command()
    data class SetAlarm(val hour24: Int, val minute: Int) : Command()
    data class SetTimer(val seconds: Int) : Command()
    data class Navigate(val place: String) : Command()
    data class WebSearch(val query: String) : Command()
    data class PlaySong(val query: String) : Command()
    data class SaveNote(val text: String) : Command()
    data class WriteEmail(
        val recipient: String,
        val subject: String = "",
        val body: String = ""
    ) : Command()
    data class WriteSms(val recipient: String, val text: String = "") : Command()
    data class OpenSystemSettings(val target: SettingsTarget) : Command()
}

/**
 * বলা কথা (English / Banglish / বাংলা) -> Command।
 *
 * মূল শব্দভান্ডার `BanglaVocab.kt` থেকে আসে, আর Pack F/G/H/I-এর সব উন্নত কমান্ডও সমর্থিত।
 */
object CommandParser {

    private val V = BanglaVocab

    val CONFIRM_YES_WORDS: Set<String> = setOf(
        "yes", "yeah", "yep", "ok", "okay", "confirm", "do it", "proceed", "go ahead", "send",
        "হ্যাঁ", "হাঁ", "জি", "জ্বি", "ঠিক আছে", "করো", "পাঠাও", "বলো"
    )

    val CONFIRM_NO_WORDS: Set<String> = setOf(
        "no", "nope", "cancel", "stop", "don't", "dont", "abort", "never mind", "nevermind",
        "না", "বাতিল", "থামো", "বন্ধ", "ক্যানসেল"
    )

    private fun norm(s: String): String =
        " " + s.lowercase()
            .replace(Regex("[,.!?।\"'“”‘’\\-_–—:;()/]"), " ")
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

    fun toEnglishDigits(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    '০' -> '0'; '১' -> '1'; '২' -> '2'; '৩' -> '3'; '৪' -> '4'
                    '৫' -> '5'; '৬' -> '6'; '৭' -> '7'; '৮' -> '8'; '৯' -> '9'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    private fun parseNumberToken(tok: String): Int? {
        val eng = toEnglishDigits(tok).trim()
        eng.toIntOrNull()?.let { return it }
        return when (eng.lowercase()) {
            "one", "ek", "এক" -> 1
            "two", "dui", "দুই" -> 2
            "three", "tin", "তিন" -> 3
            "four", "char", "চার" -> 4
            "five", "pach", "পাঁচ" -> 5
            "six", "choy", "ছয়", "ছয়" -> 6
            "seven", "sat", "সাত" -> 7
            "eight", "at", "আট" -> 8
            "nine", "noy", "নয়", "নয়" -> 9
            "ten", "dosh", "দশ" -> 10
            "eleven", "egaro", "এগারো" -> 11
            "twelve", "baro", "বারো" -> 12
            "fifteen", "ponero", "পনেরো" -> 15
            "twenty", "bish", "বিশ" -> 20
            "thirty", "trish", "তিরিশ", "ত্রিশ", "half", "sadhe", "সাড়ে", "সাড়ে" -> 30
            "forty", "chollish", "চল্লিশ" -> 40
            "fortyfive", "পঁয়তাল্লিশ", "পয়তাল্লিশ" -> 45
            "fifty", "ponchash", "পঞ্চাশ" -> 50
            else -> null
        }
    }

    private val OPEN_DROP: Set<String> = V.OPEN + V.FILLERS
    private val CALL_DROP: Set<String> = V.CALL + V.FILLERS
    private val CLICK_DROP: Set<String> = V.CLICK + V.FILLERS + setOf("button", "বাটন")

    /** YouTube query থেকে বাদ যাবে এমন সব শব্দ। */
    private val QUERY_DROP: Set<String> =
        V.QUERY_DROP + V.MEDIA + V.PLAY + V.YOUTUBE + V.SEARCH + V.OPEN + V.FILLERS

    /** "next video play koro" এর মতো কথাকে গান খোঁজা ভেবে ভুল না করতে। */
    private val CONTROL_WORDS: Set<String> =
        V.SWIPE_UP + V.SWIPE_DOWN + V.SWIPE_LEFT + V.SWIPE_RIGHT + V.PAUSE + V.LIKE + V.BACK + V.HOME

    private val THIS_APP_MARKERS: Set<String> = setOf(
        "ei", "eta", "eita", "this", "current", "এই", "এটা", "এইটা"
    )

    private val PANIC_PHRASES = setOf(
        "stop everything", "stop all", "cancel all", "abort everything", "emergency stop",
        "সব বন্ধ", "সব কিছু বন্ধ", "সব কাজ বন্ধ", "সব থামাও"
    )

    fun parse(raw: String): Command? {
        val n = norm(raw)
            .replace(" you tube ", " youtube ")
            .replace(" u tube ", " youtube ")
            .replace(" ইউ টিউব ", " ইউটিউব ")
        val tokens = n.trim().split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        fun has(vararg w: String) = w.any { n.contains(" $it ") }
        fun hasAny(w: Set<String>) = w.any { n.contains(" $it ") }
        fun rest(drop: Set<String>) = tokens.filter { it !in drop }.joinToString(" ")

        // ---- 0. Panic Stop (সব কাজ তাৎক্ষণিক বন্ধ — "সব অ্যাপ বন্ধ করো" থেকে আলাদা)
        if (!hasAny(V.APP_WORD) && PANIC_PHRASES.any { n.contains(" $it ") }) {
            return Command.PanicStop
        }

        // ---- 1. App থেকে বের হওয়া / Minimize
        if (hasAny(V.MINIMIZE)) return Command.Home
        if (hasAny(V.LEAVE_APP)) {
            return if (AssistantConfig.LEAVE_APP_GOES_HOME) Command.Home else Command.Back
        }

        // ---- 2. Assistant নিজে
        val allWord = hasAny(V.ALL)
        if (has("quit", "exit", "কুইট") ||
            (has("assistant", "bot", "বট") && hasAny(V.STOP_ASSISTANT) && !allWord)
        ) return Command.StopAssistant
        if (hasAny(V.SLEEP)) return Command.Sleep

        // ---- 3. Mute / Unmute
        if (hasAny(V.UNMUTE)) return Command.Unmute
        if (hasAny(V.MUTE)) return Command.Mute

        // ---- 4. App বন্ধ: "sob app close koro" -> CloseAllApps, "ei app bondho koro" -> CloseApp,
        //           আর সাধারণ "close app" / "অ্যাপ বন্ধ" -> Home (আগের regression অনুযায়ী)
        if (allWord && hasAny(V.CLOSE)) return Command.CloseAllApps
        if (hasAny(V.CLOSE) && hasAny(V.APP_WORD)) {
            return if (hasAny(THIS_APP_MARKERS)) Command.CloseApp else Command.Home
        }

        // ---- 5. Pack F: Folder & File creation (SAF)
        parsePackF(n, tokens)?.let { return it }

        // ---- 6. Pack H: Messenger App Call / Message (WhatsApp, imo, Messenger, Telegram)
        parsePackH(n, tokens)?.let { return it }

        // ---- 7. Pack G: AI Chat / Current app type & send
        parsePackG(n, tokens)?.let { return it }

        // ---- 8. Pack I: Everyday utilities (Alarm, Timer, Camera, Settings, Email, SMS, Note, Web, Nav, Song)
        parsePackI(n, tokens)?.let { return it }

        // ---- 9. Call: "call mim", "ammi ke call koro", "কল আম্মু"
        if (tokens.any { it in V.CALL }) {
            val who = rest(CALL_DROP)
            if (who.isNotBlank()) return Command.Call(who)
        }

        // ---- 10. YouTube search (+ most viewed): "youtube search koro adele hello"
        val yt = hasAny(V.YOUTUBE)
        val most = hasAny(V.MOST_VIEWED)
        if (yt && (tokens.any { it in V.SEARCH } || hasAny(V.MEDIA) || hasAny(V.PLAY))) {
            val q = rest(QUERY_DROP)
            return if (q.isNotBlank()) Command.YoutubeSearch(q, most) else Command.OpenApp("youtube")
        }

        // ---- 11. "youtube" না বললেও: "adele hello video play koro", "tum hi ho gaan chalao"
        if (!yt && hasAny(V.PLAY)) {
            val q = rest(QUERY_DROP)
            val qt = q.split(" ").filter { it.isNotEmpty() }
            val looksLikeControl = qt.isNotEmpty() && qt.all { it in CONTROL_WORDS }
            if (qt.isNotEmpty() && !looksLikeControl &&
                (hasAny(V.MEDIA) || hasAny(V.MEDIA_KEEP) || qt.size >= 2)
            ) return Command.YoutubeSearch(q, most)
        }

        // ---- 12. Open app: "tiktok khulo", "file e dhuko"
        if (tokens.any { it in V.OPEN }) {
            val name = rest(OPEN_DROP)
            if (name.isNotBlank()) return Command.OpenApp(name)
        }

        // ---- 13. Volume: "volume barao", "awaj komao", "volume full koro"
        if (hasAny(V.VOLUME_NOUN) || has("louder", "quieter")) {
            val up = hasAny(V.VOL_UP)
            val down = hasAny(V.VOL_DOWN)
            val steps = if (hasAny(V.VOL_BIG)) 5 else 1
            if (up && !down) return Command.VolumeUp(steps)
            if (down && !up) return Command.VolumeDown(steps)
        }

        // ---- 14. Tap / click
        if (hasAny(V.DOUBLE)) return Command.DoubleTap
        if (tokens.any { it in V.CLICK }) {
            val target = rest(CLICK_DROP)
            return if (target.isBlank()) Command.Tap else Command.ClickText(target)
        }

        // ---- 15. Type / enter
        val ti = tokens.indexOfFirst { it in V.TYPE }
        if (ti >= 0 && ti < tokens.size - 1) {
            return Command.TypeText(tokens.drop(ti + 1).joinToString(" "))
        }
        if (hasAny(V.ENTER)) return Command.Enter

        // ---- 16. Like / reaction (wow, nice, sundor ... সবই Like)
        if (hasAny(V.LIKE)) return Command.Like

        // ---- 17. Pause / play
        if (hasAny(V.PAUSE)) return Command.PlayPause

        // ---- 18. System
        if (hasAny(V.BACK)) return Command.Back
        if (hasAny(V.HOME)) return Command.Home
        if (hasAny(V.RECENTS)) return Command.Recents
        if (hasAny(V.NOTIFICATIONS)) return Command.Notifications
        if (hasAny(V.QUICK_SETTINGS)) return Command.QuickSettings
        if (hasAny(V.SCREENSHOT)) return Command.Screenshot
        if (hasAny(V.LOCK)) return Command.Lock

        // ---- 19. Swipe / scroll
        if (hasAny(V.SWIPE_UP)) return Command.Swipe(Dir.UP)
        if (hasAny(V.SWIPE_DOWN)) return Command.Swipe(Dir.DOWN)
        if (hasAny(V.SWIPE_LEFT)) return Command.Swipe(Dir.LEFT)
        if (hasAny(V.SWIPE_RIGHT)) return Command.Swipe(Dir.RIGHT)

        return null
    }

    // ---------------------------------------------------------------- Pack F (SAF Folder / File)

    private val CREATE_TRIGGERS = setOf(
        "create", "make", "new", "banao", "banaw", "tairi", "toiri", "তৈরি", "বানাও", "বানান"
    )
    private val FOLDER_WORDS = setOf("folder", "directory", "dir", "ফোল্ডার")
    private val FILE_WORDS = setOf("file", "document", "ফাইল")
    private val FILE_EXTENSIONS = setOf("txt", "csv", "md", "json", "log")
    private val PACK_F_DROP = CREATE_TRIGGERS + FOLDER_WORDS + FILE_WORDS +
        setOf("a", "an", "the", "named", "name", "called", "koro", "kor", "করো", "করুন", "নামে", "একটা", "একটি")

    private fun parsePackF(n: String, tokens: List<String>): Command? {
        val hasCreate = tokens.any { it in CREATE_TRIGGERS }
        if (!hasCreate) return null

        val isFolder = tokens.any { it in FOLDER_WORDS }
        val isFile = tokens.any { it in FILE_WORDS }
        if (!isFolder && !isFile) return null

        if (isFolder) {
            // Pattern A: "create folder <name> inside <parent>"
            val insideIdx = tokens.indexOfFirst { it == "inside" || it == "in" || it == "into" }
            if (insideIdx > 0 && insideIdx < tokens.size - 1) {
                val left = tokens.subList(0, insideIdx).filter { it !in PACK_F_DROP }.joinToString(" ").trim()
                val parent = tokens.subList(insideIdx + 1, tokens.size).filter { it !in PACK_F_DROP }.joinToString(" ").trim()
                if (left.isNotEmpty()) {
                    return Command.CreateFolder(left, parent.ifEmpty { null })
                }
            }
            // Pattern B: "<parent> er moddhe / er bhitore folder banao <name>"
            val moddheIdx = tokens.indexOfFirst { it == "moddhe" || it == "bhitore" || it == "ভিতরে" || it == "মধ্যে" }
            if (moddheIdx > 0 && moddheIdx < tokens.size - 1) {
                val parentPart = tokens.subList(0, moddheIdx)
                    .filter { it != "er" && it != "এর" && it !in PACK_F_DROP }
                    .joinToString(" ").trim()
                val namePart = tokens.subList(moddheIdx + 1, tokens.size)
                    .filter { it !in PACK_F_DROP }
                    .joinToString(" ").trim()
                if (namePart.isNotEmpty()) {
                    return Command.CreateFolder(namePart, parentPart.ifEmpty { null })
                }
            }
            val name = tokens.filter { it !in PACK_F_DROP }.joinToString(" ").trim()
            if (name.isNotEmpty()) return Command.CreateFolder(name, null)
        }

        if (isFile) {
            var contentText: String? = null
            var headTokens = tokens

            val textMarkers = listOf(" with text ", " with content ", " text ", " likhe ", " লিখে ")
            for (m in textMarkers) {
                val idx = n.indexOf(m)
                if (idx >= 0) {
                    val left = n.substring(0, idx).trim()
                    val right = n.substring(idx + m.length).trim()
                    if (right.isNotEmpty()) contentText = right
                    headTokens = left.split(" ").filter { it.isNotEmpty() }
                    break
                }
            }

            var cleaned = headTokens.filter { it !in PACK_F_DROP }.toMutableList()
            var ext = "txt"
            val dotIdx = cleaned.indexOfLast { it == "dot" || it == "ডট" }
            if (dotIdx >= 0 && dotIdx < cleaned.size - 1) {
                val candidate = cleaned[dotIdx + 1].lowercase()
                if (candidate in FILE_EXTENSIONS) {
                    ext = candidate
                    cleaned = cleaned.subList(0, dotIdx).toMutableList()
                }
            } else if (cleaned.size >= 2 && cleaned.last().lowercase() in FILE_EXTENSIONS) {
                ext = cleaned.last().lowercase()
                cleaned = cleaned.subList(0, cleaned.size - 1).toMutableList()
            }

            val name = cleaned.joinToString(" ").trim()
            if (name.isNotEmpty()) return Command.CreateFile(name, ext, contentText)
        }
        return null
    }

    // ---------------------------------------------------------------- Pack H (Messenger App Call & Message)

    private val SEND_TRIGGERS = setOf(
        "send", "pathao", "pathaw", "pathan", "patao", "পাঠাও", "পাঠান"
    )
    private val LIKHO_TRIGGERS = setOf(
        "type", "write", "likho", "lekho", "likhe", "লিখো", "লেখো", "লিখে"
    )
    private val VIDEO_WORDS = setOf("video", "ভিডিও")
    private val AUDIO_WORDS = setOf("audio", "voice", "অডিও", "ভয়েস", "ভয়েস")
    private val MESSAGE_NOUNS = setOf("message", "msg", "text", "মেসেজ", "বার্তা")

    private fun stripBanglaSuffix(tok: String, suffix: String): String {
        val t = tok.trim()
        return if (t.length > suffix.length + 1 && t.endsWith(suffix)) {
            t.substring(0, t.length - suffix.length)
        } else t
    }

    private fun normalizeKeParticles(tokens: List<String>): List<String> =
        tokens.map { stripBanglaSuffix(it, "কে") }

    private fun findMessengerInSentence(n: String, tokens: List<String>): Pair<AppFlow, List<String>>? {
        AppFlows.findInNormalized(n, Kind.MESSENGER)?.let { (flow, alias) ->
            val aliasToks = alias.split(" ").toSet()
            val remaining = tokens.filter { it !in aliasToks }
            return flow to remaining
        }
        // বাংলা বিভক্তি যেমন "ইমুতে" / "হোয়াটসঅ্যাপে"
        for (i in tokens.indices) {
            val tok = tokens[i]
            val candidates = listOf(
                stripBanglaSuffix(tok, "তে"),
                stripBanglaSuffix(tok, "এ"),
                stripBanglaSuffix(tok, "ে")
            )
            for (cand in candidates) {
                if (cand == tok) continue
                val flow = AppFlows.find(cand)
                if (flow != null && flow.kind == Kind.MESSENGER) {
                    val remaining = tokens.filterIndexed { idx, _ -> idx != i }
                    return flow to remaining
                }
            }
        }
        return null
    }

    private fun parsePackH(n: String, tokens: List<String>): Command? {
        val found = findMessengerInSentence(n, tokens) ?: return null
        val (flow, afterApp) = found

        val hasCall = afterApp.any { it in V.CALL }
        val hasVideo = afterApp.any { it in VIDEO_WORDS }
        val hasAudio = afterApp.any { it in AUDIO_WORDS }
        val hasSend = afterApp.any { it in SEND_TRIGGERS }
        val hasMsgWord = afterApp.any { it in MESSAGE_NOUNS }

        if (hasCall && !hasSend) {
            val drop = V.CALL + V.OPEN + V.FILLERS + VIDEO_WORDS + AUDIO_WORDS +
                setOf("koro", "kor", "kore", "dao", "ke", "te", "on", "in", "করো", "দাও")
            val normToks = normalizeKeParticles(afterApp)
            val contact = normToks.filter { it !in drop }.joinToString(" ").trim()
            if (contact.isNotEmpty()) {
                return Command.AppCall(flow.key, contact, video = hasVideo && !hasAudio)
            }
        }

        if (hasSend || hasMsgWord) {
            val normToks = normalizeKeParticles(afterApp)
            val dropNoise = V.OPEN + V.FILLERS + SEND_TRIGGERS + MESSAGE_NOUNS + LIKHO_TRIGGERS +
                setOf("on", "in", "to", "ke", "te", "e", "bole", "বলে", "করে")

            // Pattern: "<app> e <contact> ke <text> pathao"
            val keIdx = afterApp.indexOfFirst { it == "ke" || it.endsWith("কে") }
            if (keIdx >= 0) {
                val beforeKe = normToks.subList(0, keIdx + 1)
                    .filter { it !in dropNoise }
                    .joinToString(" ").trim()
                val afterKe = normToks.subList(keIdx + 1, normToks.size)
                    .filter { it !in SEND_TRIGGERS && it !in V.FILLERS && it !in LIKHO_TRIGGERS && it !in MESSAGE_NOUNS }
                    .joinToString(" ").trim()
                if (beforeKe.isNotEmpty() && afterKe.isNotEmpty()) {
                    return Command.AppMessage(flow.key, beforeKe, afterKe, send = hasSend)
                }
            }

            // Fallback English: "message <contact> on whatsapp saying <text>"
            val cleaned = normToks.filter { it !in dropNoise }
            if (cleaned.size >= 2) {
                val contact = cleaned.first()
                val msgText = cleaned.drop(1).joinToString(" ").trim()
                if (msgText.isNotEmpty()) {
                    return Command.AppMessage(flow.key, contact, msgText, send = hasSend)
                }
            }
        }
        return null
    }

    // ---------------------------------------------------------------- Pack G (AI Chat / Current app type + send)

    private fun parsePackG(n: String, tokens: List<String>): Command? {
        val aiMatch = AppFlows.findInNormalized(n, Kind.AI_CHAT)
        val hasLikho = tokens.any { it in LIKHO_TRIGGERS }
        val hasSend = tokens.any { it in SEND_TRIGGERS }

        if (aiMatch == null) {
            if (hasLikho && hasSend) {
                val startIdx = tokens.indexOfFirst { it in LIKHO_TRIGGERS }
                val after = tokens.drop(startIdx + 1).toMutableList()
                while (after.isNotEmpty() && (after.last() in SEND_TRIGGERS || after.last() in V.FILLERS || after.last() == "and")) {
                    after.removeAt(after.lastIndex)
                }
                val text = after.joinToString(" ").trim()
                if (text.isNotEmpty()) return Command.AppChat(null, text, send = true)
            }
            return null
        }

        val (flow, alias) = aiMatch
        if (!hasLikho && !hasSend) return null

        val aliasToks = alias.split(" ").toSet()
        val dropPrefix = aliasToks + V.OPEN + V.FILLERS
        val filtered = tokens.filter { it !in dropPrefix }

        val likhoIdx = filtered.indexOfFirst { it in LIKHO_TRIGGERS }
        fun trimTrailingSendFillers(list: MutableList<String>): String {
            while (list.isNotEmpty() &&
                (list.last() in SEND_TRIGGERS ||
                    list.last() in V.FILLERS ||
                    list.last() == "and" ||
                    list.last() == "এবং" ||
                    list.last() == "আর")
            ) {
                list.removeAt(list.lastIndex)
            }
            return list.joinToString(" ").trim()
        }

        val body = if (likhoIdx >= 0) {
            val afterBody = trimTrailingSendFillers(filtered.drop(likhoIdx + 1).toMutableList())
            if (afterBody.isNotEmpty()) {
                afterBody
            } else {
                // Banglish/Bangla word order: "<text> likho/likhe send koro"
                trimTrailingSendFillers(filtered.subList(0, likhoIdx).toMutableList())
            }
        } else {
            trimTrailingSendFillers(filtered.toMutableList())
        }
        if (body.isNotEmpty()) {
            return Command.AppChat(flow.key, body, send = hasSend)
        }
        return null
    }

    // ---------------------------------------------------------------- Pack I (Everyday mobile utilities)

    private fun parsePackI(n: String, tokens: List<String>): Command? {
        // 1) Camera & Take Photo
        if (tokens.any { it in setOf("photo", "picture", "selfie", "ছবি", "ফটো", "সেলফি") } &&
            tokens.any { it in setOf("take", "click", "capture", "snap", "tolo", "tulo", "তুলো", "তোলো", "ক্লিক") }
        ) {
            return Command.TakePhoto
        }
        if (tokens.any { it in setOf("camera", "ক্যামেরা") } &&
            (tokens.any { it in V.OPEN } || tokens.size <= 2)
        ) {
            return Command.OpenCamera
        }

        // 2) System Settings shortcuts (WiFi, Bluetooth, Display, Sound)
        if (tokens.any { it in setOf("settings", "setting", "সেটিংস", "সেটিং") }) {
            when {
                tokens.any { it in setOf("wifi", "wi", "ওয়াইফাই", "ওয়াইফাই") } ->
                    return Command.OpenSystemSettings(SettingsTarget.WIFI)
                tokens.any { it in setOf("bluetooth", "ব্লুটুথ") } ->
                    return Command.OpenSystemSettings(SettingsTarget.BLUETOOTH)
                tokens.any { it in setOf("display", "brightness", "screen", "ডিসপ্লে", "স্ক্রিন") } ->
                    return Command.OpenSystemSettings(SettingsTarget.DISPLAY)
                tokens.any { it in setOf("sound", "audio", "ringtone", "সাউন্ড") } ->
                    return Command.OpenSystemSettings(SettingsTarget.SOUND)
            }
        }

        // 3) Alarm: "set alarm 7 30 am", "7:30 am e alarm", "সকাল ৭টা ৩০ এ alarm"
        if (tokens.any { it in setOf("alarm", "অ্যালার্ম", "এলার্ম") }) {
            val engText = toEnglishDigits(n)
                .replace("টা", " ")
                .replace("মিনিট", " ")
                .replace(Regex("(\\d+)\\s*[:.]\\s*(\\d+)"), "$1 $2")
            val toks = engText.trim().split(Regex("\\s+"))
            val nums = toks.mapNotNull { parseNumberToken(it) }
            if (nums.isNotEmpty()) {
                var hour = nums[0].coerceIn(0, 23)
                val minute = if (nums.size >= 2) nums[1].coerceIn(0, 59) else 0
                val isPm = toks.any { it in setOf("pm", "p.m", "রাত", "বিকেল", "বিকাল", "দুপুর", "সন্ধ্যা") }
                val isAm = toks.any { it in setOf("am", "a.m", "সকাল", "ভোর") }
                if (isPm && hour in 1..11) hour += 12
                if (isAm && hour == 12) hour = 0
                return Command.SetAlarm(hour, minute)
            }
        }

        // 4) Timer: "set timer 5 minutes", "5 minute timer", "৩০ সেকেন্ড টাইমার"
        if (tokens.any { it in setOf("timer", "countdown", "টাইমার") }) {
            val engTokens = toEnglishDigits(n).trim().split(Regex("\\s+"))
            val qty = engTokens.firstNotNullOfOrNull { parseNumberToken(it) }
            if (qty != null && qty > 0) {
                val multiplier = when {
                    engTokens.any { it.startsWith("hour") || it in setOf("hr", "hrs", "ঘণ্টা", "ঘন্টা") } -> 3600
                    engTokens.any { it.startsWith("min") || it in setOf("মিনিট") } -> 60
                    else -> 1
                }
                return Command.SetTimer(qty * multiplier)
            }
        }

        // 5) Navigate (Maps): "navigate to Dhaka airport", "directions to Gulshan"
        val navPrefixes = listOf("navigate to ", "directions to ", "route to ", "take me to ", "go to ")
        for (p in navPrefixes) {
            val idx = n.indexOf(" $p")
            if (idx >= 0) {
                val place = n.substring(idx + p.length + 1).trim()
                if (place.isNotEmpty()) return Command.Navigate(place)
            }
        }
        if (tokens.any { it in setOf("rasta", "রাস্তা", "ম্যাপে") } && tokens.size >= 2) {
            val drop = setOf("rasta", "রাস্তা", "ম্যাপে", "dekhao", "দেখাও", "jabo", "যাবো", "er", "এর") + V.FILLERS
            val place = tokens.filter { it !in drop }.joinToString(" ").trim()
            if (place.isNotEmpty()) return Command.Navigate(place)
        }

        // 6) Web search (Google): "search google for best phones", "google e search koro ..."
        val hasGoogle = tokens.any { it in setOf("google", "গুগল", "গুগলে", "web") }
        val hasSearch = tokens.any { it in V.SEARCH }
        if (hasGoogle && hasSearch) {
            val drop = V.SEARCH + V.FILLERS + setOf("google", "গুগল", "গুগলে", "web", "for", "on", "in")
            val q = tokens.filter { it !in drop }.joinToString(" ").trim()
            if (q.isNotEmpty()) return Command.WebSearch(q)
        }

        // 7) Play song (explicit English "play song <q>" / "play music <q>")
        //    Note: Banglish "tum hi ho gaan chalao" / "adele hello video play koro"
        //    intentionally fall through to YoutubeSearch per SETUP-VOCAB-UPDATE!
        val playSongPrefixes = listOf("play song ", "play music ", "play track ")
        for (p in playSongPrefixes) {
            if (n.trimStart().startsWith(p)) {
                val q = n.trim().removePrefix(p.trim()).trim()
                if (q.isNotEmpty()) return Command.PlaySong(q)
            }
        }

        // 8) Save note: "save note buy milk", "note likho ..."
        val notePrefixes = listOf("save note ", "add note ", "take a note ", "take note ", "নোট সেভ ", "নোট লেখো ", "নোট লিখো ")
        for (p in notePrefixes) {
            val idx = n.indexOf(" $p")
            if (idx >= 0) {
                val text = n.substring(idx + p.length + 1).trim()
                if (text.isNotEmpty()) return Command.SaveNote(text)
            }
        }

        // 9) Write Email: "write email to fatema subject meeting body tomorrow at 10"
        if (tokens.any { it in setOf("email", "mail", "ইমেইল", "মেইল") } &&
            tokens.any { it in LIKHO_TRIGGERS + SEND_TRIGGERS + setOf("compose", "draft") }
        ) {
            val toIdx = tokens.indexOfFirst { it == "to" }
            val subjIdx = tokens.indexOfFirst { it == "subject" }
            val bodyIdx = tokens.indexOfFirst { it == "body" }
            if (toIdx >= 0 && toIdx < tokens.size - 1) {
                val recipEnd = listOf(subjIdx, bodyIdx).filter { it > toIdx }.minOrNull() ?: tokens.size
                val recipient = tokens.subList(toIdx + 1, recipEnd).joinToString(" ").trim()
                val subject = if (subjIdx > toIdx) {
                    val subjEnd = if (bodyIdx > subjIdx) bodyIdx else tokens.size
                    tokens.subList(subjIdx + 1, subjEnd).joinToString(" ").trim()
                } else ""
                val body = if (bodyIdx > toIdx && bodyIdx < tokens.size - 1) {
                    tokens.subList(bodyIdx + 1, tokens.size).joinToString(" ").trim()
                } else ""
                if (recipient.isNotEmpty()) return Command.WriteEmail(recipient, subject, body)
            }
        }

        // 10) Write SMS: "write sms to ammi ami ashchi"
        if (tokens.any { it in setOf("sms", "এসএমএস") } &&
            tokens.any { it in LIKHO_TRIGGERS + SEND_TRIGGERS + setOf("compose", "draft") }
        ) {
            val toIdx = tokens.indexOfFirst { it == "to" }
            if (toIdx >= 0 && toIdx < tokens.size - 2) {
                val recipient = tokens[toIdx + 1]
                val text = tokens.drop(toIdx + 2).joinToString(" ").trim()
                if (recipient.isNotEmpty()) return Command.WriteSms(recipient, text)
            }
        }

        return null
    }
}
