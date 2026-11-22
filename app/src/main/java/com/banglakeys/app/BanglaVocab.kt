package com.banglakeys.app

/**
 * ফোন-কন্ট্রোলের সব শব্দভান্ডার (Banglish + বাংলা + English) এক জায়গায়।
 *
 * নতুন শব্দ শেখাতে চাইলে শুধু এই ফাইলের সংশ্লিষ্ট তালিকায় নতুন "শব্দ" যোগ করুন — অন্য কোথাও হাত দিতে হবে না।
 *
 * নিয়ম:
 *  - সব শব্দ ছোট হাতের অক্ষরে লিখুন (বাংলার ক্ষেত্রে যেভাবে recognizer লেখে সেভাবে)।
 *  - একাধিক শব্দের বাক্যাংশও চলে (যেমন "ber hao", "clear all")।
 *  - Recognizer একই কথা নানা বানানে লেখে (khulo / kholo / kulo), তাই কাছাকাছি বানানগুলোও রাখুন।
 *  - hyphen (-) আর punctuation parser নিজেই space করে দেয়: "adele-hello" = "adele hello"।
 */
object BanglaVocab {

    // ---------------------------------------------------------------- app খোলা
    /** "tiktok khulo", "file e dhuko", "open chrome" */
    val OPEN = setOf(
        "open", "launch", "start",
        "khol", "kholo", "kholun", "khulo", "khulun", "khule", "khul", "kulo",
        "dhuko", "dhuk", "dhukao", "dhuke", "dhukun", "dhoko", "chalu",
        "ওপেন", "খোলো", "খুলো", "খোল", "খুলুন", "খুলে", "চালু",
        "ঢুকো", "ঢোকো", "ঢুকাও", "ঢুকুন", "ঢুকে"
    )

    /** কমান্ডের "ফালতু" শব্দ — app/contact/query-র নাম বের করার সময় বাদ যায়। */
    val FILLERS = setOf(
        "kor", "koro", "kore", "koren", "korun", "kora", "korbe", "dao", "dau", "daw", "den", "dibe", "diye",
        "plz", "pls", "please", "the", "hey", "bot", "app", "on", "in", "to", "e", "te", "ke",
        "ekta", "ekti", "ta", "ti", "ektu", "ekhon", "amake", "amar",
        "করো", "কর", "করুন", "করে", "দাও", "দেন", "দিয়ে", "অ্যাপ", "এ্যাপ", "প্লিজ", "কে",
        "এ", "তে", "একটা", "একটি", "একটু", "এখন", "আমাকে", "আমার"
    )

    // ---------------------------------------------------------------- call
    val CALL = setOf("call", "dial", "ring", "phone", "kol", "কল", "ফোন")

    // ---------------------------------------------------------------- YouTube / গান / ভিডিও
    val YOUTUBE = setOf(
        "youtube", "yt", "utube", "yutub", "youtub", "iutub",
        "ইউটিউব", "ইউটিউবে", "ইউটিউবের"
    )

    val SEARCH = setOf(
        "search", "searching", "searche", "serch", "sarch",
        "khuj", "khujo", "khujun", "khuje", "khoj", "khojo",
        "খুঁজ", "খুঁজো", "খুঁজুন", "খুঁজে", "খুজো", "খুজ", "খোঁজ", "সার্চ", "সারচ"
    )

    /** এগুলো বললে বুঝবো ভিডিও/গান চালাতে বলছে (query থেকে বাদ যায়)। */
    val MEDIA = setOf(
        "video", "videos", "vedio", "bideo", "song", "songs", "gaan", "gan", "music",
        "ভিডিও", "ভিডিওটা", "ভিডিওটি", "গান", "গানটা", "গানটি", "মিউজিক"
    )

    /** এগুলো থাকলেও YouTube-এ খোঁজার ইঙ্গিত (query-তে থেকে যায়, কারণ খোঁজার জন্য কাজের)। */
    val MEDIA_KEEP = setOf("natok", "নাটক", "movie", "মুভি", "trailer", "ট্রেইলার", "cartoon", "কার্টুন")

    /** "adele hello video play koro", "gaan chalao", "video dekhao" */
    val PLAY = setOf(
        "play", "pley", "plae",
        "chalao", "chalau", "chalaw", "chalan", "chaliye",
        "dekhao", "dekhaw", "dekhan", "shonao", "sonao", "shunao", "bajao", "bajau",
        "চালাও", "চালান", "চালিয়ে", "দেখাও", "দেখান", "শোনাও", "শুনাও", "বাজাও", "প্লে"
    )

    /** "most viewed" বোঝানো শব্দ (বাক্যাংশ)। */
    val MOST_VIEWED = setOf(
        "most viewed", "most watched", "most view", "top", "highest views",
        "beshi view", "beshi views", "sobcheye beshi", "shobcheye beshi", "shobcheye beshi view",
        "বেশি ভিউ", "সবচেয়ে বেশি"
    )

    /** query থেকে বাদ যাবে এমন অতিরিক্ত শব্দ। */
    val QUERY_DROP = setOf(
        "er", "ar", "r", "for", "by", "most", "viewed", "watched", "views", "view", "top", "highest",
        "beshi", "sobcheye", "shobcheye", "theke", "porjonto",
        "resume", "again", "abar", "continue", "now",
        "বেশি", "ভিউ", "সবচেয়ে", "থেকে", "আবার", "দেখা"
    )

    // ---------------------------------------------------------------- tap / click / type
    val CLICK = setOf("click", "tap", "press", "ক্লিক", "ট্যাপ", "চাপো")
    val TYPE = setOf("type", "write", "likho", "lekho", "লেখো", "লিখো")
    val DOUBLE = setOf("double tap", "double", "dubble", "dabl", "ডাবল")
    val ENTER = setOf("enter", "submit", "এন্টার")

    // ---------------------------------------------------------------- like / pause
    val LIKE = setOf(
        "like", "love", "heart", "nice", "wow", "sundor", "sundar", "darun",
        "pochondo", "pasand", "good", "great", "awesome", "superb",
        "লাইক", "সুন্দর", "দারুণ", "বাহ", "পছন্দ"
    )

    val PAUSE = setOf(
        "pause", "puse", "pose", "play", "resume", "stop", "thamo", "thamao", "thamaw", "thamun",
        "chalao", "chalau", "chalaw",
        "থামো", "থামাও", "থামুন", "চালাও", "পজ", "প্লে", "রিজিউম"
    )

    // ---------------------------------------------------------------- volume
    val VOLUME_NOUN = setOf(
        "volume", "vol", "sound", "awaj", "awaz", "awaaj", "shobdo", "sobdo",
        "ভলিউম", "সাউন্ড", "আওয়াজ", "শব্দ"
    )
    val VOL_UP = setOf(
        "up", "barao", "baraw", "barau", "bara", "barai", "bera", "berao", "beshi", "jore", "joore",
        "increase", "high", "higher", "louder", "full", "max", "maximum",
        "বাড়াও", "বাড়া", "বাড়ান", "বাড়িয়ে", "বেশি", "জোরে", "ফুল"
    )
    val VOL_DOWN = setOf(
        "down", "kamao", "komao", "kamaw", "kamau", "kom", "kome",
        "decrease", "low", "lower", "less", "quieter", "softer", "aste", "ashte",
        "কমাও", "কমান", "কম", "কমিয়ে", "আস্তে"
    )
    /** এগুলো থাকলে এক ধাপ নয়, অনেকটা (৫ ধাপ) বাড়ে/কমে। */
    val VOL_BIG = setOf("full", "max", "maximum", "onek", "অনেক", "ফুল")
    val MUTE = setOf(
        "mute", "myut", "silent", "chup", "nishobdo", "shobdo bondho", "awaj bondho", "sound off",
        "মিউট", "চুপ", "নিঃশব্দ", "আওয়াজ বন্ধ", "শব্দ বন্ধ"
    )
    val UNMUTE = setOf("unmute", "awaj dao", "awaj chalu", "আনমিউট", "আওয়াজ দাও", "আওয়াজ চালু")

    // ---------------------------------------------------------------- app ছেড়ে বের হওয়া / minimize
    /** "ber hao" = app থেকে বের হও (config অনুযায়ী Home বা Back)। */
    val LEAVE_APP = setOf(
        "ber hao", "ber hou", "ber how", "ber hoy", "ber ho", "ber hoye jao", "ber hoye",
        "bher hao", "bair hao", "bahir hao", "bahire jao", "bair hoye jao", "chere dao", "chhere dao",
        "exit app", "leave app", "get out", "go out", "come out",
        "বের হও", "বের হয়ে যাও", "বের হন", "বের হয়ে", "বাইরে যাও", "বাহির হও", "ছেড়ে দাও"
    )

    /** "minimize koro" = app ছোট করে Home-এ যাও। */
    val MINIMIZE = setOf(
        "minimize", "minimise", "minimized", "mini mize", "minimaiz",
        "মিনিমাইজ", "মিনিমাইজড"
    )

    // ---------------------------------------------------------------- app বন্ধ করা
    /** "sob app close koro" এর "sob" */
    val ALL = setOf(
        "sob", "shob", "sab", "shab", "all", "every", "everything",
        "sobgulo", "shobgulo", "sobkichu", "shobkichu",
        "সব", "সবগুলো", "সবগুলি", "সবকিছু", "সকল"
    )
    val CLOSE = setOf(
        "close", "closs", "clos", "bondho", "bondo", "bandho", "bondh", "clear", "kill", "clean", "khali",
        "বন্ধ", "ক্লোজ", "মুছো", "মুছে", "মুছুন", "সাফ"
    )
    /** "ei app bondho koro" — চলতি app বোঝাতে। */
    val APP_WORD = setOf(
        "app", "apps", "application", "eta", "eita", "this", "current",
        "অ্যাপ", "এ্যাপ", "অ্যাপস", "এপ", "এটা", "এইটা"
    )

    // ---------------------------------------------------------------- system
    val STOP_ASSISTANT = setOf("off", "stop", "bondho", "bandho", "বন্ধ")
    val SLEEP = setOf("sleep", "ghumao", "ghumaw", "ঘুমাও")
    val BACK = setOf(
        "back", "go back", "pechone", "pichone", "pechhone", "pichhone", "pechone jao", "fire jao", "fire esho",
        "ব্যাক", "পেছনে", "পিছনে", "ফিরে যাও", "ফিরে এসো"
    )
    val HOME = setOf("home", "home screen", "হোম")
    val RECENTS = setOf("recent", "recents", "multitask", "রিসেন্ট")
    val NOTIFICATIONS = setOf("notification", "notifications", "নোটিফিকেশন")
    val QUICK_SETTINGS = setOf("quick settings")
    val SCREENSHOT = setOf("screenshot", "screen shot", "স্ক্রিনশট")
    val LOCK = setOf("lock", "screen off", "screen lock", "screen bondho", "screen bondo", "লক", "স্ক্রিন বন্ধ")

    // ---------------------------------------------------------------- swipe / scroll
    val SWIPE_UP = setOf(
        "up", "next", "porer", "upore", "upor", "uporer", "uthao", "uthaw", "utha", "scroll up",
        "ওপরে", "উপরে", "উপর", "আপ", "উঠাও", "ওঠাও", "নেক্সট", "পরের"
    )
    val SWIPE_DOWN = setOf(
        "down", "previous", "prev", "ager", "niche", "nichey", "nicher", "namao", "namaw", "nama", "scroll down",
        "নিচে", "নিচের", "নামাও", "ডাউন", "আগের"
    )
    val SWIPE_LEFT = setOf("left", "bame", "bam", "bamer", "বামে", "বাম", "লেফট")
    val SWIPE_RIGHT = setOf("right", "dane", "dan", "daner", "ডানে", "ডান", "রাইট")
}
