package com.banglakeys.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.delay

/**
 * ফোন "ছোঁয়া" ছাড়া চালানোর মূল ইঞ্জিন: swipe, tap, double tap, back/home,
 * screen-এর লেখা দেখে বাটনে click, multi-step FlowRunner helpers, ও নিরাপদ Auto Ad Skipper।
 * Settings > Accessibility থেকে একবার ON করতে হয়।
 */
class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AssistantAccessibilityService? = null

        private val VIEWS_REGEX = Regex(
            """(\d[\d.,]*)\s*(k|m|b|lakh|crore|হাজার|লাখ|কোটি)?\s*(views|view|ভিউ)""",
            RegexOption.IGNORE_CASE
        )

        private const val AD_SCAN_INTERVAL_MS = 450L
        private const val AD_CLICK_COOLDOWN_MS = 2200L
    }

    private var lastAdScanAt = 0L
    private var lastAdClickAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AssistantLog.d("Accessibility service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AssistantPrefs.adSkipEnabled(this)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAdScanAt < AD_SCAN_INTERVAL_MS) return
        if (now - lastAdClickAt < AD_CLICK_COOLDOWN_MS) return
        lastAdScanAt = now
        scanAndSkipAds(now)
    }

    override fun onInterrupt() {}

    // ---------------------------------------------------------------- info

    fun foregroundPackage(): String = rootInActiveWindow?.packageName?.toString() ?: ""

    private fun screenW(): Int = resources.displayMetrics.widthPixels
    private fun screenH(): Int = resources.displayMetrics.heightPixels

    // ------------------------------------------------------------- gestures

    /** আঙুলের দিক অনুযায়ী swipe। UP = আঙুল নিচ থেকে উপরে। */
    fun swipe(dir: Dir): Boolean {
        val w = screenW().toFloat()
        val h = screenH().toFloat()
        return when (dir) {
            Dir.UP -> swipe(w * 0.5f, h * 0.72f, w * 0.5f, h * 0.28f, 260)
            Dir.DOWN -> swipe(w * 0.5f, h * 0.28f, w * 0.5f, h * 0.72f, 260)
            Dir.LEFT -> swipe(w * 0.8f, h * 0.5f, w * 0.2f, h * 0.5f, 260)
            Dir.RIGHT -> swipe(w * 0.2f, h * 0.5f, w * 0.8f, h * 0.5f, 260)
        }
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(g, null, null)
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(g, null, null)
    }

    fun doubleTap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .addStroke(GestureDescription.StrokeDescription(path, 140, 50))
            .build()
        return dispatchGesture(g, null, null)
    }

    fun tapCenter(): Boolean = tap(screenW() / 2f, screenH() / 2f)

    fun doubleTapCenter(): Boolean = doubleTap(screenW() / 2f, screenH() / 2f)

    // -------------------------------------------------------- global actions

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun quickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun lockScreen(): Boolean =
        Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun screenshot(): Boolean =
        Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    // ------------------------------------------------------- screen reading

    private fun walk(node: AccessibilityNodeInfo?, depth: Int, visit: (AccessibilityNodeInfo) -> Unit) {
        if (node == null || depth > 40) return
        visit(node)
        for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1, visit)
    }

    private fun labelOf(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString().orEmpty()
        val d = n.contentDescription?.toString().orEmpty()
        return if (d.isNotEmpty()) d else t
    }

    fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        var hit: AccessibilityNodeInfo? = null
        walk(root, 0) { n ->
            if (hit != null) return@walk
            if (predicate(n)) hit = n
        }
        return hit
    }

    fun findEditable(): AccessibilityNodeInfo? =
        findNode { n ->
            n.isVisibleToUser && (n.isEditable || n.className?.toString()?.contains("EditText", true) == true)
        }

    fun findByHints(hints: List<String>): AccessibilityNodeInfo? {
        val lowHints = hints.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (lowHints.isEmpty()) return null
        return findNode { n ->
            if (!n.isVisibleToUser) return@findNode false
            val text = n.text?.toString().orEmpty().lowercase()
            val desc = n.contentDescription?.toString().orEmpty().lowercase()
            val hint = if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString().orEmpty().lowercase() else ""
            val viewId = n.viewIdResourceName.orEmpty().lowercase()
            lowHints.any { h ->
                text.contains(h) || desc.contains(h) || hint.contains(h) || viewId.contains(h)
            }
        }
    }

    fun findLowestClickableAtBottom(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val minY = (screenH() * 0.55f).toInt()
        var best: AccessibilityNodeInfo? = null
        var bestBottom = -1
        val rect = Rect()
        walk(root, 0) { n ->
            if (!n.isVisibleToUser || (!n.isClickable && !n.isEditable)) return@walk
            n.getBoundsInScreen(rect)
            if (!rect.isEmpty && rect.centerY() >= minY && rect.bottom > bestBottom) {
                bestBottom = rect.bottom
                best = n
            }
        }
        return best
    }

    fun clickByHints(hints: List<String>): Boolean {
        val node = findByHints(hints) ?: return false
        return clickNode(node)
    }

    fun enterText(node: AccessibilityNodeInfo, text: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    suspend fun waitForPackage(packages: List<String>, timeoutMs: Long): Boolean {
        val targets = packages.map { it.lowercase() }.toSet()
        if (targets.isEmpty()) return true
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            val fg = foregroundPackage().lowercase()
            if (fg in targets) return true
            val rem = deadline - SystemClock.uptimeMillis()
            if (rem <= 0L) return false
            delay(minOf(250L, rem))
        }
    }

    /** Screen-এ [text] লেখা (বা description) থাকা বাটনে click। exact=true হলে পুরো লেখা মিলতে হবে। */
    fun clickByText(text: String, exact: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val key = text.trim().lowercase()
        var target: AccessibilityNodeInfo? = null
        walk(root, 0) { n ->
            if (target != null) return@walk
            val label = labelOf(n).lowercase()
            val t = n.text?.toString().orEmpty().lowercase()
            val hit = if (exact) (label == key || t == key) else (label.contains(key) || t.contains(key))
            if (hit) target = n
        }
        return target?.let { clickNode(it) } ?: false
    }

    /** [labels]-এর যেকোনো একটা লেখার বাটন screen-এ পেলে click করে (যেমন "Clear all" / "Close all")। */
    fun clickByAnyText(labels: List<String>): Boolean = labels.any { clickByText(it) }

    /** Recents-এ মাঝের (চলতি) app-কার্ড উপরে ঠেলে বন্ধ করে। */
    fun dismissCurrentCard(): Boolean {
        val w = screenW().toFloat()
        val h = screenH().toFloat()
        return swipe(w * 0.5f, h * 0.5f, w * 0.5f, h * 0.1f, 220)
    }

    /** Node-টা (বা তার clickable parent-কে) click করে; না পারলে মাঝখানে tap। */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 8) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            depth++
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        return if (!r.isEmpty) tap(r.centerX().toFloat(), r.centerY().toFloat()) else false
    }

    // --------------------------------------------------------------- Auto Ad Skipper

    private fun scanAndSkipAds(now: Long) {
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString().orEmpty()
        if (AdSkipper.isBlockedPackage(pkg)) return

        val adNodes = ArrayList<AdSkipper.AdNode>(64)
        val rawMap = HashMap<Int, AccessibilityNodeInfo>(64)
        val r = Rect()

        fun collect(node: AccessibilityNodeInfo?, parentId: Int, depth: Int) {
            if (node == null || depth > 35 || adNodes.size >= 240) return
            val id = adNodes.size + 1
            node.getBoundsInScreen(r)
            val hint = if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString().orEmpty() else ""
            adNodes.add(
                AdSkipper.AdNode(
                    id = id,
                    viewId = node.viewIdResourceName.orEmpty(),
                    className = node.className?.toString().orEmpty(),
                    text = node.text?.toString().orEmpty(),
                    contentDescription = node.contentDescription?.toString().orEmpty(),
                    hintText = hint,
                    isClickable = node.isClickable,
                    isEnabled = node.isEnabled,
                    isVisible = node.isVisibleToUser,
                    isEditable = node.isEditable,
                    isFocused = node.isFocused,
                    isPassword = node.isPassword,
                    left = r.left,
                    top = r.top,
                    right = r.right,
                    bottom = r.bottom,
                    parentId = parentId
                )
            )
            rawMap[id] = node
            for (i in 0 until node.childCount) {
                collect(node.getChild(i), id, depth + 1)
            }
        }

        collect(root, 0, 0)
        val allowGames = AssistantPrefs.adSkipGamesEnabled(this)
        val match = AdSkipper.findSkipTarget(pkg, screenW(), screenH(), adNodes, allowGames) ?: return
        val targetNode = rawMap[match.nodeId] ?: return
        if (clickNode(targetNode)) {
            lastAdClickAt = now
            AssistantPrefs.recordAdSkip(this, match.sourceLabel)
            AssistantLog.d("AdSkipper skipped [${match.sourceLabel}] reason=${match.reason}")
            Toast.makeText(applicationContext, "Ad skipped (${match.sourceLabel})", Toast.LENGTH_SHORT).show()
        }
    }

    /** "1.2M views" / "350K views" / "12,345 views" থেকে সংখ্যা বের করে। */
    private fun parseViews(desc: String): Long? {
        val m = VIEWS_REGEX.find(desc) ?: return null
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

    /** YouTube search result-এ এখন যেসব ভিডিও দেখা যাচ্ছে তার ভেতর প্রথমটা। */
    fun findFirstVideo(): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walk(rootInActiveWindow, 0) { n ->
            if (found != null) return@walk
            val d = n.contentDescription?.toString().orEmpty()
            if (d.isNotEmpty() && (parseViews(d) != null || d.contains("play video", true))) found = n
        }
        return found
    }

    /** এখন screen-এ দেখা যাওয়া ভিডিওগুলোর ভেতর সবচেয়ে বেশি view-ওয়ালাটা। */
    fun findMostViewedVideo(): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestViews = -1L
        walk(rootInActiveWindow, 0) { n ->
            val d = n.contentDescription?.toString().orEmpty()
            if (d.isEmpty()) return@walk
            val v = parseViews(d) ?: return@walk
            if (v > bestViews) { bestViews = v; best = n }
        }
        return best
    }

    // --------------------------------------------------------------- typing

    /** এখন যে input box-এ cursor আছে সেখানে লেখা বসায়। */
    fun typeText(text: String): Boolean {
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val old = if (Build.VERSION.SDK_INT >= 26 && f.isShowingHintText) "" else f.text?.toString().orEmpty()
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, old + text)
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Keyboard-এর Enter/Search চাপার মতো কাজ (Android 11+)। */
    fun pressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }
}
