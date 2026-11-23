package com.banglakeys.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Canvas-এ সরাসরি আঁকা keyboard: কোনো child view নেই, তাই দ্রুত ও smooth।
 * Multi-touch আছে (দ্রুত টাইপ করলে আগের key ছাড়ার আগেই পরের key চাপা যায়)।
 *
 * উপরের strip-এ দুই মোডে কাজ করে:
 *  ১) টাইপিং মোড: বামে live composing badge + ডানে সর্বোচ্চ ৩টি গোলাকার suggestion pill
 *  ২) Idle মোড: বামে ৪টি vector quick-action shortcut + ডানে next-word / starter pill
 */
class KeyboardView(context: Context) : View(context) {

    enum class QuickAction {
        NOTEPAD,
        TRANSLATE,
        VOICE,
        ASSISTANT,
    }

    interface Listener {
        fun onText(text: String)
        fun onDelete()
        fun onEnter()
        fun onSpace()
        fun onSpaceLongPress()
        fun onSwitchLang()
        fun onSymbols(mode: Int)      // 0 = অক্ষর, 1 = ?123, 2 = =\<
        fun onComposingTap()
        fun onFeedback()
        fun onSuggestionTap(word: String)
        fun onSuggestionLongPress(word: String) {}
        fun onSnippet(text: String)
        fun onNotepad()
        fun onTranslate()
        fun onVoice()
        fun onAssistant()
        fun onSecretToggle() {}
        fun onNoteEditSave() {}
        fun onNoteEditCancel() {}
    }

    var listener: Listener? = null

    // ---- state ----
    private class Box(val key: Key, val rect: RectF)

    private sealed class StripHit {
        object Composing : StripHit()
        data class Word(val index: Int) : StripHit()
        data class Action(val action: QuickAction) : StripHit()
        object CancelNoteEdit : StripHit()
        object SaveNoteEdit : StripHit()
    }

    private class Ptr(
        var box: Box?,
        val inStrip: Boolean,
        var stripHit: StripHit? = null
    ) {
        var consumed = false
        var runnable: Runnable? = null
    }

    private var rows: List<List<Key>> = emptyList()
    private var boxRows: List<List<Box>> = emptyList()
    private var composing = ""
    private var suggestions: List<String> = emptyList()
    private var composingRect = RectF()
    private var cancelEditRect = RectF()
    private var saveEditRect = RectF()
    private var suggestionRects: List<RectF> = emptyList()
    private var quickActionRects: LinkedHashMap<QuickAction, RectF> = LinkedHashMap()
    private var stripDirty = true

    private var spaceLabel = ""
    private var enterLabel: String? = null
    private var rowScale = 1f
    private var secretModeActive = false
    private var secretRecipient: String? = null
    private var noteEditModeActive = false

    private var shift = SHIFT_OFF
    private var lastShiftTap = 0L

    private val ptrs = SparseArray<Ptr>()
    private val handler = Handler(Looper.getMainLooper())

    // ---- drawing tools ----
    private val d = resources.displayMetrics.density
    private val fontD = d * resources.configuration.fontScale
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val popupRect = RectF()

    private var bgC = 0
    private var keyC = 0
    private var fnC = 0
    private var shadowC = 0
    private var textC = 0
    private var hintC = 0
    private var pressC = 0
    private var accentC = 0
    private var popupC = 0

    // Strip pill colors
    private var pillC = 0
    private var pillStrokeC = 0
    private var badgeBgC = 0
    private var badgeStrokeC = 0
    private var badgeTextC = 0
    private var actionBgC = 0
    private var secretBadgeBgC = 0
    private var secretBadgeStrokeC = 0
    private var secretBadgeTextC = 0

    // ---- theme state ----
    private var theme: KeyboardTheme = Themes.lightDefault
    private var bgBitmap: Bitmap? = null
    private var bgBitmapStamp = -1L
    private var bgShader: LinearGradient? = null
    private var bgShaderH = 0
    private var dimPct = 0
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = Rect()
    private val innerRect = RectF()

    private val stripH: Float get() = 48f * d
    private val vPad: Float get() = 4f * d
    private val hPad: Float get() = 3f * d
    private val gap: Float get() = 3.2f * d
    private val radius: Float get() = theme.radiusDp * d
    private val pillRadius: Float get() = 12f * d

    init {
        applyTheme()
        isHapticFeedbackEnabled = true
    }

    // ---------------- public API ----------------

    fun setLayout(newRows: List<List<Key>>, space: String, scale: Float) {
        rows = newRows
        spaceLabel = space
        rowScale = scale
        cancelAll()
        requestLayout()
        layoutKeys()
        updateStripRects()
        invalidate()
    }

    fun setComposing(text: String) {
        if (composing != text) {
            composing = text
            stripDirty = true
            updateStripRects()
            invalidate()
        }
    }

    fun setSuggestions(list: List<String>) {
        if (suggestions != list) {
            suggestions = list
            stripDirty = true
            updateStripRects()
            invalidate()
        }
    }

    fun setEnterLabel(label: String?) {
        enterLabel = label
        invalidate()
    }

    fun setSecretMode(active: Boolean, recipient: String? = null) {
        if (secretModeActive != active || secretRecipient != recipient) {
            secretModeActive = active
            secretRecipient = recipient
            stripDirty = true
            updateStripRects()
            invalidate()
        }
    }

    fun isSecretMode(): Boolean = secretModeActive

    fun setNoteEditMode(active: Boolean) {
        if (noteEditModeActive != active) {
            noteEditModeActive = active
            stripDirty = true
            updateStripRects()
            invalidate()
        }
    }

    fun isNoteEditMode(): Boolean = noteEditModeActive

    /** English-এ বাক্যের শুরুতে auto capital */
    fun setAutoShift(on: Boolean) {
        if (shift != SHIFT_CAPS) {
            val ns = if (on) SHIFT_ON else SHIFT_OFF
            if (ns != shift) {
                shift = ns
                invalidate()
            }
        }
    }

    fun stripComposingRect(): RectF = RectF(composingRect)

    fun stripNoteCancelRect(): RectF = RectF(cancelEditRect)

    fun stripNoteSaveRect(): RectF = RectF(saveEditRect)

    fun stripSuggestionRects(): List<RectF> = suggestionRects.map { RectF(it) }

    fun stripQuickActionRects(): Map<QuickAction, RectF> =
        quickActionRects.mapValues { RectF(it.value) }

    // ---------------- theme ----------------

    /** Settings বা IME থেকে theme বদলালে এটা ডাকলে নতুন theme আঁকা হয়। */
    fun reloadTheme() {
        applyTheme()
    }

    private fun applyTheme() {
        val t = ThemeStore.resolve(context)
        theme = t

        bgC = t.bg
        keyC = t.key
        fnC = t.fn
        shadowC = t.shadow
        textC = t.text
        hintC = t.hint
        pressC = t.press
        accentC = t.accent
        popupC = t.popup

        pillC = t.pill
        pillStrokeC = t.pillStroke
        badgeBgC = t.badgeBg
        badgeStrokeC = t.badgeStroke
        badgeTextC = t.badgeText
        actionBgC = t.actionBg

        if (t.dark) {
            secretBadgeBgC = 0xFF1B3B2B.toInt()
            secretBadgeStrokeC = 0xFF34D399.toInt()
            secretBadgeTextC = 0xFFA7F3D0.toInt()
        } else {
            secretBadgeBgC = 0xFFD1FAE5.toInt()
            secretBadgeStrokeC = 0xFF059669.toInt()
            secretBadgeTextC = 0xFF065F46.toInt()
        }

        textPaint.typeface =
            if (t.monoFont) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.DEFAULT

        bgShader = null
        bgShaderH = 0

        if (t.usesImage) {
            // key-র opacity user slider থেকে
            val a = (ThemeStore.imageAlpha(context) / 100f * 255f).toInt().coerceIn(30, 240)
            keyC = withAlpha(0x101018, a)
            fnC = withAlpha(0x000000, (a + 35).coerceAtMost(250))
            pillC = keyC
            actionBgC = fnC
            dimPct = ThemeStore.imageDim(context)

            val stamp = ThemeStore.customImageStamp(context)
            if (bgBitmap == null || stamp != bgBitmapStamp) {
                bgBitmap = ThemeStore.loadCustomImage(context)
                bgBitmapStamp = stamp
            }
        } else {
            bgBitmap = null
            bgBitmapStamp = -1L
        }
        invalidate()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        applyTheme()
    }

    // ---------------- measure / layout ----------------

    private fun rowH(): Float {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return (if (landscape) 40f else 52f) * d * rowScale
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (stripH + vPad * 2 + rowH() * max(rows.size, 1)).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cancelAll()
        layoutKeys()
        stripDirty = true
        updateStripRects()
    }

    private fun layoutKeys() {
        val w = width.toFloat()
        if (w <= 0f || rows.isEmpty()) {
            boxRows = emptyList()
            return
        }
        val rh = rowH()
        val out = ArrayList<List<Box>>()
        for ((ri, row) in rows.withIndex()) {
            var total = 0f
            for (k in row) total += k.weight
            val unit = (w - hPad * 2) / total
            var x = hPad
            val top = stripH + vPad + ri * rh
            val list = ArrayList<Box>()
            for (k in row) {
                val kw = unit * k.weight
                list.add(
                    Box(
                        k,
                        RectF(x + gap / 2, top + gap / 2, x + kw - gap / 2, top + rh - gap / 2)
                    )
                )
                x += kw
            }
            out.add(list)
        }
        boxRows = out
    }

    private fun updateStripRects() {
        val totalW = width.toFloat()
        if (totalW <= 0f) {
            composingRect.setEmpty()
            cancelEditRect.setEmpty()
            saveEditRect.setEmpty()
            suggestionRects = emptyList()
            quickActionRects.clear()
            return
        }
        stripDirty = false

        val outerPad = 8f * d
        val pillGap = 6f * d
        val vInset = 6f * d
        val top = vInset
        val bottom = stripH - vInset

        quickActionRects.clear()
        cancelEditRect.setEmpty()
        saveEditRect.setEmpty()

        if (noteEditModeActive) {
            suggestionRects = emptyList()
            val saveW = min(84f * d, totalW * 0.25f)
            val cancelW = 38f * d
            val saveLeft = totalW - outerPad - saveW
            val cancelLeft = saveLeft - pillGap - cancelW
            saveEditRect.set(saveLeft, top, saveLeft + saveW, bottom)
            cancelEditRect.set(cancelLeft, top, cancelLeft + cancelW, bottom)
            val editRight = (cancelLeft - pillGap).coerceAtLeast(outerPad + 40f * d)
            composingRect.set(outerPad, top, editRight, bottom)
            return
        }

        if (composing.isNotEmpty()) {
            // টাইপিং মোড: বামে Live Composing Badge + ডানে সর্বোচ্চ ৩টি Word Pill
            val visCount = min(suggestions.size, 3)
            stripTextPaint.textSize = 15f * fontD
            stripTextPaint.typeface = Typeface.DEFAULT_BOLD
            val rawTextW = stripTextPaint.measureText(composing)
            val badgePadding = 20f * d

            if (visCount == 0) {
                // কোনো suggestion না থাকলে composing badge-টি সুন্দরভাবে বড় হয়ে দেখাবে
                val bw = (rawTextW + badgePadding).coerceIn(72f * d, totalW - outerPad * 2)
                val left = ((totalW - bw) / 2f).coerceAtLeast(outerPad)
                composingRect.set(left, top, left + bw, bottom)
                suggestionRects = emptyList()
                return
            }

            val maxBadgeW = min(totalW * 0.34f, 148f * d)
            val badgeW = (rawTextW + badgePadding).coerceIn(52f * d, maxBadgeW)
            composingRect.set(outerPad, top, outerPad + badgeW, bottom)

            val startX = composingRect.right + pillGap
            val availW = totalW - outerPad - startX
            val rects = ArrayList<RectF>(visCount)
            if (availW > 20f * d) {
                val itemW = (availW - pillGap * (visCount - 1)) / visCount
                for (i in 0 until visCount) {
                    val left = startX + i * (itemW + pillGap)
                    rects.add(RectF(left, top, left + itemW, bottom))
                }
            }
            suggestionRects = rects
        } else {
            // Idle মোড: বামে ৪টি Quick-Action Shortcut + ডানে Next-Word / Starter Pills
            composingRect.setEmpty()
            val actions = QuickAction.values()
            val compact = totalW < 340f * d
            val actionW = (if (compact) 31f else 36f) * d
            val actionGap = (if (compact) 3.5f else 4.5f) * d

            var curX = outerPad
            for (action in actions) {
                quickActionRects[action] = RectF(curX, top, curX + actionW, bottom)
                curX += actionW + actionGap
            }

            val startX = curX + 2f * d
            val availW = totalW - outerPad - startX
            val maxIdleWords = if (compact) 2 else 3
            val visCount = min(suggestions.size, maxIdleWords)
            val rects = ArrayList<RectF>(visCount)
            if (visCount > 0 && availW > 40f * d) {
                val itemW = (availW - pillGap * (visCount - 1)) / visCount
                for (i in 0 until visCount) {
                    val left = startX + i * (itemW + pillGap)
                    rects.add(RectF(left, top, left + itemW, bottom))
                }
            }
            suggestionRects = rects
        }
    }

    // ---------------- drawing ----------------

    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        drawStrip(canvas)
        for (row in boxRows) for (b in row) drawKey(canvas, b)
        drawPopups(canvas)
    }

    private fun drawBackground(c: Canvas) {
        c.drawColor(bgC)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val bmp = bgBitmap
        if (theme.usesImage && bmp != null && !bmp.isRecycled) {
            val scale = max(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
            val sw = w / scale
            val sh = h / scale
            val sx = (bmp.width - sw) / 2f
            val sy = (bmp.height - sh) / 2f
            srcRect.set(sx.toInt(), sy.toInt(), (sx + sw).toInt().coerceAtMost(bmp.width), (sy + sh).toInt().coerceAtMost(bmp.height))
            dstRect.set(0, 0, w, h)
            c.drawBitmap(bmp, srcRect, dstRect, bgPaint)
            if (dimPct > 0) {
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(0x000000, (dimPct / 100f * 255f).toInt())
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.alpha = 255
            }
            return
        }

        if (theme.bgTop != 0 && theme.bgBottom != 0) {
            if (bgShader == null || bgShaderH != h) {
                bgShader = LinearGradient(0f, 0f, 0f, h.toFloat(), theme.bgTop, theme.bgBottom, Shader.TileMode.CLAMP)
                bgShaderH = h
            }
            bgPaint.shader = bgShader
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            bgPaint.shader = null
        }
    }

    private fun isStripPressed(hit: StripHit): Boolean {
        for (i in 0 until ptrs.size()) {
            val p = ptrs.valueAt(i)
            if (p.inStrip && p.stripHit == hit) return true
        }
        return false
    }

    private fun drawStrip(c: Canvas) {
        if (stripDirty && width > 0) updateStripRects()

        // Strip-এর নিচে সূক্ষ্ম separator
        paint.style = Paint.Style.FILL
        paint.color = shadowC
        paint.alpha = 55
        c.drawRect(0f, stripH - 1f * d, width.toFloat(), stripH, paint)
        paint.alpha = 255

        // ০) Note Edit মোডে: বামে Live Note Preview + ডানে Cancel ও Save
        if (noteEditModeActive) {
            if (!composingRect.isEmpty) {
                paint.style = Paint.Style.FILL
                paint.color = badgeBgC
                c.drawRoundRect(composingRect, pillRadius, pillRadius, paint)
                strokePaint.color = badgeStrokeC
                strokePaint.strokeWidth = 1.25f * d
                c.drawRoundRect(composingRect, pillRadius, pillRadius, strokePaint)

                val display = if (composing.isEmpty()) "✏️ Note লিখুন…" else composing
                drawFittedPillText(
                    c = c,
                    text = display,
                    rect = composingRect,
                    color = if (composing.isEmpty()) hintC else badgeTextC,
                    bold = composing.isNotEmpty(),
                    preferredSp = 14.5f,
                    truncateAt = if (composing.length > 18) TextUtils.TruncateAt.START else TextUtils.TruncateAt.END
                )
            }
            if (!cancelEditRect.isEmpty) {
                val pressed = isStripPressed(StripHit.CancelNoteEdit)
                paint.style = Paint.Style.FILL
                paint.color = if (pressed) pressC else actionBgC
                c.drawRoundRect(cancelEditRect, pillRadius, pillRadius, paint)
                strokePaint.color = pillStrokeC
                strokePaint.strokeWidth = 0.9f * d
                c.drawRoundRect(cancelEditRect, pillRadius, pillRadius, strokePaint)
                drawFittedPillText(c, "✕", cancelEditRect, textC, bold = true, preferredSp = 15f)
            }
            if (!saveEditRect.isEmpty) {
                val pressed = isStripPressed(StripHit.SaveNoteEdit)
                paint.style = Paint.Style.FILL
                paint.color = if (pressed) pressC else accentC
                c.drawRoundRect(saveEditRect, pillRadius, pillRadius, paint)
                drawFittedPillText(
                    c,
                    "✓ Save",
                    saveEditRect,
                    if (pressed) textC else theme.onAccent,
                    bold = true,
                    preferredSp = 14f
                )
            }
            return
        }

        // ১) Composing Badge (থাকলে)
        if (!composingRect.isEmpty && composing.isNotEmpty()) {
            val pressed = isStripPressed(StripHit.Composing)
            val useSecretColors = secretModeActive || composing.startsWith("🔐")
            paint.style = Paint.Style.FILL
            paint.color = when {
                pressed -> pressC
                useSecretColors -> secretBadgeBgC
                else -> badgeBgC
            }
            c.drawRoundRect(composingRect, pillRadius, pillRadius, paint)

            strokePaint.color = if (useSecretColors) secretBadgeStrokeC else badgeStrokeC
            strokePaint.strokeWidth = 1.25f * d
            c.drawRoundRect(composingRect, pillRadius, pillRadius, strokePaint)

            drawFittedPillText(
                c = c,
                text = composing,
                rect = composingRect,
                color = when {
                    pressed -> textC
                    useSecretColors -> secretBadgeTextC
                    else -> badgeTextC
                },
                bold = true,
                preferredSp = 15.5f,
                truncateAt = if (useSecretColors && composing.length > 18) {
                    TextUtils.TruncateAt.START
                } else {
                    TextUtils.TruncateAt.END
                }
            )
        }

        // ২) Quick Action Shortcut Pills (Idle মোডে)
        for ((action, rect) in quickActionRects) {
            val pressed = isStripPressed(StripHit.Action(action))
            paint.style = Paint.Style.FILL
            paint.color = if (pressed) pressC else actionBgC
            c.drawRoundRect(rect, 11f * d, 11f * d, paint)

            val cx = rect.centerX()
            val cy = rect.centerY()
            val fg = if (action == QuickAction.ASSISTANT) accentC else textC
            when (action) {
                QuickAction.NOTEPAD -> drawNotepadIcon(c, cx, cy, fg, 0.88f)
                QuickAction.TRANSLATE -> drawTranslateIcon(c, cx, cy, fg)
                QuickAction.VOICE -> drawMicIcon(c, cx, cy, fg, 0.88f)
                QuickAction.ASSISTANT -> drawAssistantIcon(c, cx, cy, fg, 0.88f)
            }
        }

        // ৩) Suggestion Word Pills
        for (i in suggestionRects.indices) {
            if (i >= suggestions.size) break
            val rect = suggestionRects[i]
            val pressed = isStripPressed(StripHit.Word(i))

            paint.style = Paint.Style.FILL
            if (!pressed) {
                paint.color = shadowC
                paint.alpha = 70
                c.drawRoundRect(
                    rect.left, rect.top + 1.1f * d, rect.right, rect.bottom + 1.1f * d,
                    pillRadius, pillRadius, paint
                )
                paint.alpha = 255
            }
            paint.color = if (pressed) pressC else pillC
            c.drawRoundRect(rect, pillRadius, pillRadius, paint)

            strokePaint.color = pillStrokeC
            strokePaint.strokeWidth = 0.8f * d
            c.drawRoundRect(rect, pillRadius, pillRadius, strokePaint)

            drawFittedPillText(
                c = c,
                text = suggestions[i],
                rect = rect,
                color = textC,
                bold = false,
                preferredSp = 15f
            )
        }
    }

    private fun drawFittedPillText(
        c: Canvas,
        text: String,
        rect: RectF,
        color: Int,
        bold: Boolean,
        preferredSp: Float,
        truncateAt: TextUtils.TruncateAt = TextUtils.TruncateAt.END
    ) {
        val availW = (rect.width() - 12f * d).coerceAtLeast(8f * d)
        stripTextPaint.color = color
        stripTextPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        stripTextPaint.textAlign = Paint.Align.CENTER

        var size = preferredSp * fontD
        val minSize = 11.5f * fontD
        stripTextPaint.textSize = size
        while (size > minSize && stripTextPaint.measureText(text) > availW) {
            size -= 1f * d
            stripTextPaint.textSize = size
        }

        val label = TextUtils.ellipsize(text, stripTextPaint, availW, truncateAt).toString()
        val fm = stripTextPaint.fontMetrics
        val y = rect.centerY() - (fm.ascent + fm.descent) / 2f
        c.drawText(label, rect.centerX(), y, stripTextPaint)
    }

    private fun isPressed(b: Box): Boolean {
        for (i in 0 until ptrs.size()) if (ptrs.valueAt(i).box === b) return true
        return false
    }

    private fun displayLabel(k: Key): String =
        if (k.type == KeyType.CHAR && shift != SHIFT_OFF) k.label.uppercase() else k.label

    private fun drawKey(c: Canvas, b: Box) {
        val k = b.key
        if (k.type == KeyType.SPACER) {
            if (k.label.isNotEmpty()) {
                drawCentered(c, k.label, b.rect.centerX(), b.rect.centerY(), b.rect.height() * 0.30f, hintC)
            }
            return
        }

        val pressed = isPressed(b)
        val isEnter = k.type == KeyType.ENTER
        val shiftActive = k.type == KeyType.SHIFT && shift != SHIFT_OFF
        val secretActiveKey = k.type == KeyType.SECRET && secretModeActive
        val functional = !(k.type == KeyType.CHAR || k.type == KeyType.SPACE || k.type == KeyType.SNIPPET)

        val fill = when {
            pressed -> pressC
            isEnter || shiftActive || secretActiveKey -> accentC
            functional -> fnC
            else -> keyC
        }

        drawKeyBody(c, b, fill, pressed, functional, isEnter || shiftActive || secretActiveKey)

        val cx = b.rect.centerX()
        val cy = b.rect.centerY()
        val rh = b.rect.height()
        val onAccent = isEnter || shiftActive || secretActiveKey
        val fg = if (onAccent) theme.onAccent else textC

        when (k.type) {
            KeyType.SHIFT -> drawShiftIcon(c, cx, cy, fg, shift != SHIFT_OFF, shift == SHIFT_CAPS)
            KeyType.DELETE -> drawBackspaceIcon(c, cx, cy, fg)
            KeyType.ENTER -> {
                when {
                    secretModeActive -> drawCentered(c, "🔒 Send", cx, cy, rh * 0.28f, fg)
                    noteEditModeActive -> drawCentered(c, "✓ Save", cx, cy, rh * 0.28f, fg)
                    else -> {
                        val el = enterLabel
                        when {
                            el == null -> drawEnterIcon(c, cx, cy, fg)
                            el == "SEARCH" -> drawSearchIcon(c, cx, cy, fg)
                            else -> drawCentered(c, el, cx, cy, rh * 0.32f, fg)
                        }
                    }
                }
            }
            KeyType.SPACE -> drawCentered(c, spaceLabel, cx, cy, rh * 0.30f, hintC)
            KeyType.CHAR -> {
                drawCentered(c, displayLabel(k), cx, cy, rh * 0.46f, textC)
                val alt = k.alt
                if (alt != null) {
                    textPaint.textSize = rh * 0.24f
                    textPaint.color = hintC
                    textPaint.textAlign = Paint.Align.RIGHT
                    c.drawText(alt, b.rect.right - 5f * d, b.rect.top + rh * 0.24f + 3f * d, textPaint)
                }
            }
            KeyType.LANG -> drawGlobeIcon(c, cx, cy, textC)
            KeyType.NOTEPAD -> drawNotepadIcon(c, cx, cy, textC)
            KeyType.MIC -> drawMicIcon(c, cx, cy, textC)
            KeyType.ASSISTANT -> drawAssistantIcon(c, cx, cy, accentC)
            KeyType.SECRET -> drawLockIcon(c, cx, cy, if (secretActiveKey) fg else accentC, secretModeActive)
            else -> drawCentered(c, k.label, cx, cy, rh * 0.30f, textC)
        }
    }

    // ---------------- theme-aware key body ----------------

    private fun drawKeyBody(c: Canvas, b: Box, fill: Int, pressed: Boolean, functional: Boolean, onAccent: Boolean) {
        val t = theme
        val rect = b.rect
        val r = radius
        paint.style = Paint.Style.FILL

        when (t.style) {
            KeyStyle.PIXEL -> drawPixelKey(c, rect, fill, pressed)
            KeyStyle.BLOCK -> drawBlockKey(c, b, fill, pressed, functional, onAccent)
            KeyStyle.NEON -> {
                paint.color = fill
                c.drawRoundRect(rect, r, r, paint)
                val glowA = if (pressed) 150 else 70
                val col = if (onAccent) accentC else t.border
                strokePaint.color = withAlpha(col, glowA)
                strokePaint.strokeWidth = (t.borderDp + 3.2f) * d
                c.drawRoundRect(rect, r, r, strokePaint)
                strokePaint.color = col
                strokePaint.strokeWidth = t.borderDp * d
                c.drawRoundRect(rect, r, r, strokePaint)
            }
            else -> {
                // FLAT / GLASS / GRADIENT
                if (!pressed && t.shadowDp > 0f && t.style != KeyStyle.GLASS) {
                    paint.color = shadowC
                    c.drawRoundRect(
                        rect.left, rect.top + t.shadowDp * d, rect.right, rect.bottom + t.shadowDp * d,
                        r, r, paint
                    )
                }
                if (t.style == KeyStyle.GRADIENT && t.keyTop != 0 && !pressed && !functional && !onAccent) {
                    paint.shader = LinearGradient(
                        rect.left, rect.top, rect.left, rect.bottom,
                        t.keyTop, t.keyBottom, Shader.TileMode.CLAMP
                    )
                    paint.color = 0xFFFFFFFF.toInt()
                    c.drawRoundRect(rect, r, r, paint)
                    paint.shader = null
                } else {
                    paint.color = fill
                    c.drawRoundRect(rect, r, r, paint)
                }
                if (t.borderDp > 0f && t.border != 0) {
                    val half = t.borderDp * d / 2f
                    innerRect.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half)
                    strokePaint.color = t.border
                    strokePaint.strokeWidth = t.borderDp * d
                    c.drawRoundRect(innerRect, r, r, strokePaint)
                }
            }
        }
    }

    /** Retro pixel key: মোটা outline + উপর/বামে highlight + নিচ/ডানে shade */
    private fun drawPixelKey(c: Canvas, rect: RectF, fill: Int, pressed: Boolean) {
        val t = theme
        val o = max(1.5f, t.borderDp) * d
        val r = radius
        if (!pressed) {
            paint.color = shadowC
            c.drawRoundRect(rect.left, rect.top + 2.2f * d, rect.right, rect.bottom + 2.2f * d, r, r, paint)
        }
        paint.color = t.border
        c.drawRoundRect(rect, r, r, paint)

        innerRect.set(rect.left + o, rect.top + o, rect.right - o, rect.bottom - o)
        paint.color = fill
        c.drawRoundRect(innerRect, r * 0.6f, r * 0.6f, paint)

        val bw = 2f * d
        val light = if (pressed) mixColor(fill, 0xFF000000.toInt(), 0.15f) else mixColor(fill, 0xFFFFFFFF.toInt(), 0.40f)
        val dark = if (pressed) mixColor(fill, 0xFF000000.toInt(), 0.10f) else mixColor(fill, 0xFF000000.toInt(), 0.28f)
        paint.color = light
        c.drawRect(innerRect.left + bw, innerRect.top, innerRect.right - bw, innerRect.top + bw, paint)
        c.drawRect(innerRect.left, innerRect.top + bw, innerRect.left + bw, innerRect.bottom - bw, paint)
        paint.color = dark
        c.drawRect(innerRect.left + bw, innerRect.bottom - bw, innerRect.right - bw, innerRect.bottom, paint)
        c.drawRect(innerRect.right - bw, innerRect.top + bw, innerRect.right, innerRect.bottom - bw, paint)
    }

    /** Block key: উপরে ঘাসের স্তর (এবড়োখেবড়ো ধার), নিচে মাটি ও ছোট ছোট দাগ — সব code দিয়ে আঁকা */
    private fun drawBlockKey(c: Canvas, b: Box, fill: Int, pressed: Boolean, functional: Boolean, onAccent: Boolean) {
        val t = theme
        val rect = b.rect
        val r = radius

        if (!pressed) {
            paint.color = shadowC
            c.drawRoundRect(rect.left, rect.top + t.shadowDp * d, rect.right, rect.bottom + t.shadowDp * d, r, r, paint)
        }
        paint.color = t.border
        c.drawRoundRect(rect, r, r, paint)
        val o = t.borderDp * d
        innerRect.set(rect.left + o, rect.top + o, rect.right - o, rect.bottom - o)
        paint.color = fill
        c.drawRoundRect(innerRect, r * 0.7f, r * 0.7f, paint)

        if (functional || onAccent) return

        val seed = (b.key.label.hashCode() and 0x7fffffff) % 97
        val grass = if (pressed) mixColor(t.keyTop, 0xFF000000.toInt(), 0.25f) else t.keyTop
        val dirtDark = mixColor(fill, 0xFF000000.toInt(), 0.20f)

        c.save()
        path.reset()
        path.addRoundRect(innerRect, r * 0.7f, r * 0.7f, Path.Direction.CW)
        c.clipPath(path)

        val gh = innerRect.height() * 0.28f
        paint.color = grass
        c.drawRect(innerRect.left, innerRect.top, innerRect.right, innerRect.top + gh, paint)
        val cols = 7
        val step = innerRect.width() / cols
        for (i in 0 until cols) {
            val extra = ((i * 37 + seed) % 3) * gh * 0.32f
            if (extra > 0f) {
                c.drawRect(
                    innerRect.left + i * step, innerRect.top + gh,
                    innerRect.left + (i + 1) * step + 0.5f, innerRect.top + gh + extra, paint
                )
            }
        }

        paint.color = dirtDark
        val px = max(2f * d, innerRect.width() / 9f)
        val dirtTop = innerRect.top + gh + gh * 0.7f
        val dirtH = (innerRect.bottom - dirtTop - px).coerceAtLeast(1f)
        val dirtW = (innerRect.width() - px).coerceAtLeast(1f)
        for (i in 0 until 5) {
            val fx = ((i * 53 + seed * 7) % 100) / 100f
            val fy = ((i * 29 + seed * 3) % 100) / 100f
            val sx = innerRect.left + fx * dirtW
            val sy = dirtTop + fy * dirtH
            c.drawRect(sx, sy, sx + px, sy + px, paint)
        }
        c.restore()
    }

    private fun drawCentered(c: Canvas, text: String, cx: Float, cy: Float, size: Float, color: Int) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.textAlign = Paint.Align.CENTER
        val fm = textPaint.fontMetrics
        c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint)
    }

    private fun drawShiftIcon(c: Canvas, cx: Float, cy: Float, color: Int, filled: Boolean, caps: Boolean) {
        val s = 9f * d
        path.reset()
        path.moveTo(cx, cy - s)
        path.lineTo(cx + s, cy)
        path.lineTo(cx + s * 0.45f, cy)
        path.lineTo(cx + s * 0.45f, cy + s * 0.9f)
        path.lineTo(cx - s * 0.45f, cy + s * 0.9f)
        path.lineTo(cx - s * 0.45f, cy)
        path.lineTo(cx - s, cy)
        path.close()
        iconPaint.color = color
        iconPaint.strokeWidth = 1.8f * d
        iconPaint.style = if (filled) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        c.drawPath(path, iconPaint)
        if (caps) {
            iconPaint.style = Paint.Style.STROKE
            c.drawLine(cx - s * 0.45f, cy + s * 1.3f, cx + s * 0.45f, cy + s * 1.3f, iconPaint)
        }
    }

    private fun drawBackspaceIcon(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = 9f * d
        path.reset()
        path.moveTo(cx - s * 1.1f, cy)
        path.lineTo(cx - s * 0.45f, cy - s * 0.8f)
        path.lineTo(cx + s * 1.1f, cy - s * 0.8f)
        path.lineTo(cx + s * 1.1f, cy + s * 0.8f)
        path.lineTo(cx - s * 0.45f, cy + s * 0.8f)
        path.close()
        iconPaint.color = color
        iconPaint.strokeWidth = 1.8f * d
        iconPaint.style = Paint.Style.STROKE
        c.drawPath(path, iconPaint)
        c.drawLine(cx - s * 0.1f, cy - s * 0.35f, cx + s * 0.6f, cy + s * 0.35f, iconPaint)
        c.drawLine(cx + s * 0.6f, cy - s * 0.35f, cx - s * 0.1f, cy + s * 0.35f, iconPaint)
    }

    private fun drawEnterIcon(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = 9f * d
        iconPaint.color = color
        iconPaint.strokeWidth = 2f * d
        iconPaint.style = Paint.Style.STROKE
        path.reset()
        path.moveTo(cx + s * 0.9f, cy - s * 0.8f)
        path.lineTo(cx + s * 0.9f, cy + s * 0.2f)
        path.lineTo(cx - s * 0.9f, cy + s * 0.2f)
        c.drawPath(path, iconPaint)
        c.drawLine(cx - s * 0.9f, cy + s * 0.2f, cx - s * 0.25f, cy - s * 0.4f, iconPaint)
        c.drawLine(cx - s * 0.9f, cy + s * 0.2f, cx - s * 0.25f, cy + s * 0.8f, iconPaint)
    }

    private fun drawGlobeIcon(c: Canvas, cx: Float, cy: Float, color: Int) {
        val r = 8.5f * d
        iconPaint.color = color
        iconPaint.strokeWidth = 1.6f * d
        iconPaint.style = Paint.Style.STROKE
        c.drawCircle(cx, cy, r, iconPaint)
        c.drawLine(cx - r, cy, cx + r, cy, iconPaint)
        c.drawOval(cx - r * 0.46f, cy - r, cx + r * 0.46f, cy + r, iconPaint)
    }

    private fun drawNotepadIcon(c: Canvas, cx: Float, cy: Float, color: Int, scale: Float = 1f) {
        val w = 6.8f * d * scale
        val h = 8.4f * d * scale
        iconPaint.color = color
        iconPaint.strokeWidth = 1.6f * d
        iconPaint.style = Paint.Style.STROKE
        c.drawRoundRect(cx - w, cy - h, cx + w, cy + h, 2f * d, 2f * d, iconPaint)
        c.drawLine(cx - w * 0.55f, cy - h * 0.35f, cx + w * 0.55f, cy - h * 0.35f, iconPaint)
        c.drawLine(cx - w * 0.55f, cy, cx + w * 0.55f, cy, iconPaint)
        c.drawLine(cx - w * 0.55f, cy + h * 0.35f, cx + w * 0.2f, cy + h * 0.35f, iconPaint)
    }

    private fun drawTranslateIcon(c: Canvas, cx: Float, cy: Float, color: Int) {
        val s = 7.4f * d
        iconPaint.color = color
        iconPaint.strokeWidth = 1.6f * d
        iconPaint.style = Paint.Style.STROKE
        // ডানমুখী উপরের তীর
        c.drawLine(cx - s * 0.9f, cy - s * 0.38f, cx + s * 0.85f, cy - s * 0.38f, iconPaint)
        c.drawLine(cx + s * 0.35f, cy - s * 0.82f, cx + s * 0.85f, cy - s * 0.38f, iconPaint)
        c.drawLine(cx + s * 0.35f, cy + s * 0.06f, cx + s * 0.85f, cy - s * 0.38f, iconPaint)
        // বামমুখী নিচের তীর
        c.drawLine(cx + s * 0.9f, cy + s * 0.42f, cx - s * 0.85f, cy + s * 0.42f, iconPaint)
        c.drawLine(cx - s * 0.35f, cy - s * 0.02f, cx - s * 0.85f, cy + s * 0.42f, iconPaint)
        c.drawLine(cx - s * 0.35f, cy + s * 0.86f, cx - s * 0.85f, cy + s * 0.42f, iconPaint)
    }

    private fun drawMicIcon(c: Canvas, cx: Float, cy: Float, color: Int, scale: Float = 1f) {
        val s = 8.5f * d * scale
        iconPaint.color = color
        iconPaint.strokeWidth = 1.65f * d
        iconPaint.style = Paint.Style.STROKE
        c.drawRoundRect(cx - s * 0.34f, cy - s * 0.95f, cx + s * 0.34f, cy + s * 0.2f, s * 0.34f, s * 0.34f, iconPaint)
        c.drawArc(cx - s * 0.68f, cy - s * 0.45f, cx + s * 0.68f, cy + s * 0.55f, 0f, 180f, false, iconPaint)
        c.drawLine(cx, cy + s * 0.55f, cx, cy + s * 0.95f, iconPaint)
        c.drawLine(cx - s * 0.45f, cy + s * 0.95f, cx + s * 0.45f, cy + s * 0.95f, iconPaint)
    }

    private fun drawAssistantIcon(c: Canvas, cx: Float, cy: Float, color: Int, scale: Float = 1f) {
        val s = 8.5f * d * scale
        iconPaint.color = color
        iconPaint.strokeWidth = 1.7f * d
        iconPaint.style = Paint.Style.STROKE
        c.drawLine(cx, cy - s * 1.0f, cx, cy - s * 0.68f, iconPaint)
        c.drawRoundRect(cx - s * 0.9f, cy - s * 0.68f, cx + s * 0.9f, cy + s * 0.78f, 3.2f * d, 3.2f * d, iconPaint)
        iconPaint.style = Paint.Style.FILL
        c.drawCircle(cx, cy - s * 1.0f, 1.3f * d, iconPaint)
        c.drawCircle(cx - s * 0.36f, cy - s * 0.08f, 1.35f * d, iconPaint)
        c.drawCircle(cx + s * 0.36f, cy - s * 0.08f, 1.35f * d, iconPaint)
        iconPaint.style = Paint.Style.STROKE
        c.drawLine(cx - s * 0.32f, cy + s * 0.38f, cx + s * 0.32f, cy + s * 0.38f, iconPaint)
    }

    private fun drawSearchIcon(c: Canvas, cx: Float, cy: Float, color: Int) {
        val r = 5.8f * d
        iconPaint.color = color
        iconPaint.strokeWidth = 2f * d
        iconPaint.style = Paint.Style.STROKE
        c.drawCircle(cx - 1.5f * d, cy - 1.5f * d, r, iconPaint)
        c.drawLine(cx + 2.6f * d, cy + 2.6f * d, cx + 7.2f * d, cy + 7.2f * d, iconPaint)
    }

    private fun drawLockIcon(c: Canvas, cx: Float, cy: Float, color: Int, locked: Boolean) {
        val w = 6.4f * d
        val h = 5.2f * d
        iconPaint.color = color
        iconPaint.strokeWidth = 1.7f * d
        iconPaint.style = Paint.Style.STROKE
        // শিকল / আর্চ
        c.drawArc(
            cx - w * 0.62f,
            cy - h * 1.45f,
            cx + w * 0.62f,
            cy - h * 0.1f,
            if (locked) 180f else 200f,
            if (locked) 180f else 135f,
            false,
            iconPaint
        )
        // লকের বডি
        iconPaint.style = if (locked) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        c.drawRoundRect(
            cx - w,
            cy - h * 0.25f,
            cx + w,
            cy + h * 1.05f,
            2.2f * d,
            2.2f * d,
            iconPaint
        )
    }

    private fun drawPopups(c: Canvas) {
        for (i in 0 until ptrs.size()) {
            val p = ptrs.valueAt(i)
            val b = p.box ?: continue
            if (b.key.type != KeyType.CHAR || p.consumed) continue

            val bw = b.rect.width() * 1.35f
            val bh = min(rowH() * 1.05f, 54f * d)
            val left = (b.rect.centerX() - bw / 2f).coerceIn(2f * d, max(2f * d, width - bw - 2f * d))
            val top = max(0f, b.rect.top - 2f * d - bh)
            popupRect.set(left, top, left + bw, top + bh)

            paint.style = Paint.Style.FILL
            paint.color = shadowC
            c.drawRoundRect(popupRect.left, popupRect.top + 2f * d, popupRect.right, popupRect.bottom + 2f * d, radius * 1.4f, radius * 1.4f, paint)
            paint.color = popupC
            c.drawRoundRect(popupRect, radius * 1.4f, radius * 1.4f, paint)
            drawCentered(c, displayLabel(b.key), popupRect.centerX(), popupRect.centerY(), bh * 0.55f, textC)
        }
    }

    // ---------------- touch ----------------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                onDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) onMove(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                onUp(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAll()
                invalidate()
            }
        }
        return true
    }

    private fun hitTestStrip(x: Float, y: Float): StripHit? {
        if (y < 0f || y >= stripH) return null
        if (stripDirty && width > 0) updateStripRects()

        if (noteEditModeActive) {
            if (!cancelEditRect.isEmpty && x in cancelEditRect.left..cancelEditRect.right) {
                return StripHit.CancelNoteEdit
            }
            if (!saveEditRect.isEmpty && x in saveEditRect.left..saveEditRect.right) {
                return StripHit.SaveNoteEdit
            }
            return null
        }

        // পূর্ণ 48dp touch height নিশ্চিত করতে horizontal range দিয়েও match করা হয়
        if (!composingRect.isEmpty && x in composingRect.left..composingRect.right) {
            return StripHit.Composing
        }
        for ((action, rect) in quickActionRects) {
            if (x in rect.left..rect.right) return StripHit.Action(action)
        }
        for (i in suggestionRects.indices) {
            val r = suggestionRects[i]
            if (x in r.left..r.right) return StripHit.Word(i)
        }
        return null
    }

    private fun findKey(x: Float, y: Float): Box? {
        if (boxRows.isEmpty()) return null
        val rh = rowH()
        val ri = ((y - stripH - vPad) / rh).toInt().coerceIn(0, boxRows.size - 1)
        var best: Box? = null
        var bestDist = Float.MAX_VALUE
        for (b in boxRows[ri]) {
            if (b.key.type == KeyType.SPACER) continue
            val dist = max(0f, abs(x - b.rect.centerX()) - b.rect.width() / 2f)
            if (dist < bestDist) {
                bestDist = dist
                best = b
            }
        }
        return best
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        if (y < stripH) {
            val hit = hitTestStrip(x, y)
            val p = Ptr(null, inStrip = true, stripHit = hit)
            ptrs.put(id, p)
            if (hit is StripHit.Word) {
                scheduleStripLong(p, hit.index)
            }
            invalidate()
            return
        }
        val b = findKey(x, y)
        val p = Ptr(b, inStrip = false)
        ptrs.put(id, p)
        if (b != null) {
            listener?.onFeedback()
            when (b.key.type) {
                KeyType.DELETE -> {
                    p.consumed = true
                    listener?.onDelete()
                    scheduleRepeat(p)
                }
                KeyType.CHAR -> if (b.key.alt != null) scheduleLong(p)
                KeyType.SPACE -> scheduleLong(p)
                else -> {}
            }
        }
        invalidate()
    }

    private fun onMove(id: Int, x: Float, y: Float) {
        val p = ptrs.get(id) ?: return
        if (p.consumed) return
        if (p.inStrip) {
            val newHit = hitTestStrip(x, y)
            if (newHit != p.stripHit) {
                clearRunnable(p)
                p.stripHit = null
                invalidate()
            }
            return
        }
        val cur = p.box ?: return
        if (cur.key.type != KeyType.CHAR) return
        val nb = findKey(x, y) ?: return
        if (nb !== cur && nb.key.type == KeyType.CHAR) {
            clearRunnable(p)
            p.box = nb
            if (nb.key.alt != null) scheduleLong(p)
            invalidate()
        }
    }

    private fun onUp(id: Int, x: Float, y: Float) {
        val p = ptrs.get(id) ?: return
        clearRunnable(p)
        ptrs.remove(id)
        if (p.inStrip) {
            val hit = hitTestStrip(x, y)
            if (!p.consumed && hit != null && hit == p.stripHit) {
                when (hit) {
                    StripHit.Composing -> {
                        listener?.onFeedback()
                        listener?.onComposingTap()
                    }
                    StripHit.CancelNoteEdit -> {
                        listener?.onFeedback()
                        listener?.onNoteEditCancel()
                    }
                    StripHit.SaveNoteEdit -> {
                        listener?.onFeedback()
                        listener?.onNoteEditSave()
                    }
                    is StripHit.Word -> {
                        val word = suggestions.getOrNull(hit.index)
                        if (word != null) {
                            listener?.onFeedback()
                            listener?.onSuggestionTap(word)
                        }
                    }
                    is StripHit.Action -> {
                        listener?.onFeedback()
                        when (hit.action) {
                            QuickAction.NOTEPAD -> listener?.onNotepad()
                            QuickAction.TRANSLATE -> listener?.onTranslate()
                            QuickAction.VOICE -> listener?.onVoice()
                            QuickAction.ASSISTANT -> listener?.onAssistant()
                        }
                    }
                }
            }
        } else {
            val b = p.box
            if (b != null && !p.consumed) fire(b.key)
        }
        invalidate()
    }

    private fun scheduleStripLong(p: Ptr, wordIndex: Int) {
        val r = Runnable {
            val word = suggestions.getOrNull(wordIndex)
            if (word != null) {
                p.consumed = true
                p.stripHit = null
                listener?.onFeedback()
                listener?.onSuggestionLongPress(word)
                invalidate()
            }
        }
        p.runnable = r
        handler.postDelayed(r, 420L)
    }

    private fun scheduleRepeat(p: Ptr) {
        val r = object : Runnable {
            override fun run() {
                listener?.onDelete()
                handler.postDelayed(this, 45L)
            }
        }
        p.runnable = r
        handler.postDelayed(r, 380L)
    }

    private fun scheduleLong(p: Ptr) {
        val r = Runnable {
            val b = p.box
            if (b != null) {
                p.consumed = true
                when (b.key.type) {
                    KeyType.CHAR -> b.key.alt?.let { listener?.onText(it) }
                    KeyType.SPACE -> listener?.onSpaceLongPress()
                    else -> {}
                }
                listener?.onFeedback()
                invalidate()
            }
        }
        p.runnable = r
        handler.postDelayed(r, 330L)
    }

    private fun clearRunnable(p: Ptr) {
        p.runnable?.let { handler.removeCallbacks(it) }
        p.runnable = null
    }

    fun cancelAll() {
        for (i in 0 until ptrs.size()) clearRunnable(ptrs.valueAt(i))
        ptrs.clear()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    private fun fire(k: Key) {
        val l = listener ?: return
        when (k.type) {
            KeyType.CHAR -> {
                l.onText(displayLabel(k))
                if (shift == SHIFT_ON) shift = SHIFT_OFF
            }
            KeyType.SPACE -> l.onSpace()
            KeyType.ENTER -> l.onEnter()
            KeyType.LANG -> l.onSwitchLang()
            KeyType.SYMBOLS -> l.onSymbols(1)
            KeyType.SYMBOLS2 -> l.onSymbols(2)
            KeyType.ABC -> l.onSymbols(0)
            KeyType.SNIPPET -> l.onSnippet(k.alt ?: k.label)
            KeyType.NOTEPAD -> l.onNotepad()
            KeyType.TRANSLATE -> l.onTranslate()
            KeyType.MIC -> l.onVoice()
            KeyType.ASSISTANT -> l.onAssistant()
            KeyType.SECRET -> l.onSecretToggle()
            KeyType.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = when {
                    shift == SHIFT_CAPS -> SHIFT_OFF
                    shift == SHIFT_ON && now - lastShiftTap < 350L -> SHIFT_CAPS
                    shift == SHIFT_ON -> SHIFT_OFF
                    else -> SHIFT_ON
                }
                lastShiftTap = now
            }
            else -> {}
        }
    }

    companion object {
        private const val SHIFT_OFF = 0
        private const val SHIFT_ON = 1
        private const val SHIFT_CAPS = 2
    }
}
