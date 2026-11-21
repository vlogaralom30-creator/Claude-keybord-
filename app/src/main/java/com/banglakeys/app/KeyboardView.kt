package com.banglakeys.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Canvas-এ সরাসরি আঁকা keyboard: কোনো child view নেই, তাই দ্রুত ও smooth।
 * Multi-touch আছে (দ্রুত টাইপ করলে আগের key ছাড়ার আগেই পরের key চাপা যায়)।
 */
class KeyboardView(context: Context) : View(context) {

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
        fun onSnippet(text: String)
        fun onNotepad()
        fun onTranslate()
        fun onVoice()
    }

    var listener: Listener? = null

    // ---- state ----
    private class Box(val key: Key, val rect: RectF)

    private class Ptr(var box: Box?, val inStrip: Boolean) {
        var consumed = false
        var runnable: Runnable? = null
    }

    private var rows: List<List<Key>> = emptyList()
    private var boxRows: List<List<Box>> = emptyList()
    private var composing = ""
    private var suggestions: List<String> = emptyList()
    private var suggestionRects: List<RectF> = emptyList()
    private var spaceLabel = ""
    private var enterLabel: String? = null
    private var rowScale = 1f

    private var shift = SHIFT_OFF
    private var lastShiftTap = 0L

    private val ptrs = SparseArray<Ptr>()
    private val handler = Handler(Looper.getMainLooper())

    // ---- drawing tools ----
    private val d = resources.displayMetrics.density
    private val fontD = d * resources.configuration.fontScale
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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

    private val stripH: Float get() = 46f * d
    private val vPad: Float get() = 4f * d
    private val hPad: Float get() = 3f * d
    private val gap: Float get() = 3.2f * d
    private val radius: Float get() = 6f * d

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
        invalidate()
    }

    fun setComposing(text: String) {
        if (composing != text) {
            composing = text
            invalidate()
        }
    }

    fun setSuggestions(list: List<String>) {
        if (suggestions != list) {
            suggestions = list
            if (list.isEmpty()) suggestionRects = emptyList()
            invalidate()
        }
    }

    fun setEnterLabel(label: String?) {
        enterLabel = label
        invalidate()
    }

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

    // ---------------- theme ----------------

    private fun applyTheme() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (night) {
            bgC = 0xFF1C1C1E.toInt()
            keyC = 0xFF3A3A3C.toInt()
            fnC = 0xFF2C2C2E.toInt()
            shadowC = 0xFF0E0E0F.toInt()
            textC = 0xFFF2F2F7.toInt()
            hintC = 0xFF9A9AA0.toInt()
            pressC = 0xFF636366.toInt()
            accentC = 0xFF26A69A.toInt()
            popupC = 0xFF4A4A4D.toInt()
        } else {
            bgC = 0xFFE6E9EC.toInt()
            keyC = 0xFFFFFFFF.toInt()
            fnC = 0xFFC9D0D6.toInt()
            shadowC = 0xFFA9B1B8.toInt()
            textC = 0xFF1F2328.toInt()
            hintC = 0xFF7A838B.toInt()
            pressC = 0xFFB4BDC5.toInt()
            accentC = 0xFF00897B.toInt()
            popupC = 0xFFFFFFFF.toInt()
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

    // ---------------- drawing ----------------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bgC)
        drawStrip(canvas)
        for (row in boxRows) for (b in row) drawKey(canvas, b)
        drawPopups(canvas)
    }

    private fun drawStrip(c: Canvas) {
        if (suggestions.isNotEmpty()) {
            drawSuggestions(c)
            return
        }
        if (composing.isEmpty()) return
        textPaint.textSize = 20f * fontD
        textPaint.color = textC
        val maxW = width - 24f * d
        val tw = textPaint.measureText(composing)
        val fm = textPaint.fontMetrics
        val y = stripH / 2f - (fm.ascent + fm.descent) / 2f
        if (tw > maxW) {
            textPaint.textAlign = Paint.Align.RIGHT
            c.drawText(composing, width - 12f * d, y, textPaint)
        } else {
            textPaint.textAlign = Paint.Align.CENTER
            c.drawText(composing, width / 2f, y, textPaint)
        }
    }

    private fun drawSuggestions(c: Canvas) {
        val n = suggestions.size
        if (n == 0) return
        val w = width / n.toFloat()
        val rects = ArrayList<RectF>(n)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 18f * fontD
        val fm = textPaint.fontMetrics
        val y = stripH / 2f - (fm.ascent + fm.descent) / 2f
        for (i in 0 until n) {
            val left = i * w
            val rect = RectF(left, 0f, left + w, stripH)
            rects.add(rect)
            textPaint.color = textC
            c.drawText(suggestions[i], rect.centerX(), y, textPaint)
            if (i > 0) {
                paint.color = shadowC
                paint.strokeWidth = 1f * d
                c.drawLine(left, stripH * 0.25f, left, stripH * 0.75f, paint)
            }
        }
        suggestionRects = rects
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
        val functional = !(k.type == KeyType.CHAR || k.type == KeyType.SPACE || k.type == KeyType.SNIPPET)

        val fill = when {
            pressed -> pressC
            isEnter || shiftActive -> accentC
            functional -> fnC
            else -> keyC
        }

        paint.style = Paint.Style.FILL
        if (!pressed) {
            paint.color = shadowC
            c.drawRoundRect(
                b.rect.left, b.rect.top + 1.3f * d, b.rect.right, b.rect.bottom + 1.3f * d,
                radius, radius, paint
            )
        }
        paint.color = fill
        c.drawRoundRect(b.rect, radius, radius, paint)

        val cx = b.rect.centerX()
        val cy = b.rect.centerY()
        val rh = b.rect.height()
        val onAccent = isEnter || shiftActive
        val fg = if (onAccent) 0xFFFFFFFF.toInt() else textC

        when (k.type) {
            KeyType.SHIFT -> drawShiftIcon(c, cx, cy, fg, shift != SHIFT_OFF, shift == SHIFT_CAPS)
            KeyType.DELETE -> drawBackspaceIcon(c, cx, cy, fg)
            KeyType.ENTER -> {
                val el = enterLabel
                if (el == null) drawEnterIcon(c, cx, cy, fg)
                else drawCentered(c, el, cx, cy, rh * 0.32f, fg)
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
            KeyType.LANG -> drawCentered(c, k.label, cx, cy, rh * 0.36f, textC)
            else -> drawCentered(c, k.label, cx, cy, rh * 0.30f, textC)
        }
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
            ptrs.put(id, Ptr(null, true))
            return
        }
        val b = findKey(x, y)
        val p = Ptr(b, false)
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
        if (p.inStrip || p.consumed) return
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
            if (y < stripH) {
                val idx = suggestionRects.indexOfFirst { it.contains(x, y) }
                if (suggestions.isNotEmpty() && idx >= 0) {
                    listener?.onFeedback()
                    listener?.onSuggestionTap(suggestions[idx])
                } else if (suggestions.isEmpty()) {
                    listener?.onComposingTap()
                }
            }
        } else {
            val b = p.box
            if (b != null && !p.consumed) fire(b.key)
        }
        invalidate()
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
