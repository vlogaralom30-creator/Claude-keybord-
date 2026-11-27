package com.banglakeys.app

/**
 * N-best candidate scoring + Phonetic/Fuzzy Banglish-Bangla-English কমান্ড রিজলভার।
 * SpeechRecognizer প্রায়ই প্রথম ফলাফলে (index 0) সামান্য ভুল করে কিন্তু index 1..4 এ
 * সঠিক কথা রাখে, অথবা "emo"/"kholoo"/"pushe"/"cloud" মতো উচ্চারণ-ঘেঁষা ভুল করে।
 * এই ক্লাসটি সব candidate মিলিয়ে সবচেয়ে নির্ভুল Command বেছে নেয় এবং ইউজারকে দেখানোর
 * জন্য স্পষ্ট Command Summary তৈরি করে।
 */
object SmartCommandResolver {

    data class Resolution(
        val woke: Boolean,
        val heardText: String,
        val normalizedText: String,
        val command: Command?,
        val summary: String?,
        val fuzzyCorrected: Boolean
    )

    private val WAKE_PREFIX_ALIASES = setOf(
        "hey", "hay", "hi", "hello", "ok", "okay", "o", "a",
        "হেই", "হে", "হ্যালো", "হাই", "ওকে", "এই"
    )

    private val WAKE_EMO_ALIASES = setOf(
        "emo", "emu", "imo", "immo", "elmo", "amo", "emoh", "emoo", "emmu",
        "ইমো", "ইমু", "ইমূ", "এমো", "এমূ"
    )

    // Action ও App কীওয়ার্ডগুলোর Canonical ডিকশনারি (যাতে ভুল বানান/উচ্চারণ ঠিক হয়)
    private val FUZZY_KEYWORDS: Set<String> = setOf(
        "open", "khol", "kholo", "khulo", "dhuko", "chalu", "launch", "start",
        "call", "dial", "ring", "phone", "video", "audio", "voice", "speaker", "end",
        "youtube", "search", "khuj", "khoj", "khojo", "khujo", "gaan", "natok", "movie",
        "click", "tap", "press", "double",
        "type", "write", "likho", "lekho", "likhe", "send", "pathao", "pathan", "enter", "submit",
        "like", "love", "heart", "nice", "wow", "sundor", "darun",
        "comment", "subscribe", "follow", "refresh", "reload", "fullscreen", "tab",
        "pause", "play", "resume", "stop", "chalao", "dekhao", "thamo",
        "volume", "awaj", "shobdo", "barao", "komao", "full", "mute", "unmute", "chup",
        "back", "pechone", "home", "minimize", "ber", "recent", "recents", "notification", "notifications",
        "quick", "settings", "screenshot", "lock",
        "next", "previous", "prev", "porer", "ager", "up", "down", "left", "right",
        "upore", "niche", "uthao", "othao", "namao", "scroll",
        "ektu", "ekto", "arekto", "beshi", "onek", "olpo", "abar", "repeat",
        "first", "second", "third", "prothom",
        "sleep", "ghumao", "quit", "exit", "close", "bondho", "clear", "leave", "assistant",
        "create", "make", "folder", "file", "files", "inside", "camera", "gallery", "calculator", "clock",
        "photo", "picture", "selfie",
        "alarm", "timer", "minute", "minutes", "second", "seconds", "hour", "hours",
        "navigate", "directions", "google", "song", "music", "note", "save", "email", "sms",
        // Popular apps
        "tiktok", "facebook", "instagram", "insta", "chrome", "browser", "gmail", "maps",
        "claude", "chatgpt", "gemini", "imo", "whatsapp", "messenger", "telegram"
    )

    private val PHONETIC_DIRECT_MAP: Map<String, String> = mapOf(
        "tik tok" to "tiktok",
        "tick tock" to "tiktok",
        "tick tuck" to "tiktok",
        "you tube" to "youtube",
        "whats app" to "whatsapp",
        "what's app" to "whatsapp",
        "chat gpt" to "chatgpt",
        "play store" to "playstore",
        "chorom" to "chrome",
        "crome" to "chrome",
        "chrom" to "chrome",
        "krume" to "chrome",
        "face book" to "facebook",
        "kholoo" to "kholo",
        "khuloo" to "khulo",
        "khul" to "khulo",
        "puse" to "pause",
        "pose" to "pause",
        "dubble" to "double",
        "dabl" to "double",
        "emo" to "imo",
        "immo" to "imo",
        "cloud" to "claude",
        "clod" to "claude",
        "claud" to "claude",
        "jemini" to "gemini",
        "messanger" to "messenger",
        "watsap" to "whatsapp",
        "wasap" to "whatsapp",
        "pathaw" to "pathao",
        "patao" to "pathao",
        "liko" to "likho",
        "leko" to "lekho",
        "scrol" to "scroll",
        "ekto" to "ektu",
        "arektu" to "arekto",
        "othaw" to "othao",
        "uthaw" to "uthao",
        "namaw" to "namao",
        "shob" to "sob",
        "bondho" to "bondho",
        "bondo" to "bondho",
        "bara o" to "barao",
        "koma o" to "komao",
        "minimise" to "minimize",
        "minimaiz" to "minimize",
        "myut" to "mute",
        "dhukoo" to "dhuko",
        "chalaw" to "chalao",
        "dekhaw" to "dekhao",
        "sabscribe" to "subscribe",
        "subscrib" to "subscribe",
        "falo" to "follow",
        "folow" to "follow",
        "koment" to "comment",
        "tiktak" to "tiktok"
    )

    private val BANGLA_SPELLING_MAP: Map<String, String> = mapOf(
        "ইমু" to "imo",
        "ইমো" to "imo",
        "হোয়াটসঅ্যাপ" to "whatsapp",
        "হোয়াটস অ্যাপ" to "whatsapp",
        "মেসেঞ্জার" to "messenger",
        "ম্যাসেঞ্জার" to "messenger",
        "টেলিগ্রাম" to "telegram",
        "টিকটক" to "tiktok",
        "টিক টক" to "tiktok",
        "ক্রোম" to "chrome",
        "ফেসবুক" to "facebook",
        "ইউটিউব" to "youtube",
        "ক্লড" to "claude",
        "ক্লাউড" to "claude",
        "ক্লদ" to "claude",
        "জেমিনি" to "gemini",
        "জেমিনাই" to "gemini",
        "চ্যাটজিপিটি" to "chatgpt",
        "চ্যাট জিপিটি" to "chatgpt"
    )

    /**
     * Strict + Fuzzy wake word splitter.
     * যেমন: "Hey immo open tiktok", "হেই ইমো উপরে যাও" ও ধরতে পারে।
     */
    fun splitWakeSmart(raw: String): Pair<Boolean, String> {
        val strict = CommandParser.splitWake(raw)
        if (strict.first) return strict

        val cleaned = raw.lowercase()
            .replace(Regex("[,.!?।\"'“”‘’]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return false to raw

        val tokens = cleaned.split(" ").filter { it.isNotEmpty() }
        if (tokens.size >= 2) {
            val first = tokens[0]
            val second = tokens[1]
            val firstMatch = first in WAKE_PREFIX_ALIASES ||
                (first.length >= 2 && editDistance(first, "hey") <= 1)
            val secondMatch = second in WAKE_EMO_ALIASES ||
                (second.length in 3..4 &&
                    (second.startsWith("em") || second.endsWith("mo") || second.endsWith("mu")) &&
                    editDistance(second, "emo") <= 1)
            if (firstMatch && secondMatch) {
                return true to tokens.drop(2).joinToString(" ").trim()
            }
        }
        // Single merged token e.g. "heyemo", "heyimu"
        if (tokens.isNotEmpty() &&
            tokens[0] in setOf("heyemo", "hayemo", "hiemo", "okemo", "heyemu", "hayemu", "heyimo")
        ) {
            return true to tokens.drop(1).joinToString(" ").trim()
        }
        return false to raw
    }

    /**
     * সব recognition candidate (0..N-1) পরীক্ষা করে সর্বোচ্চ confidence-এর Command বেছে নেয়।
     */
    fun resolve(candidates: List<String>, alreadyAwake: Boolean): Resolution {
        if (candidates.isEmpty()) {
            return Resolution(false, "", "", null, null, false)
        }

        var bestScore = Int.MIN_VALUE
        var bestRes: Resolution? = null
        var firstAwakeHeard: String? = null
        var anyWokeOnly = false
        var wokeHeardText = ""

        for ((idx, raw) in candidates.withIndex()) {
            val (woke, rest) = splitWakeSmart(raw)
            val awake = woke || alreadyAwake
            if (!awake) continue

            if (woke && rest.isBlank()) {
                if (!anyWokeOnly) {
                    anyWokeOnly = true
                    wokeHeardText = raw.trim()
                }
                continue
            }

            if (firstAwakeHeard == null) firstAwakeHeard = rest

            // 1. Strict parse (highest confidence)
            val strictCmd = CommandParser.parse(rest)
            if (strictCmd != null) {
                val score = 1000 - idx * 25 + commandSpecificityBonus(strictCmd)
                if (score > bestScore) {
                    bestScore = score
                    bestRes = Resolution(
                        woke = woke,
                        heardText = raw.trim(),
                        normalizedText = rest,
                        command = strictCmd,
                        summary = describe(strictCmd),
                        fuzzyCorrected = false
                    )
                }
                // যদি strict match কোনো অচেনা app-name বা ambiguous catch-all হয়, fuzzy pass-ও চেক করি
                val shouldAlsoTryFuzzy = strictCmd is Command.OpenApp && !AppResolver.isKnownAppName(strictCmd.name)
                if (!shouldAlsoTryFuzzy) continue
            }

            // 2. Phonetic & fuzzy token normalization pass
            val normalized = normalizeFuzzyPhrase(rest)
            if (normalized != rest) {
                val fuzzyCmd = CommandParser.parse(normalized)
                if (fuzzyCmd != null) {
                    val score = 750 - idx * 25 + commandSpecificityBonus(fuzzyCmd)
                    if (score > bestScore) {
                        bestScore = score
                        bestRes = Resolution(
                            woke = woke,
                            heardText = raw.trim(),
                            normalizedText = normalized,
                            command = fuzzyCmd,
                            summary = describe(fuzzyCmd),
                            fuzzyCorrected = true
                        )
                    }
                }
            }
        }

        if (bestRes != null) return bestRes

        if (anyWokeOnly) {
            return Resolution(
                woke = true,
                heardText = wokeHeardText,
                normalizedText = "",
                command = null,
                summary = null,
                fuzzyCorrected = false
            )
        }

        val fallbackHeard = firstAwakeHeard ?: candidates.first().trim()
        return Resolution(
            woke = false,
            heardText = fallbackHeard,
            normalizedText = fallbackHeard,
            command = null,
            summary = null,
            fuzzyCorrected = false
        )
    }

    /**
     * সেশন মেমোরি (আগের কমান্ড, স্ক্রল দিক, চলতি অ্যাপ) সহ রিজলভ করে।
     * "aro beshi", "arekto", "abar koro", "eta send koro"-এর মতো follow-up গুলো ধরে ফেলে।
     */
    fun resolveWithMemory(
        candidates: List<String>,
        alreadyAwake: Boolean,
        memory: AssistantMemory,
        foregroundPackage: String = ""
    ): Resolution {
        val direct = resolve(candidates, alreadyAwake)
        if (direct.command != null || (!direct.woke && !alreadyAwake)) return direct

        for (raw in candidates) {
            val (woke, rest) = splitWakeSmart(raw)
            if (!woke && !alreadyAwake) continue
            if (rest.isBlank()) continue

            val followCmd = memory.resolveFollowUp(rest, foregroundPackage)
                ?: memory.resolveFollowUp(normalizeFuzzyPhrase(rest), foregroundPackage)
            if (followCmd != null) {
                return Resolution(
                    woke = woke,
                    heardText = raw.trim(),
                    normalizedText = rest,
                    command = followCmd,
                    summary = describe(followCmd),
                    fuzzyCorrected = true
                )
            }
        }
        return direct
    }

    /**
     * Multi-word ও single-word phonetic/fuzzy normalization।
     * "type <text>"-এর পরের ফ্রি-টেক্সট স্পর্শ করে না, শুধু কমান্ড অংশ ঠিক করে।
     */
    fun normalizeFuzzyPhrase(raw: String): String {
        val cleaned = raw.lowercase()
            .replace(Regex("[,.!?।\"'“”‘’]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return ""

        // যদি কমান্ড অংশে type/write/likho থাকে (এবং email/sms না হয়), এর পরের ফ্রি-টেক্সট স্পর্শ করব না
        val rawTokens = cleaned.split(" ").filter { it.isNotEmpty() }
        val typeTriggers = setOf("type", "write", "likho", "lekho", "লিখো", "লেখো")
        val typeIdx = rawTokens.indexOfFirst { it in typeTriggers }
        val isStructuredWrite = typeIdx >= 0 && typeIdx + 1 < rawTokens.size &&
            rawTokens[typeIdx + 1] in setOf("email", "mail", "sms", "text", "message", "note")

        val headTokensRaw: List<String>
        val tailPayload: String
        if (typeIdx >= 0 && !isStructuredWrite) {
            headTokensRaw = rawTokens.subList(0, typeIdx + 1)
            tailPayload = rawTokens.subList(typeIdx + 1, rawTokens.size).joinToString(" ")
        } else {
            headTokensRaw = rawTokens
            tailPayload = ""
        }

        var text = headTokensRaw.joinToString(" ")
        for ((k, v) in BANGLA_SPELLING_MAP) {
            if (text.contains(k)) {
                text = text.replace(k, v)
            }
        }
        for ((k, v) in PHONETIC_DIRECT_MAP) {
            text = Regex("(^|\\s)" + Regex.escape(k) + "(?=\\s|\$)").replace(text) { mr ->
                mr.groupValues[1] + v
            }
        }

        val tokens = text.split(" ").filter { it.isNotEmpty() }.toMutableList()
        for (i in tokens.indices) {
            val tok = tokens[i]
            val rewritten = bestFuzzyKeyword(tok)
            if (rewritten != null) {
                tokens[i] = rewritten
            }
        }
        val rebuiltHead = tokens.joinToString(" ")
        return if (tailPayload.isEmpty()) rebuiltHead else "$rebuiltHead $tailPayload".trim()
    }

    private fun bestFuzzyKeyword(tok: String): String? {
        if (tok.length < 3) return null
        if (tok in FUZZY_KEYWORDS) return tok

        // যদি বাংলা/নন-ASCII হয়, শুধু ASCII-এর উপর ভুল match এড়ানো
        val isAscii = tok.all { it in 'a'..'z' || it in '0'..'9' }
        if (!isAscii) return null

        val folded = phoneticFold(tok)
        val maxDist = if (tok.length <= 5) 1 else 2

        var bestWord: String? = null
        var bestDist = Int.MAX_VALUE

        for (kw in FUZZY_KEYWORDS) {
            // খুব ভিন্ন দৈর্ঘ্যের শব্দ বাদ
            if (kotlin.math.abs(kw.length - tok.length) > maxDist + 1) continue
            val dRaw = editDistance(tok, kw)
            val dFolded = editDistance(folded, phoneticFold(kw))
            val d = minOf(dRaw, dFolded)
            if (d <= maxDist && d < bestDist) {
                bestDist = d
                bestWord = kw
            }
        }
        return bestWord
    }

    private fun phoneticFold(s: String): String = s
        .replace("ph", "f")
        .replace("bh", "v")
        .replace("oo", "u")
        .replace("ee", "i")
        .replace("aa", "a")
        .replace(Regex("(.)\\1+"), "$1")

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + cost
                )
                prev = temp
            }
        }
        return dp[b.length]
    }

    private fun commandSpecificityBonus(cmd: Command): Int = when (cmd) {
        Command.PanicStop -> 50
        is Command.AppCall, is Command.AppMessage, is Command.AppChat -> 35
        is Command.ChromeNavigate, is Command.InAppSearch, is Command.AppAction -> 32
        is Command.CreateFolder, is Command.CreateFile -> 30
        is Command.SetAlarm, is Command.SetTimer, is Command.WriteEmail, is Command.WriteSms -> 25
        is Command.YoutubeSearch, is Command.Navigate, is Command.WebSearch, is Command.PlaySong,
        is Command.PlayScreenItem, is Command.MediaStep -> 20
        is Command.OpenApp -> if (AppResolver.isKnownAppName(cmd.name)) 350 else 10
        is Command.Call -> 10
        else -> 5
    }

    /** Overlay pill-এ ইউজারকে নিশ্চিত দেখানোর জন্য সংক্ষিপ্ত ও স্পষ্ট কমান্ড ব্যাখ্যা। */
    fun describe(cmd: Command): String = when (cmd) {
        is Command.OpenApp -> "অ্যাপ খোলা: ${cmd.name}"
        is Command.Call -> "ফোন কল: ${cmd.target}"
        is Command.YoutubeSearch -> "YouTube / ভিডিও: ${cmd.query}" + if (cmd.mostViewed) " (Top views)" else ""
        is Command.Swipe -> {
            val scaleTxt = when (cmd.scale) {
                ScrollScale.SLIGHT -> " (একটু)"
                ScrollScale.NORMAL -> ""
                ScrollScale.FAR -> " (বেশি)"
            }
            when (cmd.dir) {
                Dir.UP -> "উপরে স্ক্রল$scaleTxt"
                Dir.DOWN -> "নিচে স্ক্রল$scaleTxt"
                Dir.LEFT -> "বামে Swipe"
                Dir.RIGHT -> "ডানে Swipe"
            }
        }
        is Command.MediaStep -> if (cmd.next) "পরের ভিডিও" else "আগের ভিডিও"
        is Command.ChromeNavigate -> if (cmd.isUrl) "Chrome ওয়েবসাইট: ${cmd.urlOrQuery}" else "Chrome সার্চ: ${cmd.urlOrQuery}"
        is Command.InAppSearch -> "${cmd.appKey ?: "স্ক্রিন"}-এ সার্চ: ${cmd.query}"
        is Command.AppAction -> {
            val app = cmd.appKey?.let { "$it " } ?: ""
            when (cmd.action) {
                AppActionType.LIKE -> "${app}Like"
                AppActionType.COMMENT -> if (cmd.text != null) "${app}কমেন্ট: \"${cmd.text}\"" else "${app}কমেন্ট বক্স খোলা"
                AppActionType.SUBSCRIBE -> "${app}Subscribe"
                AppActionType.FOLLOW -> "${app}Follow"
                AppActionType.REFRESH -> "${app}পেজ রিফ্রেশ"
                AppActionType.NEW_TAB -> "Chrome নতুন ট্যাব"
                AppActionType.FULLSCREEN -> "Fullscreen চালু/বন্ধ"
                AppActionType.END_CALL -> "কল কাটা"
                AppActionType.SPEAKER_ON -> "Speaker চালু"
                AppActionType.SPEAKER_OFF -> "Speaker বন্ধ"
            }
        }
        is Command.PlayScreenItem -> "স্ক্রিনের ${cmd.index + 1}নং ভিডিও চালানো"
        Command.SubmitActiveInput -> "Send / Search চাপুন"
        is Command.ClickText -> "ক্লিক: ${cmd.text}"
        is Command.TypeText -> "টাইপ: \"${cmd.text}\""
        Command.PlayPause -> "Play / Pause"
        Command.Like -> "Like"
        Command.DoubleTap -> "Double Tap"
        Command.Tap -> "Tap"
        Command.Enter -> "Enter চাপুন"
        Command.Back -> "Back"
        Command.Home -> "Home-এ ফেরা"
        Command.Recents -> "Recent Apps"
        Command.Notifications -> "Notifications"
        Command.QuickSettings -> "Quick Settings"
        Command.Lock -> "Screen Lock"
        Command.Screenshot -> "Screenshot"
        is Command.VolumeUp -> if (cmd.steps > 1) "Volume বাড়ানো (${cmd.steps}x)" else "Volume বাড়ানো"
        is Command.VolumeDown -> if (cmd.steps > 1) "Volume কমানো (${cmd.steps}x)" else "Volume কমানো"
        Command.Mute -> "Sound Mute"
        Command.Unmute -> "Sound Unmute"
        Command.CloseAllApps -> "সব অ্যাপ বন্ধ"
        Command.CloseApp -> "বর্তমান অ্যাপ বন্ধ"
        Command.Sleep -> "Sleep মোড"
        Command.StopAssistant -> "Assistant বন্ধ"
        Command.PanicStop -> "সব কাজ বন্ধ"
        is Command.CreateFolder -> if (cmd.parentFolder != null) {
            "ফোল্ডার তৈরি: ${cmd.parentFolder}/${cmd.name}"
        } else {
            "ফোল্ডার তৈরি: ${cmd.name}"
        }
        is Command.CreateFile -> "ফাইল তৈরি: ${cmd.name}.${cmd.ext}"
        is Command.AppChat -> {
            val target = cmd.appName ?: "এই অ্যাপ"
            if (cmd.send) "$target-এ লেখা ও সেন্ড: \"${cmd.text}\"" else "$target-এ লেখা: \"${cmd.text}\""
        }
        is Command.AppCall -> "${cmd.appName} ${if (cmd.video) "ভিডিও" else "অডিও"} কল: ${cmd.contact}"
        is Command.AppMessage -> "${cmd.appName} মেসেজ → ${cmd.contact}: \"${cmd.text}\""
        Command.OpenCamera -> "ক্যামেরা খোলা"
        Command.TakePhoto -> "ছবি তোলা"
        is Command.SetAlarm -> String.format("Alarm: %02d:%02d", cmd.hour24, cmd.minute)
        is Command.SetTimer -> "Timer: ${cmd.seconds} সেকেন্ড"
        is Command.Navigate -> "রুট দেখানো: ${cmd.place}"
        is Command.WebSearch -> "Google সার্চ: ${cmd.query}"
        is Command.PlaySong -> "গান চালানো: ${cmd.query}"
        is Command.SaveNote -> "নোট সেভ: \"${cmd.text}\""
        is Command.WriteEmail -> "ইমেইল ড্রাফট → ${cmd.recipient}"
        is Command.WriteSms -> "SMS ড্রাফট → ${cmd.recipient}"
        is Command.OpenSystemSettings -> "সেটিংস: ${cmd.target.name}"
    }
}
