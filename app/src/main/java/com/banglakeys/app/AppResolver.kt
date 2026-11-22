package com.banglakeys.app

import android.content.Context
import android.content.Intent

/** "tiktok", "ফেসবুক" ইত্যাদি নাম থেকে app-এর launch Intent বের করে। */
object AppResolver {

    private val KNOWN: List<Pair<List<String>, List<String>>> = listOf(
        listOf("tiktok", "tik tok", "টিকটক", "টিক টক") to
            listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"),
        listOf("youtube", "you tube", "ইউটিউব") to listOf("com.google.android.youtube"),
        listOf("facebook", "ফেসবুক", "fb") to listOf("com.facebook.katana", "com.facebook.lite"),
        listOf("instagram", "ইনস্টাগ্রাম", "insta") to listOf("com.instagram.android"),
        listOf("chrome", "ক্রোম", "browser") to listOf("com.android.chrome"),
        listOf("gmail", "জিমেইল") to listOf("com.google.android.gm"),
        listOf("maps", "map", "ম্যাপ") to listOf("com.google.android.apps.maps"),
        listOf("play store", "playstore", "প্লে স্টোর") to listOf("com.android.vending")
    ) + AppFlows.all.map { it.names to it.packages }

    // "reel", "shorts", "video" ইত্যাদি একাধিক app হতে পারে এমন শব্দ — এগুলো শুনলে
    // আন্দাজ করে একটা app খুলে ফেলার বদলে, কোনটা চান তা স্পষ্ট করে জিজ্ঞাসা করা হয়।
    private val AMBIGUOUS: Map<String, List<String>> = mapOf(
        "reel" to listOf("TikTok", "Instagram Reels", "Facebook Reels"),
        "reels" to listOf("TikTok", "Instagram Reels", "Facebook Reels"),
        "short" to listOf("YouTube Shorts", "Instagram Reels", "TikTok"),
        "shorts" to listOf("YouTube Shorts", "Instagram Reels", "TikTok"),
        "video" to listOf("YouTube", "TikTok", "Facebook"),
        "videos" to listOf("YouTube", "TikTok", "Facebook"),
        "রিল" to listOf("TikTok", "Instagram Reels", "Facebook Reels"),
        "ভিডিও" to listOf("YouTube", "TikTok", "Facebook"),
    )

    /**
     * [spoken] যদি একটা নির্দিষ্ট app না বুঝিয়ে একাধিক app হতে পারে এমন ছোট শব্দ হয়
     * (যেমন শুধু "reel" বা "shorts"), তাহলে স্পষ্ট জিজ্ঞাসা লেখা ফেরত দেয়; নাহলে null।
     * null মানে [launchIntent] দিয়ে সরাসরি app খোঁজা নিরাপদ।
     */
    fun ambiguousPrompt(spoken: String): String? {
        val q = spoken.trim().lowercase()
        val options = AMBIGUOUS[q] ?: return null
        return "\"$spoken\" বললে কোনটা বোঝাচ্ছেন — ${options.joinToString(" / ")}? নির্দিষ্ট নাম বলুন।"
    }

    fun isKnownAppName(spoken: String): Boolean {
        val q = spoken.trim().lowercase()
        if (q.isEmpty()) return false
        return KNOWN.any { (names, _) -> names.any { q == it || q.contains(it) } }
    }

    fun launchIntent(ctx: Context, spoken: String): Intent? {
        val pm = ctx.packageManager
        val q = spoken.trim().lowercase()
        if (q.isEmpty()) return null

        for ((names, pkgs) in KNOWN) {
            if (names.any { q == it || q.contains(it) }) {
                for (p in pkgs) pm.getLaunchIntentForPackage(p)?.let { return it }
            }
        }

        return launchIntentByLabel(ctx, listOf(q))
    }

    fun launchIntentForAppFlow(ctx: Context, flow: AppFlow): Intent? {
        val pm = ctx.packageManager
        for (p in flow.packages) {
            pm.getLaunchIntentForPackage(p)?.let { return it }
        }
        return launchIntentByLabel(ctx, listOf(flow.key) + flow.names)
    }

    private fun launchIntentByLabel(ctx: Context, queries: List<String>): Intent? {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        for (q in queries) {
            val lq = q.trim().lowercase()
            if (lq.isEmpty()) continue
            val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == lq }
                ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(lq) }
            if (hit != null) {
                pm.getLaunchIntentForPackage(hit.activityInfo.packageName)?.let { return it }
            }
        }
        return null
    }
}
