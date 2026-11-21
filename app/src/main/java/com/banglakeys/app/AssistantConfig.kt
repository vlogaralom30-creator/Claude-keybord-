package com.banglakeys.app

/**
 * Assistant-এর সব সেটিং এক জায়গায়। নিজের মতো বদলাতে চাইলে শুধু এই ফাইল এডিট করুন।
 */
object AssistantConfig {

    const val PREFS = "assistant_prefs"
    const val KEY_BANGLA = "bangla"

    /** এই শব্দগুলোর যেকোনোটা শুনলে assistant জেগে ওঠে (ছোট হাতের অক্ষরে লিখুন)। */
    val WAKE_WORDS = listOf(
        "hey bot", "hay bot", "hi bot", "hey boat", "hey but", "heybot", "ok bot", "okay bot",
        "হেই বট", "হে বট", "হ্যালো বট", "হাই বট", "ওকে বট"
    )

    /** Wake word বা কমান্ডের পর এতক্ষণ (ms) আর "Hey Bot" না বললেও চলবে। */
    const val AWAKE_WINDOW_MS = 20_000L

    /**
     * true  = "up" বললে আঙুল নিচ থেকে উপরে swipe করবে (TikTok/Reels/Shorts-এ পরের ভিডিও)।
     * false = উল্টো (up বললে আঙুল উপর থেকে নিচে)।
     */
    const val UP_MEANS_FINGER_UP = true

    /** এই app গুলোতে Like = screen-এর মাঝখানে double tap, Pause/Play = single tap। */
    val SHORT_VIDEO_APPS = setOf(
        "com.zhiliaoapp.musically",      // TikTok
        "com.zhiliaoapp.musically.go",   // TikTok Lite
        "com.ss.android.ugc.trill",      // TikTok (কিছু region)
        "com.instagram.android",         // Instagram Reels
        "com.facebook.katana",           // Facebook Reels
        "com.google.android.youtube"     // YouTube (Shorts/player)
    )

    const val YOUTUBE_PKG = "com.google.android.youtube"

    /**
     * একই মানুষের বিভিন্ন নাম। "call ammi" বললে নিচের পুরো গ্রুপ দিয়ে contact খোঁজা হবে।
     * নিজের contact-এর নাম অনুযায়ী যোগ/বদল করুন।
     */
    val CONTACT_GROUPS: List<List<String>> = listOf(
        listOf("ammi", "amma", "ammu", "mom", "maa", "mother", "আম্মি", "আম্মু", "আম্মা", "আম্মো", "মা"),
        listOf("abbu", "abba", "abbo", "dad", "baba", "father", "আব্বু", "আব্বা", "বাবা"),
        listOf("mim", "meem", "mimm", "মিম")
    )
}
