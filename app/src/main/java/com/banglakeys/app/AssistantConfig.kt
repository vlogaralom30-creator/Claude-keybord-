package com.banglakeys.app

/**
 * EMO রোবট ও ভয়েস কন্ট্রোল সেটিং এক জায়গায়।
 */
object AssistantConfig {

    const val PREFS = "assistant_prefs"
    const val KEY_BANGLA = "bangla"

    /** Wake wordগুলোর যেকোনোটা শুনলে EMO জেগে ওঠে (ছোট হাতের অক্ষরে লিখুন)। */
    val WAKE_WORDS = listOf(
        "hey emo", "hay emo", "hi emo", "hello emo", "ok emo", "okay emo",
        "hey emu", "hay emu", "ok emu", "okay emu",
        "heyemo", "hayemo", "heyemu", "heyimo",
        "হেই ইমো", "হে ইমো", "এই ইমো", "হ্যালো ইমো", "হাই ইমো", "ওকে ইমো",
        "হেই ইমূ", "হে ইমূ", "হেই এমো"
    )

    /** Wake word বা কমান্ডের পর এতক্ষণ (ms) আর "Hey EMO" না বললেও চলবে। */
    const val AWAKE_WINDOW_MS = 20_000L

    /**
     * Legacy fallback default (runtime toggle `AssistantPrefs.scrollByContentDirection(ctx)` এর সাথে সিঙ্ক থাকে)।
     */
    const val UP_MEANS_FINGER_UP = true

    /** এই app গুলোতে Like = screen-এর মাঝখানে double tap, Pause/Play = single tap। */
    val SHORT_VIDEO_APPS = setOf(
        "com.zhiliaoapp.musically",      // TikTok
        "com.zhiliaoapp.musically.go",   // TikTok Lite
        "com.ss.android.ugc.trill",      // TikTok (কিছু region)
        "com.instagram.android",         // Instagram Reels
        "com.facebook.katana",           // Facebook Reels
        "com.facebook.lite",             // Facebook Lite
        "com.google.android.youtube"     // YouTube (Shorts/player)
    )

    val CHROME_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "org.mozilla.firefox",
        "com.microsoft.emmx"
    )

    val FACEBOOK_PACKAGES = setOf("com.facebook.katana", "com.facebook.lite")
    val TIKTOK_PACKAGES = setOf("com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.trill")

    const val YOUTUBE_PKG = "com.google.android.youtube"

    /** "ber hao" / "exit app" বললে true = Home-এ যাবে, false = Back চাপবে। */
    const val LEAVE_APP_GOES_HOME = true

    /**
     * "sob app close koro" বললে Recents খুলে এই লেখার বাটন খোঁজা হয়।
     * আপনার ফোনের বাটনে অন্য লেখা থাকলে (Samsung: "Close all", Pixel/Xiaomi: "Clear all") এখানে যোগ করুন।
     */
    val CLEAR_ALL_LABELS = listOf(
        "clear all", "close all", "clear everything", "close all apps",
        "সব বন্ধ", "সব মুছুন", "সব সাফ", "সবকিছু সাফ", "সবগুলি বন্ধ"
    )

    /**
     * একই মানুষের বিভিন্ন নাম। "call ammi" বললে নিচের পুরো গ্রুপ দিয়ে contact খোঁজা হবে।
     * নিজের contact-এর নাম অনুযায়ী যোগ/বদল করুন।
     */
    val CONTACT_GROUPS: List<List<String>> = listOf(
        listOf("ammi", "amma", "ammu", "mom", "maa", "mother", "আম্মি", "আম্মু", "আম্মা", "আম্মো", "মা"),
        listOf("abbu", "abba", "abbo", "dad", "baba", "father", "আব্বু", "আব্বা", "বাবা"),
        listOf("mim", "meem", "mimm", "মিম"),
        listOf("bhai", "vai", "bhaiya", "vaiya", "brother", "ভাই", "ভাইয়া"),
        listOf("apu", "apa", "apuni", "sister", "আপু", "আপা")
    )
}
