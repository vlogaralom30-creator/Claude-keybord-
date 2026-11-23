package com.banglakeys.app

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Pure offline Accessibility screen-layout analyzer & spatial UI resolver.
 *
 * ডিজাইন নীতি:
 *  - কোনো ক্লাউড কল ছাড়াই স্ক্রিনের UI নোড, বাটন, ইনপুট বক্স, ভিডিও টাইটেল ও আইকন সম্পর্ক বোঝে।
 *  - আধুনিক অ্যাপে Send/Search বাটনগুলোতে লেখা না থাকলেও EditText-এর ডান পাশের companion আইকন
 *    এবং viewId/bounds বিশ্লেষণ করে নির্ভুলভাবে খুঁজে বের করে।
 *  - Pure immutable [UiNodeInfo] মডেলের ওপর চলায় Robolectric-এ ১০০% টেস্টযোগ্য।
 */
object ScreenInspector {

    data class UiNodeInfo(
        val id: Int,
        val parentId: Int,
        val packageName: String = "",
        val viewId: String = "",
        val className: String = "",
        val text: String = "",
        val contentDescription: String = "",
        val hintText: String = "",
        val isClickable: Boolean = false,
        val isEditable: Boolean = false,
        val isFocused: Boolean = false,
        val isEnabled: Boolean = true,
        val isVisible: Boolean = true,
        val isScrollable: Boolean = false,
        val left: Int = 0,
        val top: Int = 0,
        val right: Int = 0,
        val bottom: Int = 0
    ) {
        val width: Int get() = (right - left).coerceAtLeast(0)
        val height: Int get() = (bottom - top).coerceAtLeast(0)
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
    }

    private val VIEW_COUNT_REGEX = Regex(
        """(\d[\d.,]*)\s*(k|m|b|lakh|crore|হাজার|লাখ|কোটি)?\s*(views|view|ভিউ)""",
        RegexOption.IGNORE_CASE
    )

    private val STOP_QUERY_WORDS = setOf(
        "the", "a", "an", "and", "or", "in", "on", "for", "to", "of", "by", "with",
        "video", "videos", "song", "songs", "gaan", "natok", "play", "chalao", "dekhao",
        "koro", "kor", "ta", "ti", "ekta", "ei", "ভিডিও", "গান", "চালাও", "দেখাও", "করো"
    )

    private val BANGLA_VIDEO_EQUIV: Map<String, List<String>> = mapOf(
        "natok" to listOf("natok", "নাটক", "drama"),
        "gaan" to listOf("gaan", "gan", "song", "music", "গান"),
        "waz" to listOf("waz", "waj", "ওয়াজ", "ওয়াজ"),
        "news" to listOf("news", "khobor", "খবর", "নিউজ"),
        "cartoon" to listOf("cartoon", "কার্টুন"),
        "cricket" to listOf("cricket", "ক্রিকেট", "match"),
        "football" to listOf("football", "ফুটবল"),
        "movie" to listOf("movie", "film", "cinema", "মুভি", "সিনেমা"),
        "funny" to listOf("funny", "comedy", "hasir", "ফানি", "কমেডি", "হাসির"),
        "islamic" to listOf("islamic", "ইসলামিক", "surah", "quran"),
        "recipe" to listOf("recipe", "ranna", "রান্না", "রেসিপি")
    )

    private val NON_VIDEO_UI_KEYWORDS = setOf(
        "search youtube", "search facebook", "search", "subscribe", "subscribed",
        "home", "shorts", "subscriptions", "library", "you", "notifications",
        "account", "back", "navigate up", "more options", "url bar", "new tab"
    )

    fun capture(root: AccessibilityNodeInfo?): List<UiNodeInfo> {
        if (root == null) return emptyList()
        val out = ArrayList<UiNodeInfo>(128)
        var nextId = 0

        fun walk(node: AccessibilityNodeInfo?, parentId: Int, depth: Int) {
            if (node == null || depth > 42 || out.size >= 600) return
            val myId = nextId++
            val rect = Rect()
            node.getBoundsInScreen(rect)
            out.add(
                UiNodeInfo(
                    id = myId,
                    parentId = parentId,
                    packageName = node.packageName?.toString().orEmpty(),
                    viewId = node.viewIdResourceName.orEmpty(),
                    className = node.className?.toString().orEmpty(),
                    text = node.text?.toString().orEmpty(),
                    contentDescription = node.contentDescription?.toString().orEmpty(),
                    hintText = if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString().orEmpty() else "",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isFocused = node.isFocused,
                    isEnabled = node.isEnabled,
                    isVisible = node.isVisibleToUser,
                    isScrollable = node.isScrollable,
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom
                )
            )
            for (i in 0 until node.childCount) {
                walk(node.getChild(i), myId, depth + 1)
            }
        }
        walk(root, -1, 0)
        return out
    }

    /** Exact index দিয়ে live tree থেকে [AccessibilityNodeInfo] খুঁজে আনে। */
    fun resolveLiveNode(root: AccessibilityNodeInfo?, targetId: Int): AccessibilityNodeInfo? {
        if (root == null || targetId < 0) return null
        var counter = 0
        var result: AccessibilityNodeInfo? = null
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || result != null || depth > 42 || counter > 600) return
            val id = counter++
            if (id == targetId) {
                result = node
                return
            }
            for (i in 0 until node.childCount) {
                walk(node.getChild(i), depth + 1)
            }
        }
        walk(root, 0)
        return result
    }

    /**
     * Text / contentDescription / hint / viewId মিলিয়ে সবচেয়ে উপযুক্ত UI নোড খুঁজে দেয়।
     */
    fun findBestByHints(
        nodes: List<UiNodeInfo>,
        hints: List<String>,
        preferClickable: Boolean = true
    ): UiNodeInfo? {
        val cleanHints = hints.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (cleanHints.isEmpty()) return null

        var best: UiNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        for (n in nodes) {
            if (!n.isVisible || !n.isEnabled) continue
            val t = n.text.trim().lowercase()
            val d = n.contentDescription.trim().lowercase()
            val h = n.hintText.trim().lowercase()
            val vidShort = n.viewId.substringAfterLast('/').lowercase()

            var score = 0
            for (hint in cleanHints) {
                when {
                    d == hint || t == hint -> score = maxOf(score, 120)
                    h == hint -> score = maxOf(score, 100)
                    vidShort == hint -> score = maxOf(score, 95)
                    d.contains(hint) || t.contains(hint) -> score = maxOf(score, 75)
                    h.contains(hint) -> score = maxOf(score, 60)
                    vidShort.contains(hint) -> score = maxOf(score, 55)
                }
            }
            if (score <= 0) continue
            if (preferClickable && n.isClickable) score += 25
            if (n.isEditable && !preferClickable) score += 20
            if (score > bestScore) {
                bestScore = score
                best = n
            }
        }
        return best
    }

    /**
     * Send / Search / Go / Post বাটন খোঁজা — লেখা বা contentDescription না থাকলেও কাজ করে!
     * ধাপ:
     *  1) viewId বা label-এ send/submit/post/search/arrow থাকলে তা বেছে নেয় (input box বাদে)
     *  2) ইনপুট বক্সের একই সারিতে (ডান পাশে) থাকা icon button / ImageView / Button খুঁজে বের করে!
     */
    fun findCompanionActionButton(
        nodes: List<UiNodeInfo>,
        inputNode: UiNodeInfo?,
        actionHints: List<String> = emptyList(),
        screenWidth: Int = 1080
    ): UiNodeInfo? {
        val byId = nodes.associateBy { it.id }
        val defaultHints = (actionHints + listOf(
            "send", "submit", "post", "comment_send", "send_button", "send_btn", "arrow",
            "search", "go", "done", "paper_plane", "পাঠান", "পোস্ট"
        )).map { it.lowercase() }.distinct()

        // Pass 1: Semantic label / viewId match on non-editable clickable buttons
        var bestSemantic: UiNodeInfo? = null
        var bestSemanticScore = Int.MIN_VALUE

        for (n in nodes) {
            if (!n.isVisible || !n.isEnabled || n.isEditable) continue
            if (n.className.contains("EditText", ignoreCase = true)) continue
            if (!n.isClickable && !hasClickableAncestor(n, byId)) continue

            val d = n.contentDescription.trim().lowercase()
            val t = n.text.trim().lowercase()
            val vid = n.viewId.substringAfterLast('/').lowercase()

            // "Voice search" বা "Attach" যেন ভুল করে "Send" না হয়
            if (d.contains("voice") || d.contains("mic") || d.contains("attach") ||
                d.contains("camera") || d.contains("emoji") || vid.contains("mic") || vid.contains("emoji")
            ) {
                continue
            }

            var score = 0
            for (h in defaultHints) {
                when {
                    d == h || t == h -> score = maxOf(score, 120)
                    vid == h -> score = maxOf(score, 110)
                    d.contains(h) || t.contains(h) -> score = maxOf(score, 85)
                    vid.contains(h) -> score = maxOf(score, 80)
                }
            }
            if (score <= 0) continue

            if (inputNode != null && shareAncestor(inputNode, n, byId, maxHops = 4)) {
                score += 45
            }
            if (inputNode != null && kotlin.math.abs(n.centerY - inputNode.centerY) <= (inputNode.height.coerceAtLeast(80) * 1.4f)) {
                score += 30
            }
            if (score > bestSemanticScore) {
                bestSemanticScore = score
                bestSemantic = n
            }
        }
        if (bestSemantic != null) return bestSemantic

        // Pass 2: Unlabeled icon fallback right beside or inside same row container as inputNode
        if (inputNode == null) return null
        val rowToleranceY = (inputNode.height.coerceAtLeast(90) * 1.35f).toInt()
        val maxIconSize = (screenWidth * 0.30f).toInt().coerceAtLeast(220)

        var bestSpatial: UiNodeInfo? = null
        var bestSpatialScore = Int.MIN_VALUE

        for (n in nodes) {
            if (n.id == inputNode.id || !n.isVisible || !n.isEnabled || n.isEditable) continue
            if (n.className.contains("EditText", ignoreCase = true)) continue
            if (!n.isClickable) continue
            if (n.width <= 0 || n.height <= 0 || n.width > maxIconSize || n.height > maxIconSize) continue

            val desc = (n.contentDescription + " " + n.viewId).lowercase()
            if (desc.contains("emoji") || desc.contains("sticker") || desc.contains("attach") ||
                desc.contains("camera") || desc.contains("mic") || desc.contains("voice") ||
                desc.contains("clear") || desc.contains("close")
            ) continue

            val sameRow = kotlin.math.abs(n.centerY - inputNode.centerY) <= rowToleranceY
            val toRightOfInput = n.centerX >= inputNode.centerX && n.right >= inputNode.right - (screenWidth * 0.12f)
            if (!sameRow || !toRightOfInput) continue

            var score = 50
            if (shareAncestor(inputNode, n, byId, maxHops = 4)) score += 60
            // ডানদিকের সবচেয়ে প্রান্তের বাটন প্রায়ই Send হয়
            score += (n.centerX * 20) / screenWidth.coerceAtLeast(1)
            if (n.className.contains("Button", ignoreCase = true) || n.className.contains("Image", ignoreCase = true)) {
                score += 15
            }
            if (score > bestSpatialScore) {
                bestSpatialScore = score
                bestSpatial = n
            }
        }
        return bestSpatial
    }

    /**
     * স্ক্রিনে দৃশ্যমান ভিডিও কার্ডগুলোর মধ্যে [query]-এর সাথে সবচেয়ে মিলে যাওয়া ভিডিও খুঁজে দেয়।
     * ইউজার যখন YouTube/Facebook/TikTok/Chrome-এ স্ক্রল করতে করতে দৃশ্যমান ভিডিওর নাম বলে প্লে করতে চায়,
     * তখন সরাসরি সেই ভিডিওটাই ট্যাপ করে — অকারণে নতুন সার্চ খুলে দেয় না।
     */
    fun findMatchingVideoOnScreen(
        nodes: List<UiNodeInfo>,
        query: String,
        screenWidth: Int = 1080,
        screenHeight: Int = 1920
    ): UiNodeInfo? {
        val queryTokens = tokenizeForMatch(query)
        if (queryTokens.isEmpty()) return null

        val expandedTokens = queryTokens.flatMap { tok ->
            BANGLA_VIDEO_EQUIV[tok] ?: listOf(tok)
        }.distinct()

        val byId = nodes.associateBy { it.id }
        var bestNode: UiNodeInfo? = null
        var bestScore = 0.0

        for (n in nodes) {
            if (!n.isVisible || !n.isEnabled || n.isEditable) continue
            if (n.bottom <= (screenHeight * 0.08f) || n.top >= (screenHeight * 0.92f)) continue
            if (n.width <= 0 || n.height <= 0) continue

            val combined = buildString {
                append(n.contentDescription)
                if (n.contentDescription.isNotEmpty() && n.text.isNotEmpty()) append(' ')
                append(n.text)
            }.trim()
            if (combined.length < 4) continue

            val low = combined.lowercase()
            if (NON_VIDEO_UI_KEYWORDS.any { low == it }) continue
            // URL bar বা top search box বাদ
            val vid = n.viewId.lowercase()
            if (vid.contains("url_bar") || vid.contains("search_box") || vid.contains("search_edit")) continue

            val nodeTokens = tokenizeForMatch(combined)
            if (nodeTokens.isEmpty()) continue

            var matchedQueryTerms = 0
            for (qt in queryTokens) {
                val variants = BANGLA_VIDEO_EQUIV[qt] ?: listOf(qt)
                val hit = variants.any { v ->
                    low.contains(v) || nodeTokens.any { nt -> nt.contains(v) || v.contains(nt) }
                }
                if (hit) matchedQueryTerms++
            }
            if (matchedQueryTerms == 0) continue

            val coverage = matchedQueryTerms.toDouble() / queryTokens.size.toDouble()
            // কমপক্ষে ৬০% শব্দ না মিললে ভুল ভিডিও ক্লিক করব না (১ শব্দের query হলে ১০০%)
            val minRatio = if (queryTokens.size == 1) 1.0 else 0.55
            if (coverage < minRatio) continue

            var score = coverage * 100.0
            if (low.contains(query.trim().lowercase())) score += 40.0
            if (expandedTokens.any { it.length >= 4 && low.contains(it) }) score += 10.0
            if (VIEW_COUNT_REGEX.containsMatchIn(low) || low.contains("play video")) score += 20.0
            if (vid.contains("title") || vid.contains("video") || vid.contains("caption")) score += 15.0
            if (n.isClickable || hasClickableAncestor(n, byId)) score += 15.0

            if (score > bestScore) {
                bestScore = score
                bestNode = n
            }
        }
        return bestNode
    }

    /** স্ক্রিনের দৃশ্যমান ভিডিও কার্ডগুলোকে উপর থেকে নিচে সাজিয়ে index (0, 1, 2...) অনুযায়ী রিটার্ন করে। */
    fun findOrdinalVideoCard(
        nodes: List<UiNodeInfo>,
        index: Int,
        screenHeight: Int = 1920
    ): UiNodeInfo? {
        if (index < 0) return null
        val byId = nodes.associateBy { it.id }
        val candidates = ArrayList<UiNodeInfo>()
        val seenTopY = ArrayList<Int>()

        val sorted = nodes.filter { n ->
            n.isVisible && n.isEnabled && !n.isEditable &&
                n.top >= (screenHeight * 0.12f) && n.bottom <= (screenHeight * 0.90f)
        }.sortedBy { it.top }

        for (n in sorted) {
            val d = n.contentDescription.trim()
            val t = n.text.trim()
            val vid = n.viewId.lowercase()
            val looksLikeVideo =
                (d.isNotEmpty() && (VIEW_COUNT_REGEX.containsMatchIn(d) || d.contains("play video", true))) ||
                    (t.length >= 12 && (vid.contains("title") || vid.contains("video") || vid.contains("caption")) &&
                        (n.isClickable || hasClickableAncestor(n, byId)))
            if (!looksLikeVideo) continue

            // একই কার্ডের ভেতরে title আর description যেন আলাদা item হিসেবে গণনা না হয়
            if (seenTopY.any { kotlin.math.abs(it - n.top) < 120 }) continue
            seenTopY.add(n.top)
            candidates.add(n)
        }
        return candidates.getOrNull(index)
    }

    fun parseViewCount(text: String): Long? {
        val m = VIEW_COUNT_REGEX.find(text) ?: return null
        val num = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val mul = when (m.groupValues[2].lowercase()) {
            "k", "হাজার" -> 1_000.0
            "m" -> 1_000_000.0
            "b" -> 1_000_000_000.0
            "lakh", "লাখ" -> 100_000.0
            "crore", "কোটি" -> 10_000_000.0
            else -> 1.0
        }
        return (num * mul).toLong()
    }

    fun findPrimaryEditable(nodes: List<UiNodeInfo>): UiNodeInfo? {
        val visibleEditables = nodes.filter {
            it.isVisible && it.isEnabled &&
                (it.isEditable || it.className.contains("EditText", ignoreCase = true))
        }
        return visibleEditables.firstOrNull { it.isFocused }
            ?: visibleEditables.maxByOrNull { it.top }
    }

    fun hasClickableAncestor(node: UiNodeInfo, byId: Map<Int, UiNodeInfo>): Boolean {
        var cur: UiNodeInfo? = node
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable) return true
            cur = byId[cur.parentId]
            hops++
        }
        return false
    }

    private fun shareAncestor(
        a: UiNodeInfo,
        b: UiNodeInfo,
        byId: Map<Int, UiNodeInfo>,
        maxHops: Int
    ): Boolean {
        val aAncestors = HashSet<Int>()
        var ca: UiNodeInfo? = a
        var h = 0
        while (ca != null && h <= maxHops) {
            aAncestors.add(ca.id)
            ca = byId[ca.parentId]
            h++
        }
        var cb: UiNodeInfo? = b
        h = 0
        while (cb != null && h <= maxHops) {
            if (cb.id in aAncestors) return true
            cb = byId[cb.parentId]
            h++
        }
        return false
    }

    internal fun tokenizeForMatch(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[,.!?।\"'“”‘’\\-_–—:;()/|#@]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_QUERY_WORDS }
}
