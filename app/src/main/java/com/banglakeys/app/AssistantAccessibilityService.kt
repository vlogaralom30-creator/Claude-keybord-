package com.banglakeys.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

/**
 * ফোন "ছোঁয়া" ছাড়া চালানোর মূল ইঞ্জিন: swipe, tap, double tap, back/home,
 * screen-এর লেখা দেখে বাটনে click, Auto Ad Skipper, এবং Secret Message Auto-Detect।
 */
class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AssistantAccessibilityService? = null

        private val VIEWS_REGEX = Regex(
            """(\d[\d.,]*)\s*(k|m|b|lakh|crore|হাজার|লাখ|কোটি)?\s*(views|view|ভিউ)""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        hideSecretBubble()
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        hideSecretBubble()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: TextView? = null
    private var lastShownGlyph: String? = null
    private var lastTriedGlyph: String? = null
    @Volatile private var decoding = false
    private var lastScanAt = 0L
    private var lastAdScanAt = 0L

    private val windowManager: WindowManager get() = getSystemService(WINDOW_SERVICE) as WindowManager

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val now = System.currentTimeMillis()

        // 1. Auto Ad Skipper check (throttled ~450ms)
        if (now - lastAdScanAt > 450) {
            lastAdScanAt = now
            scanAndSkipAds()
        }

        // 2. Secret Message auto-detect check (throttled ~800ms)
        if (now - lastScanAt > 800) {
            lastScanAt = now
            scanForSecretText()
        }
    }

    private fun scanAndSkipAds() {
        if (!AssistantPrefs.adSkipEnabled(this)) return
        val root = rootInActiveWindow ?: return
        val pkg = foregroundPackage()
        if (pkg.isEmpty()) return
        val sw = screenW()
        val sh = screenH()
        val list = ArrayList<AdSkipper.AdNode>()
        var idCounter = 0

        fun collect(node: AccessibilityNodeInfo?, parentId: Int) {
            if (node == null || list.size > 500) return
            val id = idCounter++
            val r = Rect()
            node.getBoundsInScreen(r)
            val adNode = AdSkipper.AdNode(
                id = id,
                viewId = node.viewIdResourceName ?: "",
                className = node.className?.toString() ?: "",
                text = node.text?.toString() ?: "",
                contentDescription = node.contentDescription?.toString() ?: "",
                hintText = if (Build.VERSION.SDK_INT >= 26) node.hintText?.toString() ?: "" else "",
                isClickable = node.isClickable,
                isEnabled = node.isEnabled,
                isVisible = node.isVisibleToUser,
                isEditable = node.isEditable,
                isFocused = node.isFocused,
                isPassword = if (Build.VERSION.SDK_INT >= 26) node.isPassword else false,
                left = r.left,
                top = r.top,
                right = r.right,
                bottom = r.bottom,
                parentId = parentId
            )
            list.add(adNode)
            for (i in 0 until node.childCount) {
                collect(node.getChild(i), id)
            }
        }
        collect(root, -1)

        val allowGames = AssistantPrefs.adSkipGamesEnabled(this)
        val match = AdSkipper.findSkipTarget(pkg, sw, sh, list, allowGameAds = allowGames)
        if (match != null) {
            var targetInfo: AccessibilityNodeInfo? = null
            var currentId = 0
            fun findInfo(n: AccessibilityNodeInfo?) {
                if (n == null || targetInfo != null) return
                if (currentId == match.nodeId) {
                    targetInfo = n
                    return
                }
                currentId++
                for (i in 0 until n.childCount) {
                    findInfo(n.getChild(i))
                }
            }
            findInfo(root)
            if (targetInfo != null) {
                if (clickNode(targetInfo!!)) {
                    AssistantPrefs.recordAdSkip(this, match.sourceLabel)
                }
            }
        }
    }

    private val hideBubbleRunnable = Runnable { hideSecretBubble() }

    private fun scanForSecretText() {
        if (!SecretConfig.autoDetectEnabled(this)) return
        val contacts = SecretConfig.getContacts(this)
        if (contacts.isEmpty()) return

        val root = rootInActiveWindow ?: return
        val candidates = ArrayList<Pair<String, Rect>>()
        walk(root, 0) { n ->
            if (!n.isVisibleToUser) return@walk
            val t = n.text?.toString().orEmpty().trim()
            if (t.isNotEmpty() && GlyphAlphabet.looksLikeGlyphText(t)) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (!r.isEmpty) {
                    candidates.add(t to r)
                }
            }
        }
        if (candidates.isEmpty()) {
            hideSecretBubble()
            // glyph স্ক্রিন থেকে সরে গেলে ভুলে যাই, যাতে scroll করে ফিরলে আবার দেখায়
            lastShownGlyph = null
            lastTriedGlyph = null
            return
        }

        // চ্যাট স্ক্রিনে সবচেয়ে নিচের (নতুন) মেসেজটি আগে দেখানো হবে
        candidates.sortByDescending { it.second.bottom }
        val (glyphText, anchorRect) = candidates.first()

        // একই লেখা আবার আবার decrypt করলে (PBKDF2 ভারী) UI আটকে যেত — একবারই চেষ্টা
        if (glyphText == lastShownGlyph || glyphText == lastTriedGlyph || decoding) return
        lastTriedGlyph = glyphText
        decoding = true
        Thread {
            val bytes = GlyphAlphabet.decodeAny(glyphText)
            val matched = bytes?.let { SecretCipher.decryptWithMatchingContact(it, contacts) }
            if (matched != null) {
                val style = GlyphAlphabet.detectStyle(glyphText) ?: GlyphStyle.RUNIC
                SecretConfig.saveSecretCacheEntry(
                    ctx = this,
                    contactName = matched.first,
                    plainText = matched.second,
                    glyphText = glyphText,
                    style = style,
                    isOutgoing = false
                )
            }
            handler.post {
                decoding = false
                if (matched != null) {
                    lastShownGlyph = glyphText
                    showSecretBubble("${matched.first}: ${matched.second}", anchorRect)
                }
            }
        }.start()
    }

    private fun showSecretBubble(text: String, anchor: Rect) {
        hideSecretBubble()
        val density = resources.displayMetrics.density
        val margin = (12 * density).toInt()
        val sw = screenW()
        val sh = screenH()
        val bubbleMaxW = (sw * 0.86f).toInt().coerceAtLeast((180 * density).toInt())

        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 16 * density
            setColor(0xEB0B2E2B.toInt())
            setStroke((1.5f * density).toInt(), 0xFF26A69A.toInt())
        }

        val tv = TextView(this).apply {
            this.text = "🔓 $text"
            background = bg
            setTextColor(0xFFFFFFFF.toInt())
            setPadding((16 * density).toInt(), (11 * density).toInt(), (16 * density).toInt(), (11 * density).toInt())
            textSize = 14f
            maxWidth = bubbleMaxW
            elevation = 8 * density
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = anchor.left.coerceIn(margin, (sw - bubbleMaxW - margin).coerceAtLeast(margin))
        val preferredY = anchor.bottom + (8 * density).toInt()
        params.y = if (preferredY > sh - (140 * density).toInt()) {
            (anchor.top - (96 * density).toInt()).coerceAtLeast(margin)
        } else {
            preferredY.coerceAtLeast(margin)
        }
        try {
            windowManager.addView(tv, params)
            overlayView = tv
        } catch (_: Exception) {}
        handler.postDelayed(hideBubbleRunnable, 8000)
    }

    private fun hideSecretBubble() {
        handler.removeCallbacks(hideBubbleRunnable)
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    override fun onInterrupt() {}

    // ---------------------------------------------------------------- info & FlowRunner helpers

    fun foregroundPackage(): String = rootInActiveWindow?.packageName?.toString() ?: ""

    fun waitForPackage(packages: List<String>, timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val pkg = foregroundPackage()
            if (pkg.isNotEmpty() && packages.any { pkg.contains(it, true) }) return true
            try {
                Thread.sleep(100L)
            } catch (_: InterruptedException) {
                break
            }
        }
        return false
    }

    fun findEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        var found: AccessibilityNodeInfo? = null
        walk(root, 0) { n ->
            if (found != null) return@walk
            if (n.isEditable) found = n
        }
        return found
    }

    fun findByHints(hints: List<String>): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val lowerHints = hints.map { it.lowercase() }
        var found: AccessibilityNodeInfo? = null
        walk(root, 0) { n ->
            if (found != null) return@walk
            val label = labelOf(n).lowercase()
            val text = n.text?.toString().orEmpty().lowercase()
            val hint = if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString().orEmpty().lowercase() else ""
            if (lowerHints.any { h -> label.contains(h) || text.contains(h) || hint.contains(h) }) {
                found = n
            }
        }
        return found
    }

    fun findLowestClickableAtBottom(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        var best: AccessibilityNodeInfo? = null
        var maxY = -1
        walk(root, 0) { n ->
            if (n.isClickable) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (r.top > maxY && r.bottom <= screenH()) {
                    maxY = r.top
                    best = n
                }
            }
        }
        return best
    }

    fun clickByHints(hints: List<String>): Boolean {
        val node = findByHints(hints) ?: return false
        return clickNode(node)
    }

    fun findNode(filter: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        var found: AccessibilityNodeInfo? = null
        walk(root, 0) { n ->
            if (found != null) return@walk
            if (filter(n)) found = n
        }
        return found
    }

    fun clickByAnyText(texts: List<String>): Boolean {
        for (t in texts) {
            if (clickByText(t)) return true
        }
        return false
    }

    fun dismissCurrentCard(): Boolean {
        return swipe(Dir.DOWN)
    }

    private fun screenW(): Int = resources.displayMetrics.widthPixels
    private fun screenH(): Int = resources.displayMetrics.heightPixels

    // ------------------------------------------------------------- gestures

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

    fun findFirstVideo(): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walk(rootInActiveWindow, 0) { n ->
            if (found != null) return@walk
            val d = n.contentDescription?.toString().orEmpty()
            if (d.isNotEmpty() && (parseViews(d) != null || d.contains("play video", true))) found = n
        }
        return found
    }

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

    fun enterText(node: AccessibilityNodeInfo, text: String): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 6) {
            if (n.isEditable) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                if (n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
            }
            n = n.parent
            depth++
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true

        val r = Rect()
        node.getBoundsInScreen(r)
        if (!r.isEmpty) {
            tap(r.centerX().toFloat(), r.centerY().toFloat())
            try { Thread.sleep(100L) } catch (_: Exception) {}
            return typeText(text)
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val old = if (Build.VERSION.SDK_INT >= 26 && f.isShowingHintText) "" else f.text?.toString().orEmpty()
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, old + text)
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun pressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }
}
