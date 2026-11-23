package com.banglakeys.app

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * Coroutine-ভিত্তিক Multi-step App Flow Runner (Pack G, Pack H, Camera shutter)।
 * Main thread ব্লক না করে 300ms পরপর screen poll করে।
 */
class FlowRunner(private val ctx: Context) {

    companion object {
        private const val POLL_MS = 300L
        private const val SEND_SETTLE_MS = 400L

        private val DEFAULT_INPUT_HINTS = listOf("message", "ask", "type", "প্রশ্ন", "মেসেজ", "লিখুন")
        private val DEFAULT_SEND_HINTS = listOf("send", "send message", "submit", "পাঠান", "পাঠাও")
        private val SHUTTER_HINTS = listOf("shutter", "capture", "take photo", "take picture", "ছবি তুলুন", "ক্যাপচার")
    }

    suspend fun waitFor(timeoutMs: Long, pollMs: Long = POLL_MS, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (condition()) return true
            val rem = deadline - System.currentTimeMillis()
            if (rem <= 0L) return false
            delay(minOf(pollMs, rem))
        }
    }

    private suspend fun <T> waitForValue(
        timeoutMs: Long,
        pollMs: Long = POLL_MS,
        supplier: () -> T?
    ): T? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val v = supplier()
            if (v != null) return v
            val rem = deadline - System.currentTimeMillis()
            if (rem <= 0L) return null
            delay(minOf(pollMs, rem))
        }
    }

    private fun openFlowApp(flow: AppFlow): Boolean {
        val intent = AppResolver.launchIntentForAppFlow(ctx, flow) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launcher: Context = AssistantAccessibilityService.instance ?: ctx
        return try {
            launcher.startActivity(intent)
            true
        } catch (e: Exception) {
            AssistantLog.w("FlowRunner openFlowApp failed: ${flow.key}", e)
            false
        }
    }

    private fun isBlocked(svc: AssistantAccessibilityService): Boolean {
        val pkg = svc.foregroundPackage()
        val blocked = pkg.isNotEmpty() && pkg in AssistantPrefs.blockedPackages(ctx)
        if (blocked) AssistantLog.d("FlowRunner blocked package: $pkg")
        return blocked
    }

    // ------------------------------------------------------------ Pack G (AI chat / type+send)

    suspend fun runAppChat(
        flow: AppFlow?,
        text: String,
        send: Boolean,
        onProgress: (String) -> Unit = {}
    ): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"
        val appLabel = flow?.key ?: "এই app"
        val openTimeout = maxOf(flow?.openTimeoutMs ?: 4000L, AssistantPrefs.appFlowTimeoutMs(ctx))
        val stepTimeout = maxOf(flow?.stepTimeoutMs ?: 4000L, AssistantPrefs.appFlowTimeoutMs(ctx))

        if (flow != null) {
            AssistantLog.d("FlowRunner.runAppChat: launching ${flow.key}, send=$send")
            onProgress("${flow.key} খোলা হচ্ছে...")
            if (!openFlowApp(flow)) {
                return "❌ ${flow.key} অ্যাপটি ফোনে পাইনি"
            }
            val inFg = svc.waitForPackage(flow.packages, openTimeout)
            AssistantLog.d("FlowRunner.runAppChat: waitForPackage(${flow.packages}) -> $inFg (fg=${svc.foregroundPackage()})")
            if (!inFg) {
                return "⏱ ${flow.key} খুলতে সময় বেশি লেগেছে"
            }
            delay(400)
        }

        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"

        onProgress("$appLabel-এ লেখার বক্স খোঁজা হচ্ছে...")
        val hints = flow?.inputHints ?: DEFAULT_INPUT_HINTS
        val inputNode = waitForValue(stepTimeout) {
            svc.findEditable()
                ?: svc.findByHints(hints)
                ?: svc.findLowestClickableAtBottom()
        }
        if (inputNode == null) {
            AssistantLog.d("FlowRunner.runAppChat: input not found for $appLabel (hints=$hints)")
            return "❌ $appLabel-এ লেখার বক্স পাইনি"
        }

        onProgress("লেখা বসানো হচ্ছে...")
        val typed = svc.enterText(inputNode, text)
        AssistantLog.d("FlowRunner.runAppChat: enterText -> $typed")
        if (!typed) {
            return "❌ $appLabel-এ লেখা বসানো যায়নি"
        }

        if (!send) {
            return "⌨️ $appLabel-এ লেখা হয়েছে: $text"
        }

        delay(SEND_SETTLE_MS)
        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"

        onProgress("Send বাটন চাপা হচ্ছে...")
        val sendHints = flow?.sendHints ?: DEFAULT_SEND_HINTS
        val sent = waitFor(2500L) {
            svc.submitFromInput(svc.findEditable() ?: inputNode, sendHints)
        }
        AssistantLog.d("FlowRunner.runAppChat: submitFromInput(send=$sendHints) -> $sent")
        return if (sent) {
            "✅ $appLabel-এ পাঠানো হয়েছে"
        } else {
            "⚠️ $appLabel-এ send বাটন পাইনি (লেখা বসানো আছে)"
        }
    }

    // ------------------------------------------------------------ Chrome / In-App Search / Comments / Screen Actions

    suspend fun runChromeUrlOrSearch(
        urlOrQuery: String,
        isUrl: Boolean,
        onProgress: (String) -> Unit = {}
    ): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"
        val chromeFlow = AppFlows.find("chrome")
            ?: return "❌ Chrome flow পাওয়া যায়নি"
        val openTimeout = maxOf(chromeFlow.openTimeoutMs, AssistantPrefs.appFlowTimeoutMs(ctx))
        val stepTimeout = maxOf(chromeFlow.stepTimeoutMs, AssistantPrefs.appFlowTimeoutMs(ctx))

        if (isUrl) {
            val normalizedUrl = if (urlOrQuery.startsWith("http://", true) || urlOrQuery.startsWith("https://", true)) {
                urlOrQuery
            } else {
                "https://$urlOrQuery"
            }
            onProgress("Chrome-এ $urlOrQuery খোলা হচ্ছে...")
            val installedChromePkg = AssistantConfig.CHROME_PACKAGES.firstOrNull { pkg ->
                runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
            }
            val directIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(normalizedUrl)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (installedChromePkg != null) setPackage(installedChromePkg)
            }
            val launched = runCatching {
                svc.startActivity(directIntent)
                true
            }.getOrDefault(false)
            if (launched) {
                return "🌐 Chrome: $urlOrQuery"
            }
        }

        val currentPkg = svc.foregroundPackage()
        if (currentPkg.isEmpty() || chromeFlow.packages.none { currentPkg.contains(it, true) }) {
            onProgress("Chrome খোলা হচ্ছে...")
            if (!openFlowApp(chromeFlow)) {
                return "❌ Chrome অ্যাপটি ফোনে পাইনি"
            }
            if (!svc.waitForPackage(chromeFlow.packages, openTimeout)) {
                return "⏱ Chrome খুলতে সময় বেশি লেগেছে"
            }
            delay(350)
        }
        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"

        onProgress("Chrome address bar খোঁজা হচ্ছে...")
        var addrInput = svc.findEditable()
        if (addrInput == null) {
            waitFor(stepTimeout) {
                svc.clickByHints(chromeFlow.searchHints)
            }
            delay(300)
            addrInput = waitForValue(stepTimeout) {
                svc.findEditable()
            }
        }
        if (addrInput == null) {
            val fallbackSearch = Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse(
                    if (isUrl) "https://$urlOrQuery"
                    else "https://www.google.com/search?q=" + android.net.Uri.encode(urlOrQuery)
                )
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ok = runCatching {
                svc.startActivity(fallbackSearch)
                true
            }.getOrDefault(false)
            return if (ok) {
                if (isUrl) "🌐 Chrome: $urlOrQuery" else "🔍 Chrome: $urlOrQuery"
            } else {
                "⚠️ Chrome-এ address bar পাইনি"
            }
        }

        onProgress("লেখা বসানো হচ্ছে...")
        if (!svc.enterText(addrInput, urlOrQuery)) {
            return "⚠️ Chrome-এ লেখা বসানো যায়নি"
        }
        delay(250)
        onProgress("Search / Go চাপা হচ্ছে...")
        val submitted = waitFor(2000L) {
            svc.submitFromInput(svc.findEditable() ?: addrInput, chromeFlow.sendHints)
        }
        return if (submitted) {
            if (isUrl) "🌐 Chrome: $urlOrQuery" else "🔍 Chrome সার্চ: $urlOrQuery"
        } else {
            "⚠️ Chrome-এ Go/Search চাপা যায়নি (লেখা বসানো আছে)"
        }
    }

    suspend fun runInAppSearch(
        flow: AppFlow?,
        query: String,
        onProgress: (String) -> Unit = {}
    ): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"
        val appLabel = flow?.key ?: "স্ক্রিন"
        val openTimeout = maxOf(flow?.openTimeoutMs ?: 4000L, AssistantPrefs.appFlowTimeoutMs(ctx))
        val stepTimeout = maxOf(flow?.stepTimeoutMs ?: 4000L, AssistantPrefs.appFlowTimeoutMs(ctx))

        if (flow != null) {
            val currentPkg = svc.foregroundPackage()
            if (currentPkg.isEmpty() || flow.packages.none { currentPkg.contains(it, true) }) {
                onProgress("${flow.key} খোলা হচ্ছে...")
                if (!openFlowApp(flow)) {
                    return "❌ ${flow.key} অ্যাপটি ফোনে পাইনি"
                }
                if (!svc.waitForPackage(flow.packages, openTimeout)) {
                    return "⏱ ${flow.key} খুলতে সময় বেশি লেগেছে"
                }
                delay(450)
            }
        }
        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"

        onProgress("$appLabel-এ Search খোঁজা হচ্ছে...")
        val searchHints = flow?.searchHints?.ifEmpty { null }
            ?: listOf("search", "search youtube", "search facebook", "discover", "সার্চ", "খুঁজুন")
        var inputNode = svc.findEditable()
        if (inputNode == null) {
            val clickedSearch = waitFor(stepTimeout) {
                svc.clickByHints(searchHints)
            }
            if (!clickedSearch) {
                return "⚠️ $appLabel-এ search বাটন পাইনি"
            }
            delay(350)
            inputNode = waitForValue(stepTimeout) { svc.findEditable() }
        }
        if (inputNode == null) {
            return "⚠️ $appLabel-এ search বক্স পাইনি"
        }

        onProgress("সার্চ লেখা বসানো হচ্ছে...")
        if (!svc.enterText(inputNode, query)) {
            return "⚠️ $appLabel-এ লেখা বসানো যায়নি"
        }
        delay(250)
        onProgress("সার্চ করা হচ্ছে...")
        val submitted = waitFor(2500L) {
            svc.submitFromInput(svc.findEditable() ?: inputNode, flow?.sendHints ?: listOf("search", "go", "submit"))
        }
        return if (submitted) {
            "🔍 $appLabel সার্চ: $query"
        } else {
            "⚠️ $appLabel-এ search submit করা যায়নি"
        }
    }

    suspend fun runAppComment(
        flow: AppFlow?,
        text: String?,
        onProgress: (String) -> Unit = {}
    ): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"
        val appLabel = flow?.key ?: AssistantMemory.appKeyFromPackage(svc.foregroundPackage()) ?: "অ্যাপ"
        val openTimeout = maxOf(flow?.openTimeoutMs ?: 4000L, AssistantPrefs.appFlowTimeoutMs(ctx))
        val stepTimeout = maxOf(flow?.stepTimeoutMs ?: 4000L, AssistantPrefs.appFlowTimeoutMs(ctx))

        if (flow != null) {
            val currentPkg = svc.foregroundPackage()
            if (currentPkg.isEmpty() || flow.packages.none { currentPkg.contains(it, true) }) {
                onProgress("${flow.key} খোলা হচ্ছে...")
                if (!openFlowApp(flow)) {
                    return "❌ ${flow.key} অ্যাপটি ফোনে পাইনি"
                }
                if (!svc.waitForPackage(flow.packages, openTimeout)) {
                    return "⏱ ${flow.key} খুলতে সময় বেশি লেগেছে"
                }
                delay(400)
            }
        }
        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"

        val commentBoxHints = listOf("add a comment", "add comment", "write a comment", "comment", "কমেন্ট")
        var commentInput = svc.findEditable()
        if (commentInput == null) {
            onProgress("$appLabel-এ কমেন্ট বাটন খোঁজা হচ্ছে...")
            val opened = waitFor(stepTimeout) {
                svc.clickByHints(listOf("comments", "comment", "add a comment", "write a comment", "কমেন্ট"))
            }
            if (!opened) {
                return "⚠️ $appLabel-এ কমেন্ট বাটন পাইনি"
            }
            delay(450)
            commentInput = waitForValue(stepTimeout) {
                svc.findEditable() ?: svc.findByHints(commentBoxHints)
            }
        }
        if (text == null) {
            return if (commentInput != null) "💬 $appLabel কমেন্ট বক্স খোলা হয়েছে" else "💬 কমেন্ট খোলা হয়েছে"
        }
        if (commentInput == null) {
            return "⚠️ $appLabel-এ কমেন্ট লেখার বক্স পাইনি"
        }

        onProgress("কমেন্ট লেখা হচ্ছে...")
        if (!svc.enterText(commentInput, text)) {
            return "⚠️ $appLabel-এ কমেন্ট লেখা যায়নি"
        }
        delay(SEND_SETTLE_MS)
        onProgress("কমেন্ট পোস্ট করা হচ্ছে...")
        val sendHints = flow?.sendHints ?: listOf("send", "post", "comment", "পাঠান", "পোস্ট")
        val sent = waitFor(2500L) {
            svc.submitFromInput(svc.findEditable() ?: commentInput, sendHints)
        }
        return if (sent) {
            "💬 $appLabel-এ কমেন্ট পোস্ট: $text"
        } else {
            "⚠️ $appLabel-এ কমেন্ট send বাটন পাইনি (লেখা বসানো আছে)"
        }
    }

    suspend fun runAppScreenAction(
        appKey: String?,
        action: AppActionType,
        text: String? = null,
        onProgress: (String) -> Unit = {}
    ): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"
        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"

        val flow = appKey?.let { AppFlows.find(it) }
            ?: AssistantMemory.appKeyFromPackage(svc.foregroundPackage())?.let { AppFlows.find(it) }

        if (action == AppActionType.COMMENT) {
            return runAppComment(flow, text, onProgress)
        }

        val stepTimeout = maxOf(flow?.stepTimeoutMs ?: 3500L, AssistantPrefs.appFlowTimeoutMs(ctx))

        return when (action) {
            AppActionType.LIKE -> {
                val pkg = svc.foregroundPackage()
                if (pkg in AssistantConfig.SHORT_VIDEO_APPS) {
                    svc.doubleTapCenter()
                    "❤️ Like"
                } else if (svc.clickByHints(listOf("like", "react", "লাইক"))) {
                    "👍 Like"
                } else {
                    svc.doubleTapCenter()
                    "❤️ Like (double tap)"
                }
            }
            AppActionType.SUBSCRIBE -> {
                val ok = waitFor(stepTimeout) {
                    svc.clickByHints(listOf("subscribe", "সাবস্ক্রাইব"))
                }
                if (ok) "🔔 Subscribe করা হয়েছে" else "⚠️ Subscribe বাটন স্ক্রিনে পাইনি"
            }
            AppActionType.FOLLOW -> {
                val ok = waitFor(stepTimeout) {
                    svc.clickByHints(listOf("follow", "ফলো"))
                }
                if (ok) "➕ Follow করা হয়েছে" else "⚠️ Follow বাটন স্ক্রিনে পাইনি"
            }
            AppActionType.REFRESH -> {
                if (svc.clickByHints(listOf("refresh", "reload", "রিফ্রেশ"))) {
                    return "🔄 পেজ রিফ্রেশ হয়েছে"
                }
                if (svc.clickByHints(listOf("more options", "more", "menu"))) {
                    delay(350)
                    if (svc.clickByHints(listOf("refresh", "reload", "রিফ্রেশ"))) {
                        return "🔄 পেজ রিফ্রেশ হয়েছে"
                    }
                }
                "⚠️ Refresh বাটন পাইনি"
            }
            AppActionType.NEW_TAB -> {
                if (svc.clickByHints(listOf("new tab", "নতুন ট্যাব"))) {
                    return "🗂️ নতুন ট্যাব খোলা হয়েছে"
                }
                if (svc.clickByHints(listOf("more options", "more", "tab_switcher_button", "switch or close tabs"))) {
                    delay(350)
                    if (svc.clickByHints(listOf("new tab", "নতুন ট্যাব", "create new tab"))) {
                        return "🗂️ নতুন ট্যাব খোলা হয়েছে"
                    }
                }
                "⚠️ New Tab বাটন পাইনি"
            }
            AppActionType.FULLSCREEN -> {
                if (svc.clickByHints(listOf("enter fullscreen", "fullscreen", "full screen", "exit fullscreen", "ফুলস্ক্রিন"))) {
                    "⛶ Fullscreen বদলানো হয়েছে"
                } else {
                    svc.tapCenter()
                    delay(200)
                    if (svc.clickByHints(listOf("enter fullscreen", "fullscreen", "full screen", "exit fullscreen"))) {
                        "⛶ Fullscreen বদলানো হয়েছে"
                    } else {
                        "⚠️ Fullscreen বাটন পাইনি"
                    }
                }
            }
            AppActionType.END_CALL -> {
                val ok = waitFor(2500L) {
                    svc.clickByHints(listOf("end call", "hang up", "disconnect", "decline", "call_end", "কল কাটুন"))
                }
                if (ok) "📵 কল কাটা হয়েছে" else "⚠️ কল কাটার বাটন স্ক্রিনে পাইনি"
            }
            AppActionType.SPEAKER_ON, AppActionType.SPEAKER_OFF -> {
                val ok = svc.clickByHints(listOf("speaker", "speakerphone", "audio output", "স্পিকার"))
                if (ok) {
                    if (action == AppActionType.SPEAKER_ON) "🔊 Speaker চালু করা হয়েছে" else "🔈 Speaker বদলানো হয়েছে"
                } else {
                    "⚠️ Speaker বাটন স্ক্রিনে পাইনি"
                }
            }
            AppActionType.COMMENT -> "💬 কমেন্ট"
        }
    }

    // ------------------------------------------------------------ Pack H (Messenger Call & Message)

    private fun contactVariants(spoken: String): List<String> {
        val out = LinkedHashSet<String>()
        val clean = spoken.trim()
        if (clean.isNotEmpty()) out.add(clean)
        val low = clean.lowercase()
        for (g in AssistantConfig.CONTACT_GROUPS) {
            if (g.any { it.lowercase() == low }) {
                out.addAll(g)
            }
        }
        if (AssistantSetup.has(ctx, android.Manifest.permission.READ_CONTACTS) && out.isNotEmpty()) {
            for (name in out.toList()) {
                try {
                    ctx.contentResolver.query(
                        Phone.CONTENT_URI,
                        arrayOf(Phone.DISPLAY_NAME),
                        "${Phone.DISPLAY_NAME} LIKE ?",
                        arrayOf("%$name%"),
                        null
                    )?.use { c ->
                        while (c.moveToNext() && out.size < 12) {
                            val dn = c.getString(0)?.trim()
                            if (!dn.isNullOrEmpty()) out.add(dn)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        return out.toList()
    }

    private fun findMatchingContactNode(
        svc: AssistantAccessibilityService,
        variants: List<String>
    ): AccessibilityNodeInfo? {
        val lowerVariants = variants.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (lowerVariants.isEmpty()) return null
        return svc.findNode { n ->
            if (n.isEditable || n.className?.toString()?.contains("EditText", true) == true) {
                return@findNode false
            }
            val t = n.text?.toString().orEmpty().trim().lowercase()
            val d = n.contentDescription?.toString().orEmpty().trim().lowercase()
            lowerVariants.any { v ->
                (t.isNotEmpty() && t.contains(v)) || (d.isNotEmpty() && d.contains(v))
            }
        }
    }

    private suspend fun openSearchAndSelectContact(
        svc: AssistantAccessibilityService,
        flow: AppFlow,
        contact: String,
        openTimeout: Long,
        stepTimeout: Long,
        onProgress: (String) -> Unit
    ): String? {
        AssistantLog.d("FlowRunner: opening ${flow.key} for contact='$contact'")
        onProgress("${flow.key} খোলা হচ্ছে...")
        if (!openFlowApp(flow)) {
            return "❌ ${flow.key} অ্যাপটি ফোনে পাইনি"
        }
        if (!svc.waitForPackage(flow.packages, openTimeout)) {
            return "⏱ ${flow.key} খুলতে সময় বেশি লেগেছে"
        }
        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"
        delay(500)

        onProgress("${flow.key}-তে Search খোঁজা হচ্ছে...")
        val searchClicked = waitFor(stepTimeout) {
            svc.clickByHints(flow.searchHints)
        }
        AssistantLog.d("FlowRunner(${flow.key}): click search(${flow.searchHints}) -> $searchClicked")
        if (!searchClicked) {
            return "⚠️ ${flow.key}-তে search বাটন পাইনি"
        }
        delay(350)

        val searchInput = waitForValue(stepTimeout) {
            svc.findEditable() ?: svc.findByHints(flow.searchHints)
        }
        if (searchInput == null) {
            AssistantLog.d("FlowRunner(${flow.key}): search input not found")
            return "⚠️ ${flow.key}-তে search বক্স পাইনি"
        }
        onProgress("$contact খোঁজা হচ্ছে...")
        val variants = contactVariants(contact)
        val queryName = variants.firstOrNull { it != contact && it.length >= 2 && it.any { ch -> ch in 'a'..'z' || ch in 'A'..'Z' } }
            ?: contact
        val entered = svc.enterText(searchInput, queryName)
        AssistantLog.d("FlowRunner(${flow.key}): enterText('$queryName') -> $entered")
        if (!entered) {
            return "⚠️ ${flow.key}-তে নাম লেখা যায়নি"
        }
        delay(600)

        var resultNode = waitForValue(stepTimeout) {
            findMatchingContactNode(svc, variants)
        }
        if (resultNode == null && queryName != contact) {
            svc.enterText(searchInput, contact)
            delay(500)
            resultNode = waitForValue(2500L) {
                findMatchingContactNode(svc, variants)
            }
        }
        if (resultNode == null) {
            AssistantLog.d("FlowRunner(${flow.key}): contact '$contact' not found in results (variants=$variants)")
            return "⚠️ ${flow.key}-তে '$contact' খুঁজে পাইনি"
        }
        onProgress("$contact-এ ক্লিক করা হচ্ছে...")
        val clickedContact = svc.clickNode(resultNode)
        AssistantLog.d("FlowRunner(${flow.key}): click contact node -> $clickedContact")
        if (!clickedContact) {
            return "⚠️ ${flow.key}-তে '$contact'-এ ক্লিক করা যায়নি"
        }
        delay(500)
        return null
    }

    suspend fun runAppCall(
        flow: AppFlow,
        contact: String,
        video: Boolean,
        onProgress: (String) -> Unit = {}
    ): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"
        val openTimeout = maxOf(flow.openTimeoutMs, AssistantPrefs.appFlowTimeoutMs(ctx))
        val stepTimeout = maxOf(flow.stepTimeoutMs, AssistantPrefs.appFlowTimeoutMs(ctx))

        val err = openSearchAndSelectContact(svc, flow, contact, openTimeout, stepTimeout, onProgress)
        if (err != null) return err

        val callHints = if (video) flow.videoCallHints else flow.audioCallHints
        val callLabel = if (video) "video call" else "audio call"
        onProgress("${flow.key}-তে $callLabel করা হচ্ছে...")
        val callClicked = waitFor(stepTimeout) {
            svc.clickByHints(callHints)
        }
        AssistantLog.d("FlowRunner(${flow.key}): click $callLabel($callHints) -> $callClicked")
        return if (callClicked) {
            "📞 ${flow.key}-তে $contact-কে $callLabel শুরু হয়েছে (best effort)"
        } else {
            "⚠️ ${flow.key}-তে $callLabel বাটন পাইনি"
        }
    }

    suspend fun runAppMessage(
        flow: AppFlow,
        contact: String,
        text: String,
        send: Boolean,
        onProgress: (String) -> Unit = {}
    ): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"
        val openTimeout = maxOf(flow.openTimeoutMs, AssistantPrefs.appFlowTimeoutMs(ctx))
        val stepTimeout = maxOf(flow.stepTimeoutMs, AssistantPrefs.appFlowTimeoutMs(ctx))

        val err = openSearchAndSelectContact(svc, flow, contact, openTimeout, stepTimeout, onProgress)
        if (err != null) return err

        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"

        onProgress("মেসেজ বক্স খোঁজা হচ্ছে...")
        val msgInput = waitForValue(stepTimeout) {
            svc.findEditable() ?: svc.findByHints(flow.inputHints)
        }
        if (msgInput == null) {
            AssistantLog.d("FlowRunner(${flow.key}): message input not found")
            return "⚠️ ${flow.key}-তে মেসেজ লেখার বক্স পাইনি"
        }
        onProgress("মেসেজ লেখা হচ্ছে...")
        if (!svc.enterText(msgInput, text)) {
            return "⚠️ ${flow.key}-তে মেসেজ লেখা যায়নি"
        }
        if (!send) {
            return "⌨️ ${flow.key}-তে $contact-এর বক্সে লেখা হয়েছে"
        }

        delay(SEND_SETTLE_MS)
        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"

        onProgress("মেসেজ পাঠানো হচ্ছে...")
        val sent = waitFor(2500L) {
            svc.submitFromInput(svc.findEditable() ?: msgInput, flow.sendHints)
        }
        AssistantLog.d("FlowRunner(${flow.key}): submitFromInput(send=$send) -> $sent")
        return if (sent) {
            "✅ ${flow.key}-তে $contact-কে পাঠানো হয়েছে"
        } else {
            "⚠️ ${flow.key}-তে send বাটন পাইনি (লেখা বসানো আছে)"
        }
    }

    // ------------------------------------------------------------ Pack I (Camera shutter)

    suspend fun tapShutterIfPresent(): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "📷 ক্যামেরা খোলা হয়েছে"
        val clicked = waitFor(2500L) {
            svc.clickByHints(SHUTTER_HINTS)
        }
        AssistantLog.d("FlowRunner.tapShutterIfPresent -> $clicked")
        return if (clicked) "📸 ছবি তোলা হয়েছে" else "📷 ক্যামেরা খোলা হয়েছে (shutter বাটন পাননি)"
    }
}
