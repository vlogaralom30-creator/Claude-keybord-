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
        listOf("messenger", "মেসেঞ্জার") to listOf("com.facebook.orca", "com.facebook.mlite"),
        listOf("whatsapp", "whats app", "হোয়াটসঅ্যাপ", "হোয়াটস অ্যাপ") to listOf("com.whatsapp", "com.whatsapp.w4b"),
        listOf("instagram", "ইনস্টাগ্রাম", "insta") to listOf("com.instagram.android"),
        listOf("chrome", "ক্রোম", "browser") to listOf("com.android.chrome"),
        listOf("telegram", "টেলিগ্রাম") to listOf("org.telegram.messenger"),
        listOf("gmail", "জিমেইল") to listOf("com.google.android.gm"),
        listOf("maps", "map", "ম্যাপ") to listOf("com.google.android.apps.maps"),
        listOf("play store", "playstore", "প্লে স্টোর") to listOf("com.android.vending")
    )

    fun launchIntent(ctx: Context, spoken: String): Intent? {
        val pm = ctx.packageManager
        val q = spoken.trim().lowercase()
        if (q.isEmpty()) return null

        for ((names, pkgs) in KNOWN) {
            if (names.any { q == it || q.contains(it) }) {
                for (p in pkgs) pm.getLaunchIntentForPackage(p)?.let { return it }
            }
        }

        // ইনস্টল করা app-এর নাম (label) দিয়ে খোঁজা
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
        return hit?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
    }
}
