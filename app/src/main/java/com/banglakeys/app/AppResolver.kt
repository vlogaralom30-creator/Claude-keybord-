package com.banglakeys.app

import android.content.Context
import android.content.Intent
import android.net.Uri

/** "tiktok", "ফেসবুক", "file" ইত্যাদি নাম থেকে app-এর launch Intent বের করে। */
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
        listOf("telegram", "টেলিগ্রাম") to listOf("org.telegram.messenger", "org.telegram.messenger.web"),
        listOf("gmail", "জিমেইল") to listOf("com.google.android.gm"),
        listOf("maps", "map", "ম্যাপ") to listOf("com.google.android.apps.maps"),
        listOf("play store", "playstore", "প্লে স্টোর") to listOf("com.android.vending"),

        // ---- ফোনের নিজস্ব app (company ভেদে package আলাদা, তাই সবগুলো দেওয়া আছে — যেটা ফোনে আছে সেটাই খুলবে)
        listOf("file", "files", "file manager", "my files", "ফাইল", "ফাইলস", "ফাইল ম্যানেজার") to listOf(
            "com.google.android.apps.nbu.files",   // Files by Google
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.sec.android.app.myfiles",         // Samsung
            "com.mi.android.globalFileexplorer",   // Xiaomi
            "com.android.fileexplorer",            // Xiaomi (চীনা ROM)
            "com.coloros.filemanager",             // Oppo / Realme
            "com.oplus.filemanager",
            "com.android.filemanager",             // Vivo
            "com.vivo.filemanager",
            "com.mediatek.filemanager"
        ),
        listOf("settings", "setting", "সেটিংস", "সেটিং") to listOf("com.android.settings"),
        listOf("camera", "ক্যামেরা") to listOf(
            "com.google.android.GoogleCamera", "com.sec.android.app.camera",
            "com.android.camera", "com.android.camera2"
        ),
        listOf("gallery", "photos", "গ্যালারি", "ফটোস") to listOf(
            "com.google.android.apps.photos", "com.sec.android.gallery3d",
            "com.miui.gallery", "com.android.gallery3d"
        ),
        listOf("calculator", "ক্যালকুলেটর") to listOf(
            "com.google.android.calculator", "com.sec.android.app.popupcalculator",
            "com.miui.calculator", "com.android.calculator2"
        ),
        listOf("clock", "alarm", "ঘড়ি", "অ্যালার্ম") to listOf(
            "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock"
        ),
        listOf("contacts", "contact", "কন্টাক্টস") to listOf(
            "com.google.android.contacts", "com.samsung.android.contacts", "com.android.contacts"
        )
    ) + AppFlows.all.map { it.names to it.packages }

    private val FILE_NAMES = setOf("file", "files", "file manager", "my files", "ফাইল", "ফাইলস", "ফাইল ম্যানেজার")

    private val AMBIGUOUS_MEDIA_WORDS = setOf(
        "reel", "reels", "রিল", "রিলস",
        "video", "videos", "ভিডিও",
        "short", "shorts", "শর্ট", "শর্টস",
        "story", "stories", "স্টোরি"
    )

    fun ambiguousPrompt(spoken: String): String? {
        val q = spoken.trim().lowercase()
        if (q in AMBIGUOUS_MEDIA_WORDS) {
            return "কোন app খুলবো? বলুন: Facebook, TikTok, Instagram বা YouTube"
        }
        return null
    }

    fun isKnownAppName(spoken: String): Boolean {
        val q = spoken.trim().lowercase()
        if (q.isEmpty()) return false
        if (AppFlows.find(q) != null) return true
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

        // ইনস্টল করা app-এর নাম (label) দিয়ে খোঁজা
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
        val byLabel = hit?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (byLabel != null) return byLabel

        // শেষ ভরসা: file app-এর নাম/package না মিললে সিস্টেমের file browser খোলার চেষ্টা
        if (FILE_NAMES.any { q.contains(it) }) {
            return Intent("android.provider.action.BROWSE")
                .setData(Uri.parse("content://com.android.externalstorage.documents/root/primary"))
        }
        return null
    }

    fun launchIntentForAppFlow(ctx: Context, flow: AppFlow): Intent? {
        val pm = ctx.packageManager
        for (p in flow.packages) {
            pm.getLaunchIntentForPackage(p)?.let { return it }
        }
        for (alias in flow.names) {
            launchIntent(ctx, alias)?.let { return it }
        }
        return null
    }
}
