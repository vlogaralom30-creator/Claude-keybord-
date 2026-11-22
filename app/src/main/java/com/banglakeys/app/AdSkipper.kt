package com.banglakeys.app

/**
 * YouTube ও ভেরিফাইড বিজ্ঞাপনের (Ad-SDK) Skip / × Close বাটন সনাক্ত করার জন্য নিরাপদ,
 * false-positive মুক্ত ইঞ্জিন।
 *
 * মূল নিরাপত্তা নীতি:
 *  ১) কোনো অ্যাপের রেজিস্ট্রেশন, লগইন, OTP, ফর্ম বা অনবোর্ডিং স্ক্রিনের "Skip" বাটনে কখনোই ক্লিক করে না।
 *  ২) কাউন্টডাউন ("Skip in 4", "5s") শেষ হওয়ার আগে ক্লিক করে না।
 *  ৩) গেম/অ্যাপের ক্ষেত্রে অবশ্যই verified Ad-SDK প্রমাণ বা বিজ্ঞাপন লেবেল থাকতে হবে এবং "Install/Download" ফাঁদ এড়িয়ে চলে।
 */
object AdSkipper {

    data class AdNode(
        val id: Int,
        val viewId: String,
        val className: String,
        val text: String,
        val contentDescription: String,
        val hintText: String,
        val isClickable: Boolean,
        val isEnabled: Boolean,
        val isVisible: Boolean,
        val isEditable: Boolean,
        val isFocused: Boolean,
        val isPassword: Boolean,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val parentId: Int
    ) {
        val label: String get() = contentDescription.ifBlank { text }
        val width: Int get() = (right - left).coerceAtLeast(0)
        val height: Int get() = (bottom - top).coerceAtLeast(0)
    }

    data class SkipMatch(
        val nodeId: Int,
        val sourceLabel: String,
        val reason: String,
        val isYouTube: Boolean
    )

    fun isBlockedPackage(pkg: String): Boolean {
        val low = pkg.trim().lowercase()
        if (low.isEmpty()) return true
        if (low.contains("launcher") || low.contains("inputmethod") || low.contains("keyboard")) return true
        if (low in BLOCKED_PACKAGES) return true
        return low == "com.example" ||
            low.startsWith("com.aistudio.banglakeys") ||
            low.startsWith("com.banglakeys") ||
            low.startsWith("com.android.systemui") ||
            low.startsWith("com.android.settings")
    }

    fun findSkipTarget(
        packageName: String,
        screenW: Int,
        screenH: Int,
        nodes: List<AdNode>,
        allowGameAds: Boolean
    ): SkipMatch? {
        if (nodes.isEmpty() || screenW <= 0 || screenH <= 0) return null
        if (isBlockedPackage(packageName)) return null

        val visibleNodes = nodes.filter { it.isVisible }
        if (visibleNodes.isEmpty()) return null

        val pkgLow = packageName.trim().lowercase()
        val isYouTubeApp = pkgLow in YOUTUBE_PACKAGES
        val byId = nodes.associateBy { it.id }

        // ১) হার্ড সিকিউরিটি গার্ড: পাসওয়ার্ড / রেজিস্ট্রেশন / লগইন / ফর্ম স্ক্রিন পুরোপুরি ব্লক
        if (hasAuthOrFormScreen(visibleNodes, isYouTubeApp)) {
            return null
        }

        // ২) YouTube ভিডিও বিজ্ঞাপন সনাক্তকরণ
        val ytMatch = findYouTubeAdSkip(pkgLow, isYouTubeApp, visibleNodes, byId)
        if (ytMatch != null) return ytMatch

        // ৩) গেম ও অ্যাপের Verified Ad-SDK ওভারলে (Skip / ×)
        if (!allowGameAds) return null
        if (hasOnboardingSignals(visibleNodes)) return null

        return findVerifiedAdOverlayClose(visibleNodes, byId, screenW, screenH)
    }

    private fun hasAuthOrFormScreen(nodes: List<AdNode>, isYouTubeApp: Boolean): Boolean {
        for (n in nodes) {
            if (n.isPassword) return true

            val clsLow = n.className.lowercase()
            val isInputBox = n.isEditable || clsLow.contains("edittext") || clsLow.contains("autocompletetextview")
            if (isInputBox) {
                // গেম/অ্যাপ ওভারলেতে কোনো EditText থাকলে সেটি ফর্ম বা সার্চ; কখনোই ad স্কিপ করবে না।
                // YouTube-এ কেবল focused বা comment/search ফিল্ড ছাড়া, explicit auth না থাকলে গার্ড করি:
                if (!isYouTubeApp || n.isFocused) return true
                val combinedInput = "${n.hintText} ${n.contentDescription} ${n.text} ${n.viewId}".lowercase()
                if (AUTH_FORM_KEYWORDS.any { combinedInput.contains(it) }) return true
            }

            val combined = "${n.text} ${n.contentDescription} ${n.hintText}".trim().lowercase()
            if (combined.isNotEmpty() && AUTH_FORM_KEYWORDS.any { containsKeyword(combined, it) }) {
                return true
            }
        }
        return false
    }

    private fun hasOnboardingSignals(nodes: List<AdNode>): Boolean {
        for (n in nodes) {
            val combined = "${n.text} ${n.contentDescription}".trim().lowercase()
            if (combined.isEmpty()) continue
            if (ONBOARDING_KEYWORDS.any { containsKeyword(combined, it) }) return true
        }
        return false
    }

    private fun findYouTubeAdSkip(
        pkgLow: String,
        isYouTubeApp: Boolean,
        nodes: List<AdNode>,
        byId: Map<Int, AdNode>
    ): SkipMatch? {
        val hasYtAdContext = nodes.any { n ->
            val vid = n.viewId.lowercase()
            val txt = "${n.text} ${n.contentDescription}".lowercase()
            YT_AD_CONTEXT_IDS.any { vid.contains(it) } ||
                YT_AD_CONTEXT_TEXTS.any { txt.contains(it) }
        }

        if (!isYouTubeApp && !hasYtAdContext) return null

        // ধাপ ক: YouTube-এর নির্দিষ্ট Skip Ad view ID সরাসরি চেক
        for (n in nodes) {
            if (!n.isEnabled || isEmptyBounds(n)) continue
            val vid = n.viewId.lowercase()
            if (YT_SKIP_IDS.any { vid.endsWith(it) || vid.contains(":id/$it") || vid.contains(it) }) {
                val lbl = combinedNodeLabel(n, byId)
                if (isCountdownLabel(lbl)) continue
                if (isForbiddenCta(lbl)) continue
                val clickTarget = resolveClickable(n, byId) ?: continue
                return SkipMatch(
                    nodeId = clickTarget.id,
                    sourceLabel = if (isYouTubeApp) "YouTube" else pkgLow,
                    reason = "yt_skip_id:$vid",
                    isYouTube = true
                )
            }
        }

        // ধাপ খ: Ad context থাকলে (যেমন ব্রাউজার বা পরিবর্তিত UI) Skip টেক্সট বাটন
        if (!hasYtAdContext) return null
        for (n in nodes) {
            if (!n.isEnabled || isEmptyBounds(n)) continue
            val rawLbl = n.label.trim()
            if (rawLbl.isEmpty()) continue
            val low = rawLbl.lowercase()
            if (isForbiddenSkipPhrase(low) || isForbiddenCta(low) || isCountdownLabel(low)) continue
            if (low in EXACT_YT_SKIP_LABELS) {
                val clickTarget = resolveClickable(n, byId) ?: continue
                return SkipMatch(
                    nodeId = clickTarget.id,
                    sourceLabel = if (isYouTubeApp) "YouTube" else pkgLow,
                    reason = "yt_skip_label:$low",
                    isYouTube = true
                )
            }
        }
        return null
    }

    private fun findVerifiedAdOverlayClose(
        nodes: List<AdNode>,
        byId: Map<Int, AdNode>,
        screenW: Int,
        screenH: Int
    ): SkipMatch? {
        // প্রথমেই শক্তিশালী Ad-SDK বা বিজ্ঞাপন প্রকাশনার প্রমাণ থাকতে হবে
        val sdkProof = nodes.any { n ->
            val vid = n.viewId.lowercase()
            val cls = n.className.lowercase()
            val txt = "${n.text} ${n.contentDescription}".lowercase()
            AD_SDK_ID_MARKERS.any { vid.contains(it) || cls.contains(it) } ||
                AD_DISCLOSURE_TEXTS.any { txt.contains(it) }
        }
        if (!sdkProof) return null

        // ১) Ad-SDK-এর সুনির্দিষ্ট Close / Skip View ID অগ্রাধিকার পায়
        for (n in nodes) {
            if (!n.isEnabled || isEmptyBounds(n)) continue
            val vid = n.viewId.lowercase()
            if (AD_SDK_CLOSE_IDS.any { vid.contains(it) }) {
                val lbl = combinedNodeLabel(n, byId)
                if (isCountdownLabel(lbl) || isForbiddenCta(lbl) || isForbiddenSkipPhrase(lbl)) continue
                val clickTarget = resolveClickable(n, byId) ?: continue
                return SkipMatch(
                    nodeId = clickTarget.id,
                    sourceLabel = "Ad Overlay",
                    reason = "sdk_close_id:$vid",
                    isYouTube = false
                )
            }
        }

        // ২) স্ক্রিনের উপরের কোণায় থাকা Skip Ad / Close / × আইকন বাটন
        val topLimitY = (screenH * 0.36f).toInt()
        val maxCloseW = (screenW * 0.28f).toInt()

        for (n in nodes) {
            if (!n.isEnabled || isEmptyBounds(n)) continue
            val raw = n.label.trim()
            if (raw.isEmpty()) continue
            val low = raw.lowercase()
            if (isForbiddenCta(low) || isForbiddenSkipPhrase(low) || isCountdownLabel(low)) continue

            val isExplicitAdSkip = low in EXACT_AD_SKIP_LABELS
            val isCloseSymbol = raw in CLOSE_SYMBOLS || (low == "x" && n.top <= topLimitY && n.width <= maxCloseW)
            val isCornerCloseWord = (low == "close" || low == "dismiss" || low == "skip" || low == "close ad") &&
                n.top <= topLimitY

            if (isExplicitAdSkip || isCloseSymbol || isCornerCloseWord) {
                val clickTarget = resolveClickable(n, byId) ?: continue
                // CTA বা বড় অ্যাড ব্যানার ভুল করে ক্লিক না করার জন্য আকৃতি যাচাই
                if (!isExplicitAdSkip && clickTarget.width > (screenW * 0.45f).toInt() && clickTarget.height > (screenH * 0.16f).toInt()) {
                    continue
                }
                return SkipMatch(
                    nodeId = clickTarget.id,
                    sourceLabel = "Ad Overlay",
                    reason = "sdk_close_label:$low",
                    isYouTube = false
                )
            }
        }

        return null
    }

    private fun resolveClickable(node: AdNode, byId: Map<Int, AdNode>): AdNode? {
        var cur: AdNode? = node
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable && cur.isEnabled && !isEmptyBounds(cur)) {
                val lbl = "${cur.text} ${cur.contentDescription}".lowercase()
                if (!isForbiddenCta(lbl)) return cur
                return null
            }
            cur = byId[cur.parentId]
            depth++
        }
        return if (!isEmptyBounds(node) && node.isEnabled) node else null
    }

    private fun combinedNodeLabel(n: AdNode, byId: Map<Int, AdNode>): String {
        val self = "${n.text} ${n.contentDescription}".trim()
        if (self.isNotEmpty()) return self.lowercase()
        val parent = byId[n.parentId]
        return "${parent?.text.orEmpty()} ${parent?.contentDescription.orEmpty()}".trim().lowercase()
    }

    private fun isEmptyBounds(n: AdNode): Boolean =
        n.right <= n.left || n.bottom <= n.top

    private fun isCountdownLabel(low: String): Boolean {
        if (low.isEmpty()) return false
        if (low.matches(Regex("^\\d+\\s*(s|sec|সেকেন্ড)?$"))) return true
        return low.contains("skip in") ||
            low.contains("reward in") ||
            low.contains("seconds remaining") ||
            low.contains("অপেক্ষা") ||
            Regex("\\b\\d+\\s*(s|sec)\\b").containsMatchIn(low)
    }

    private fun isForbiddenSkipPhrase(low: String): Boolean =
        FORBIDDEN_SKIP_PHRASES.any { low.contains(it) }

    private fun isForbiddenCta(low: String): Boolean =
        FORBIDDEN_CTA_WORDS.any { containsKeyword(low, it) }

    private fun containsKeyword(haystack: String, needle: String): Boolean {
        if (!haystack.contains(needle)) return false
        if (needle.any { it in '\u0980'..'\u09FF' }) return true
        return Regex("(?<![a-z0-9])${Regex.escape(needle)}(?![a-z0-9])").containsMatchIn(haystack)
    }

    private val BLOCKED_PACKAGES = setOf(
        "com.android.vending",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.android.phone",
        "com.android.incallui"
    )

    private val YOUTUBE_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.kids",
        "com.google.android.apps.youtube.music",
        "app.revanced.android.youtube",
        "com.vanced.android.youtube"
    )

    private val YT_SKIP_IDS = listOf(
        "skip_ad_button_container",
        "skip_ad_button_text",
        "modern_skip_ad_button",
        "skip_ad_button"
    )

    private val YT_AD_CONTEXT_IDS = listOf(
        "ad_progress_text",
        "ad_badge",
        "ad_overlay",
        "player_learn_more_button",
        "visit_advertiser"
    )

    private val YT_AD_CONTEXT_TEXTS = listOf(
        "sponsored",
        "video will play after",
        "ad ·",
        "বিজ্ঞাপন"
    )

    private val EXACT_YT_SKIP_LABELS = setOf(
        "skip",
        "skip ad",
        "skip ads",
        "এড়িয়ে যান",
        "এড়িয়ে যান",
        "স্কিপ"
    )

    private val EXACT_AD_SKIP_LABELS = setOf(
        "skip ad",
        "skip ads",
        "close ad",
        "skip video",
        "এড়িয়ে যান",
        "এড়িয়ে যান"
    )

    private val CLOSE_SYMBOLS = setOf("×", "✕", "✖", "╳", "ⓧ")

    private val AUTH_FORM_KEYWORDS = listOf(
        "sign up", "signup", "sign in", "signin", "register", "registration",
        "create account", "log in", "login", "password", "forgot password",
        "otp", "verification code", "verify code", "enter code",
        "phone number", "email address", "full name", "date of birth",
        "terms of service", "privacy policy", "continue with google",
        "নিবন্ধন", "রেজিস্টার", "সাইন আপ", "লগইন", "অ্যাকাউন্ট", "পাসওয়ার্ড", "পাসওয়ার্ড", "ওটিপি"
    )

    private val ONBOARDING_KEYWORDS = listOf(
        "get started", "onboarding", "tutorial", "choose your", "select your",
        "personalize", "skip setup", "skip intro", "skip tutorial", "skip for now"
    )

    private val FORBIDDEN_SKIP_PHRASES = listOf(
        "skip for now", "skip setup", "skip intro", "skip tutorial",
        "skip registration", "skip step", "do it later", "not now", "maybe later",
        "এখন নয়", "পরে করুন"
    )

    private val FORBIDDEN_CTA_WORDS = listOf(
        "install", "download", "learn more", "shop now", "visit site", "play now",
        "google play", "open app", "get app"
    )

    private val AD_SDK_ID_MARKERS = listOf(
        "com.google.android.gms.ads", "admob", "applovin", "al_close", "al_skip",
        "unityads", "unity3d.services.ads", "mbridge", "mintegral",
        "ironsource", "vungle", "pangle", "openadsdk", "tt_ad", "tt_video",
        "chartboost", "ia_tv", "fyber", "startapp",
        "ad_container", "interstitial_ad", "rewarded_ad", "ad_overlay", "ad_choices", "adchoices"
    )

    private val AD_DISCLOSURE_TEXTS = listOf(
        "test ad", "adchoices", "ads by google", "google ads", "unity ads",
        "applovin", "advertisement", "reward granted", "you can now close", "sponsored",
        "বিজ্ঞাপন"
    )

    private val AD_SDK_CLOSE_IDS = listOf(
        "ad_skip", "ad_close", "skip_btn", "skip_button",
        "ia_tv_close", "al_close", "al_skip",
        "mbridge_windown_close", "tt_video_ad_close", "tt_titlebar_close",
        "interstitial_close", "close_button", "btn_close"
    )
}
