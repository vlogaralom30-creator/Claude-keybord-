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

    suspend fun runAppChat(flow: AppFlow?, text: String, send: Boolean): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"
        val appLabel = flow?.key ?: "এই app"
        val openTimeout = maxOf(flow?.openTimeoutMs ?: 4000L, AssistantPrefs.appFlowTimeoutMs(ctx))
        val stepTimeout = maxOf(flow?.stepTimeoutMs ?: 4000L, AssistantPrefs.appFlowTimeoutMs(ctx))

        if (flow != null) {
            AssistantLog.d("FlowRunner.runAppChat: launching ${flow.key}, send=$send")
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

        val sendHints = flow?.sendHints ?: DEFAULT_SEND_HINTS
        val sent = waitFor(2500L) {
            svc.clickByHints(sendHints)
        }
        AssistantLog.d("FlowRunner.runAppChat: clickByHints(send=$sendHints) -> $sent")
        return if (sent) {
            "✅ $appLabel-এ পাঠানো হয়েছে"
        } else {
            "⚠️ $appLabel-এ send বাটন পাইনি (লেখা বসানো আছে)"
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
        stepTimeout: Long
    ): String? {
        AssistantLog.d("FlowRunner: opening ${flow.key} for contact='$contact'")
        if (!openFlowApp(flow)) {
            return "❌ ${flow.key} অ্যাপটি ফোনে পাইনি"
        }
        if (!svc.waitForPackage(flow.packages, openTimeout)) {
            return "⏱ ${flow.key} খুলতে সময় বেশি লেগেছে"
        }
        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"
        delay(500)

        // Step 1: find & click search button (unless an editable search box is already open)
        val searchClicked = waitFor(stepTimeout) {
            svc.clickByHints(flow.searchHints)
        }
        AssistantLog.d("FlowRunner(${flow.key}): click search(${flow.searchHints}) -> $searchClicked")
        if (!searchClicked) {
            return "⚠️ ${flow.key}-তে search বাটন পাইনি"
        }
        delay(350)

        // Step 2: find search input & enter contact name
        val searchInput = waitForValue(stepTimeout) {
            svc.findEditable() ?: svc.findByHints(flow.searchHints)
        }
        if (searchInput == null) {
            AssistantLog.d("FlowRunner(${flow.key}): search input not found")
            return "⚠️ ${flow.key}-তে search বক্স পাইনি"
        }
        val variants = contactVariants(contact)
        val queryName = variants.firstOrNull { it != contact && it.length >= 2 && it.any { ch -> ch in 'a'..'z' || ch in 'A'..'Z' } }
            ?: contact
        val entered = svc.enterText(searchInput, queryName)
        AssistantLog.d("FlowRunner(${flow.key}): enterText('$queryName') -> $entered")
        if (!entered) {
            return "⚠️ ${flow.key}-তে নাম লেখা যায়নি"
        }
        delay(600)

        // Step 3: click FIRST matching contact result (try typed query variants, else fallback with original)
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
        val clickedContact = svc.clickNode(resultNode)
        AssistantLog.d("FlowRunner(${flow.key}): click contact node -> $clickedContact")
        if (!clickedContact) {
            return "⚠️ ${flow.key}-তে '$contact'-এ ক্লিক করা যায়নি"
        }
        delay(500)
        return null // null means success up to opened chat/profile screen
    }

    suspend fun runAppCall(flow: AppFlow, contact: String, video: Boolean): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"
        val openTimeout = maxOf(flow.openTimeoutMs, AssistantPrefs.appFlowTimeoutMs(ctx))
        val stepTimeout = maxOf(flow.stepTimeoutMs, AssistantPrefs.appFlowTimeoutMs(ctx))

        val err = openSearchAndSelectContact(svc, flow, contact, openTimeout, stepTimeout)
        if (err != null) return err

        val callHints = if (video) flow.videoCallHints else flow.audioCallHints
        val callLabel = if (video) "video call" else "audio call"
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

    suspend fun runAppMessage(flow: AppFlow, contact: String, text: String, send: Boolean): String {
        val svc = AssistantAccessibilityService.instance
            ?: return "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"
        val openTimeout = maxOf(flow.openTimeoutMs, AssistantPrefs.appFlowTimeoutMs(ctx))
        val stepTimeout = maxOf(flow.stepTimeoutMs, AssistantPrefs.appFlowTimeoutMs(ctx))

        val err = openSearchAndSelectContact(svc, flow, contact, openTimeout, stepTimeout)
        if (err != null) return err

        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"

        val msgInput = waitForValue(stepTimeout) {
            svc.findEditable() ?: svc.findByHints(flow.inputHints)
        }
        if (msgInput == null) {
            AssistantLog.d("FlowRunner(${flow.key}): message input not found")
            return "⚠️ ${flow.key}-তে মেসেজ লেখার বক্স পাইনি"
        }
        if (!svc.enterText(msgInput, text)) {
            return "⚠️ ${flow.key}-তে মেসেজ লেখা যায়নি"
        }
        if (!send) {
            return "⌨️ ${flow.key}-তে $contact-এর বক্সে লেখা হয়েছে"
        }

        delay(SEND_SETTLE_MS)
        if (isBlocked(svc)) return "🔒 এই app-এ assistant বন্ধ"

        val sent = waitFor(2500L) {
            svc.clickByHints(flow.sendHints)
        }
        AssistantLog.d("FlowRunner(${flow.key}): send($send) -> $sent")
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
