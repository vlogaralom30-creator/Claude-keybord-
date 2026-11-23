package com.banglakeys.app

enum class Kind {
    AI_CHAT,
    MESSENGER,
    BROWSER,
    VIDEO,
    SOCIAL
}

/**
 * Data-driven app flow definition.
 * নতুন app যোগ করতে শুধু [AppFlows.all] লিস্টে একটা নতুন row যোগ করুন।
 */
data class AppFlow(
    val key: String,
    val names: List<String>,
    val packages: List<String>,
    val kind: Kind,
    val inputHints: List<String>,
    val searchHints: List<String>,
    val sendHints: List<String>,
    val audioCallHints: List<String>,
    val videoCallHints: List<String>,
    val openTimeoutMs: Long = 6000L,
    val stepTimeoutMs: Long = 4000L
)

object AppFlows {

    val all: List<AppFlow> = listOf(
        AppFlow(
            key = "claude",
            names = listOf("claude", "cloud", "clod", "claud", "ক্লড", "ক্লাউড", "ক্লদ"),
            packages = listOf("com.anthropic.claude"),
            kind = Kind.AI_CHAT,
            inputHints = listOf("message", "ask", "type", "reply", "chat", "talk to claude", "প্রশ্ন", "মেসেজ", "লিখুন"),
            searchHints = emptyList(),
            sendHints = listOf("send", "send message", "submit", "পাঠান", "পাঠাও"),
            audioCallHints = emptyList(),
            videoCallHints = emptyList()
        ),
        AppFlow(
            key = "chatgpt",
            names = listOf("chatgpt", "chat gpt", "gpt", "চ্যাট জিপিটি", "চ্যাটজিপিটি", "জিপিটি"),
            packages = listOf("com.openai.chatgpt"),
            kind = Kind.AI_CHAT,
            inputHints = listOf("message", "ask", "type", "ask anything", "প্রশ্ন", "মেসেজ", "লিখুন"),
            searchHints = emptyList(),
            sendHints = listOf("send", "send message", "send prompt", "submit", "পাঠান", "পাঠাও"),
            audioCallHints = emptyList(),
            videoCallHints = emptyList()
        ),
        AppFlow(
            key = "gemini",
            names = listOf("gemini", "jemini", "bard", "জেমিনি", "জেমিনাই"),
            packages = listOf("com.google.android.apps.bard"),
            kind = Kind.AI_CHAT,
            inputHints = listOf("ask gemini", "ask", "message", "type", "enter a prompt", "প্রশ্ন", "মেসেজ", "লিখুন"),
            searchHints = emptyList(),
            sendHints = listOf("send", "send message", "submit", "পাঠান", "পাঠাও"),
            audioCallHints = emptyList(),
            videoCallHints = emptyList()
        ),
        AppFlow(
            key = "imo",
            names = listOf("imo", "emo", "immo", "ইমু", "ইমো"),
            packages = listOf("com.imo.android.imoim", "com.imo.android.imoimhd", "com.imo.android.imoimbeta"),
            kind = Kind.MESSENGER,
            inputHints = listOf("message", "say something", "type a message", "chat", "মেসেজ", "বার্তা", "লিখুন"),
            searchHints = listOf("search", "find", "খুঁজুন", "সার্চ"),
            sendHints = listOf("send", "send message", "পাঠান", "পাঠাও"),
            audioCallHints = listOf("voice call", "audio call", "call", "phone", "ভয়েস কল", "অডিও কল", "কল"),
            videoCallHints = listOf("video call", "video", "ভিডিও কল", "ভিডিও")
        ),
        AppFlow(
            key = "whatsapp",
            names = listOf("whatsapp", "whats app", "what's app", "wasap", "watsap", "হোয়াটসঅ্যাপ", "হোয়াটস অ্যাপ"),
            packages = listOf("com.whatsapp", "com.whatsapp.w4b"),
            kind = Kind.MESSENGER,
            inputHints = listOf("message", "type a message", "মেসেজ", "বার্তা", "লিখুন"),
            searchHints = listOf("search", "খুঁজুন", "সার্চ"),
            sendHints = listOf("send", "পাঠান", "পাঠাও"),
            audioCallHints = listOf("voice call", "audio call", "call", "ভয়েস কল", "অডিও কল", "কল"),
            videoCallHints = listOf("video call", "ভিডিও কল", "ভিডিও")
        ),
        AppFlow(
            key = "messenger",
            names = listOf("messenger", "messanger", "মেসেঞ্জার", "ম্যাসেঞ্জার"),
            packages = listOf("com.facebook.orca", "com.facebook.mlite"),
            kind = Kind.MESSENGER,
            inputHints = listOf("message", "aa", "type a message", "মেসেজ", "লিখুন"),
            searchHints = listOf("search", "খুঁজুন", "সার্চ"),
            sendHints = listOf("send", "পাঠান", "পাঠাও"),
            audioCallHints = listOf("audio call", "voice call", "start audio call", "call", "অডিও কল", "ভয়েস কল", "কল"),
            videoCallHints = listOf("video call", "start video call", "ভিডিও কল", "ভিডিও")
        ),
        AppFlow(
            key = "telegram",
            names = listOf("telegram", "টেলিগ্রাম"),
            packages = listOf("org.telegram.messenger", "org.telegram.messenger.web"),
            kind = Kind.MESSENGER,
            inputHints = listOf("message", "মেসেজ", "বার্তা", "লিখুন"),
            searchHints = listOf("search", "খুঁজুন", "সার্চ"),
            sendHints = listOf("send", "পাঠান", "পাঠাও"),
            audioCallHints = listOf("call", "voice call", "audio call", "কল", "অডিও কল"),
            videoCallHints = listOf("video call", "video", "ভিডিও কল", "ভিডিও")
        ),
        AppFlow(
            key = "chrome",
            names = listOf("chrome", "chorom", "crome", "browser", "ক্রোম", "ক্রোমে", "ব্রাউজার"),
            packages = listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev"),
            kind = Kind.BROWSER,
            inputHints = listOf("search or type url", "search or type web address", "search", "url_bar", "ঠিকানা", "সার্চ"),
            searchHints = listOf("search", "url_bar", "omnibox", "সার্চ"),
            sendHints = listOf("go", "search", "enter", "যান"),
            audioCallHints = emptyList(),
            videoCallHints = emptyList()
        ),
        AppFlow(
            key = "youtube",
            names = listOf("youtube", "you tube", "yt", "utube", "ইউটিউব", "ইউটিউবে"),
            packages = listOf("com.google.android.youtube"),
            kind = Kind.VIDEO,
            inputHints = listOf("search youtube", "add a comment", "comment", "লিখুন", "কমেন্ট"),
            searchHints = listOf("search", "search youtube", "menu_search", "সার্চ"),
            sendHints = listOf("send", "post", "comment", "পাঠান", "পোস্ট"),
            audioCallHints = emptyList(),
            videoCallHints = emptyList()
        ),
        AppFlow(
            key = "facebook",
            names = listOf("facebook", "fb", "face book", "ফেসবুক", "ফেসবুকে", "এফবি"),
            packages = listOf("com.facebook.katana", "com.facebook.lite"),
            kind = Kind.SOCIAL,
            inputHints = listOf("write a comment", "comment", "search facebook", "what's on your mind", "কমেন্ট", "লিখুন"),
            searchHints = listOf("search facebook", "search", "সার্চ", "খুঁজুন"),
            sendHints = listOf("send", "post", "submit", "পাঠান", "পোস্ট"),
            audioCallHints = emptyList(),
            videoCallHints = emptyList()
        ),
        AppFlow(
            key = "tiktok",
            names = listOf("tiktok", "tik tok", "tick tock", "tiktak", "টিকটক", "টিক টক", "টিকটকে"),
            packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"),
            kind = Kind.VIDEO,
            inputHints = listOf("add comment", "comment", "search", "কমেন্ট", "লিখুন"),
            searchHints = listOf("search", "discover", "খুঁজুন", "সার্চ"),
            sendHints = listOf("send", "post", "পাঠান", "পোস্ট"),
            audioCallHints = emptyList(),
            videoCallHints = emptyList()
        )
    )

    fun find(spokenName: String): AppFlow? {
        val q = spokenName.trim().lowercase()
        if (q.isEmpty()) return null
        // Exact match first
        for (flow in all) {
            if (flow.key == q || flow.names.any { it.lowercase() == q }) return flow
        }
        // Longer alias substring match next
        val candidates = all.flatMap { flow ->
            flow.names.map { alias -> flow to alias.lowercase() }
        }.sortedByDescending { it.second.length }
        for ((flow, alias) in candidates) {
            if (q.contains(alias)) return flow
        }
        return null
    }

    /**
     * Normalized (" ... ") text-এর ভেতর কোন AppFlow-এর নাম আছে কিনা খুঁজে বের করে।
     * Return: Pair(AppFlow, matchedAlias)
     */
    fun findInNormalized(normalizedSpaced: String, kindFilter: Kind? = null): Pair<AppFlow, String>? {
        val pairs = all
            .filter { kindFilter == null || it.kind == kindFilter }
            .flatMap { flow -> flow.names.map { alias -> flow to alias.lowercase() } }
            .sortedByDescending { it.second.length }
        for ((flow, alias) in pairs) {
            if (normalizedSpaced.contains(" $alias ")) return flow to alias
        }
        return null
    }
}
