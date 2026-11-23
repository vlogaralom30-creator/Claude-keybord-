package com.banglakeys.app

/**
 * BanglaKeys Assistant-এর ৯৯% নির্ভুল কমান্ড লিস্ট ও উদাহরণ ক্যাটালগ।
 * CommandGuideActivity থেকে সার্চ, ফিল্টার, কপি এবং সরাসরি টেস্ট/চালানো যায়।
 */
object CommandCatalog {

    enum class Category(val key: String, val labelBn: String) {
        ALL("all", "সব কমান্ড"),
        SCROLL_MEMORY("scroll", "স্ক্রল ও মেমোরি"),
        YOUTUBE("youtube", "YouTube"),
        CHROME("chrome", "Chrome"),
        FACEBOOK("facebook", "Facebook"),
        TIKTOK("tiktok", "TikTok / Reels"),
        CALL("call", "ফোন ও কল"),
        CHAT_AI("chat", "Chat & টাইপ+সেন্ড"),
        UTILITIES("utils", "দৈনন্দিন ও সিস্টেম")
    }

    data class Entry(
        val id: String,
        val category: Category,
        val titleBn: String,
        val descriptionBn: String,
        val examples: List<String>,
        val tipBn: String,
        val testPhrase: String
    )

    val entries: List<Entry> = listOf(
        // 1. Scroll & Short-Term Memory
        Entry(
            id = "scroll_slight",
            category = Category.SCROLL_MEMORY,
            titleBn = "একটু উপরে বা নিচে স্ক্রল",
            descriptionBn = "ধীরে ও অল্প পরিমাণ পেজ উপরে বা নিচে স্ক্রল করে (~20% স্ট্রোক)।",
            examples = listOf("ektu niche", "ektu upore", "একটু নিচে নামো", "একটু উপরে উঠাও", "olpo niche"),
            tipBn = "সেটিংস থেকে Content Direction (ডিফল্ট) বা Finger Direction বদলানো যায়।",
            testPhrase = "ektu niche"
        ),
        Entry(
            id = "scroll_normal_far",
            category = Category.SCROLL_MEMORY,
            titleBn = "বেশি বা সাধারণ স্ক্রল",
            descriptionBn = "স্বাভাবিক (~44%) বা বড় (~72%) স্ক্রল — দ্রুত পেজ বা ফিড ঘোরানোর জন্য।",
            examples = listOf("niche", "upore", "aro beshi niche", "onek upore uthao", "আরো বেশি নিচে"),
            tipBn = "আগে কোনোদিকে স্ক্রল করে পরে শুধু 'aro beshi' বা 'arekto' বললেও মেমোরি ধরে রাখবে।",
            testPhrase = "aro beshi niche"
        ),
        Entry(
            id = "memory_followups",
            category = Category.SCROLL_MEMORY,
            titleBn = "শর্ট-টার্ম মেমোরি Follow-up",
            descriptionBn = "আগের কমান্ড ও স্ক্রিন মনে রেখে ছোট কথায় কাজ পুনরাবৃত্তি বা বাড়ানো।",
            examples = listOf("aro beshi", "arekto", "abar koro", "repeat", "eta send koro"),
            tipBn = "৯০ সেকেন্ড পর্যন্ত আগের অ্যাপ, স্ক্রল দিক এবং শেষ লেখা মেমোরিতে থাকে।",
            testPhrase = "arekto"
        ),
        Entry(
            id = "screen_click_text",
            category = Category.SCROLL_MEMORY,
            titleBn = "স্ক্রিনের যেকোনো বাটন/লেখা চাপুন",
            descriptionBn = "স্ক্রিনে যে লেখা বা বাটন দেখা যাচ্ছে তার নাম বললেই অফলাইনে ক্লিক করে।",
            examples = listOf("click Skip", "Next চাপো", "Settings-এ ক্লিক করো", "Double tap", "Back যাও"),
            tipBn = "বাটনে থাকা ঠিক শব্দটি বললে ৯৯% নির্ভুলভাবে ক্লিক হয়।",
            testPhrase = "click Settings"
        ),

        // 2. YouTube
        Entry(
            id = "yt_visible_or_search",
            category = Category.YOUTUBE,
            titleBn = "ভিডিও টাইটেল বলে চালু বা সার্চ",
            descriptionBn = "স্ক্রিনে ভিডিও থাকলে সরাসরি ওটাই প্লে করবে; না থাকলে YouTube-এ সার্চ করে চালাবে।",
            examples = listOf(
                "youtube e natok chalao",
                "play bangla natok",
                "first video play koro",
                "second video chalao",
                "youtube most viewed islamic song"
            ),
            tipBn = "হোম ফিডে থাকা ভিডিওর শিরোনামের ২-৩টি শব্দ বললে সার্চ না করে সরাসরি প্লে হয়।",
            testPhrase = "youtube e natok chalao"
        ),
        Entry(
            id = "yt_controls",
            category = Category.YOUTUBE,
            titleBn = "YouTube প্লেয়ার ও চ্যানেল অ্যাকশন",
            descriptionBn = "ভিডিও থামানো, পরের ভিডিও, লাইক, সাবস্ক্রাইব, ফুলস্ক্রিন ও কমেন্ট করা।",
            examples = listOf(
                "play pause",
                "porer video",
                "subscribe koro",
                "fullscreen koro",
                "youtube e comment koro darun"
            ),
            tipBn = "ভিডিও চলার সময় শুধু 'subscribe koro' বা 'fullscreen koro' বললেই কাজ হয়।",
            testPhrase = "subscribe koro"
        ),

        // 3. Chrome
        Entry(
            id = "chrome_site_or_search",
            category = Category.CHROME,
            titleBn = "Chrome-এ ওয়েবসাইট ও সার্চ",
            descriptionBn = "ডোমেইন বা মুখের 'dot com' বললে সরাসরি ওয়েবসাইট খুলে; বাকি সব সার্চ করে।",
            examples = listOf(
                "chrome e prothomalo.com khulo",
                "chrome e youtube dot com khulo",
                "chrome e cricket score search koro",
                "google search bangla news"
            ),
            tipBn = "ভয়েসে '.com' ঠিকমতো না এলেও 'prothomalo dot com' বললেই অটো `.com` হয়ে যাবে।",
            testPhrase = "chrome e prothomalo.com khulo"
        ),
        Entry(
            id = "chrome_tabs_refresh",
            category = Category.CHROME,
            titleBn = "Chrome নতুন ট্যাব ও রিফ্রেশ",
            descriptionBn = "ব্রাউজারের টপ আইকন বা মেনু থেকে নতুন ট্যাব ও পেজ রিলোড করে।",
            examples = listOf("new tab khulo", "notun tab khulo", "refresh koro", "reload page"),
            tipBn = "Chrome খোলা অবস্থায় বললে সাথে সাথেই কাজ করবে।",
            testPhrase = "new tab khulo"
        ),

        // 4. Facebook
        Entry(
            id = "fb_search_action",
            category = Category.FACEBOOK,
            titleBn = "Facebook সার্চ, লাইক ও কমেন্ট",
            descriptionBn = "ফেসবুক খোলা, যেকোনো পেজ/ভিডিও সার্চ, রিয়েক্ট/লাইক ও কমেন্ট পোস্ট।",
            examples = listOf(
                "facebook khulo",
                "facebook e bangladesh cricket search koro",
                "like koro",
                "comment koro shundor",
                "follow koro"
            ),
            tipBn = "পোস্টের নিচে থাকলে 'comment koro <আপনার কথা>' বললে বক্স খুলে টাইপ ও পোস্ট করবে।",
            testPhrase = "facebook e cricket search koro"
        ),

        // 5. TikTok / Reels
        Entry(
            id = "tiktok_swipe_react",
            category = Category.TIKTOK,
            titleBn = "TikTok / Reels পরের ভিডিও ও ফলো",
            descriptionBn = "পরের বা আগের শর্ট ভিডিওতে যাওয়া, ডাবল-ট্যাপ লাইক, ফলো ও কমেন্ট।",
            examples = listOf(
                "tiktok khulo",
                "porer video",
                "next video",
                "ager video",
                "like koro",
                "tiktok follow koro",
                "tiktok e comment koro nice"
            ),
            tipBn = "'porer video' সবসময় পরের শর্ট ভিডিও আনবে, স্ক্রল ডিরেকশন সেটিংস যাই থাকুক।",
            testPhrase = "porer video"
        ),

        // 6. Phone & Messenger Calls
        Entry(
            id = "phone_call",
            category = Category.CALL,
            titleBn = "সাধারণ কল, ইমু/WhatsApp কল ও কল কাটা",
            descriptionBn = "কনট্যাক্ট নাম বা বাংলা সম্পর্কের শব্দ (মা, বাবা, ভাই) দিয়ে কল ও কল নিয়ন্ত্রণ।",
            examples = listOf(
                "call Ma",
                "Rahim ke call koro",
                "whatsapp e Rahim ke video call koro",
                "imo te Ma ke call koro",
                "call kato",
                "speaker on koro"
            ),
            tipBn = "সুরক্ষার জন্য কলের আগে ওভারলেতে Confirm চাইবে (চাইলে সেটিংস থেকে বন্ধ করতে পারেন)।",
            testPhrase = "call kato"
        ),

        // 7. Chat & Type + Send
        Entry(
            id = "chat_type_send",
            category = Category.CHAT_AI,
            titleBn = "মেসেজ লেখা ও Send আইকন চাপা",
            descriptionBn = "নাম/লেবেলহীন Send ও Search আইকনও EditText-এর পাশ থেকে খুঁজে চাপতে পারে।",
            examples = listOf(
                "type hello কেমন আছো",
                "likho আমি এখন আসছি and send",
                "eta send koro",
                "whatsapp e Rahim ke message dao ami aschi",
                "claude e likho explain gravity and send"
            ),
            tipBn = "কিছু টাইপ করে থেমে থাকলে পরে শুধু 'eta send koro' বললেই পাশের Send আইকন চাপবে।",
            testPhrase = "eta send koro"
        ),

        // 8. Utilities & System
        Entry(
            id = "system_utilities",
            category = Category.UTILITIES,
            titleBn = "অ্যালার্ম, টাইমার, ভলিউম ও সিস্টেম কমান্ড",
            descriptionBn = "দ্রুত অ্যালার্ম, টাইমার, ম্যাপস ন্যাভিগেশন, স্ক্রিনশট, নোট সেভ ও ভলিউম।",
            examples = listOf(
                "7 tay alarm dao",
                "5 minute timer set koro",
                "navigate to Dhanmondi",
                "volume full koro",
                "screenshot nao",
                "wifi settings khulo",
                "sob bondho"
            ),
            tipBn = "জরুরি অবস্থায় সব চলমান ফ্লো থামাতে বলুন: 'sob bondho' বা 'stop everything'।",
            testPhrase = "5 minute timer set koro"
        )
    )

    fun filter(category: Category, query: String): List<Entry> {
        val q = query.trim().lowercase()
        return entries.filter { entry ->
            val catOk = category == Category.ALL || entry.category == category
            if (!catOk) return@filter false
            if (q.isEmpty()) return@filter true
            entry.titleBn.lowercase().contains(q) ||
                entry.descriptionBn.lowercase().contains(q) ||
                entry.tipBn.lowercase().contains(q) ||
                entry.category.labelBn.lowercase().contains(q) ||
                entry.examples.any { it.lowercase().contains(q) }
        }
    }
}
