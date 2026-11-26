package com.banglakeys.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * কিবোর্ডের press effects (ripple / glow / flash) + popup "pop" animation।
 *
 * নকশার নিয়ম — typing-এর accuracy ও speed অক্ষুণ্ণ রাখতে:
 *  • শুধু *overlay* আঁকে; key-র আকার, hit-test, touch handling, listener কিছুতেই হাত দেয় না।
 *  • effect শুরু হয় onFeedback()-এর পরে, key commit-এর পথে কোনো কাজ যোগ হয় না।
 *  • draw-এর সময় কোনো object তৈরি হয় না (পূর্বনির্ধারিত Paint/RectF, fixed slot array) → GC jank নেই।
 *  • effect ছোট (১৫০–২৮০ms), সর্বোচ্চ [MAX] টা একসাথে; শুধু সক্রিয় অংশের dirty rect invalidate।
 *  • PerfRecorder ধীর হলে নিজে বন্ধ হয়ে যায় (EffectsConfig.autoDisable)।
 */
class KeyEffects(private val d: Float) {

    enum class Kind { NONE, RIPPLE, GLOW, FLASH }

    var kind: Kind = Kind.RIPPLE
    var level: Int = 1

    val active: Boolean get() = level > 0 && kind != Kind.NONE

    private val left = FloatArray(MAX)
    private val top = FloatArray(MAX)
    private val right = FloatArray(MAX)
    private val bottom = FloatArray(MAX)
    private val cx = FloatArray(MAX)
    private val cy = FloatArray(MAX)
    private val start = LongArray(MAX) // 0 = ফাঁকা slot
    private var lastPress = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var hasDirty = false
    private var dl = 0f
    private var dt = 0f
    private var dr = 0f
    private var db = 0f

    fun onPress(l: Float, t: Float, r: Float, b: Float, x: Float, y: Float, now: Long) {
        if (level <= 0) return
        lastPress = now
        if (kind == Kind.NONE) return
        var slot = 0
        var oldest = Long.MAX_VALUE
        for (i in 0 until MAX) {
            if (start[i] == 0L) { slot = i; break }
            if (start[i] < oldest) { oldest = start[i]; slot = i }
        }
        left[slot] = l; top[slot] = t; right[slot] = r; bottom[slot] = b
        cx[slot] = x; cy[slot] = y
        start[slot] = now
    }

    /** true = এখনও animation চলছে (পরের frame চাই)। */
    fun draw(c: Canvas, now: Long, color: Int, radius: Float): Boolean {
        hasDirty = false
        if (!active) return false
        val dur = durationFor(level)
        val alphaMax = alphaFor(level)
        var any = false
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (i in 0 until MAX) {
            val s = start[i]
            if (s == 0L) continue
            val p = progress(now, s, dur)
            if (p >= 1f) { start[i] = 0L; continue }
            any = true
            val e = easeOut(p)
            val a = (alphaMax * (1f - p)).toInt().coerceIn(0, 255)
            paint.color = (color and 0x00FFFFFF) or (a shl 24)
            when (kind) {
                Kind.RIPPLE -> {
                    paint.style = Paint.Style.FILL
                    rect.set(left[i], top[i], right[i], bottom[i])
                    val maxR = min(hypot(rect.width(), rect.height()) * 0.75f, 90f * d)
                    c.save()
                    c.clipRect(rect)
                    c.drawCircle(cx[i], cy[i], maxR * e, paint)
                    c.restore()
                }
                Kind.GLOW -> {
                    val g = 2f * d * e
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = (1.2f + 1.8f * (1f - p)) * d
                    rect.set(left[i] - g, top[i] - g, right[i] + g, bottom[i] + g)
                    c.drawRoundRect(rect, radius + g, radius + g, paint)
                }
                Kind.FLASH -> {
                    paint.style = Paint.Style.FILL
                    rect.set(left[i], top[i], right[i], bottom[i])
                    c.drawRoundRect(rect, radius, radius, paint)
                }
                Kind.NONE -> {}
            }
            if (left[i] < l) l = left[i]
            if (top[i] < t) t = top[i]
            if (right[i] > r) r = right[i]
            if (bottom[i] > b) b = bottom[i]
        }
        if (any) {
            val padX = 24f * d
            val padY = 6f * d
            dl = l - padX; dt = t - 64f * d; dr = r + padX; db = b + padY // উপরে popup-এর জায়গাও
            hasDirty = true
        }
        return any
    }

    fun includeDirty(l: Float, t: Float, r: Float, b: Float) {
        if (!hasDirty) return
        if (l < dl) dl = l
        if (t < dt) dt = t
        if (r > dr) dr = r
        if (b > db) db = b
    }

    fun scheduleNext(v: View) {
        if (hasDirty) {
            v.postInvalidateOnAnimation(dl.toInt().coerceAtLeast(0), dt.toInt().coerceAtLeast(0), dr.toInt() + 1, db.toInt() + 1)
        } else {
            v.postInvalidateOnAnimation()
        }
    }

    // ---- popup "pop" (key-র উপরের bubble ৮৮% → ১০০%, ~৭০ms) ----
    fun popupScale(now: Long): Float {
        if (level <= 0 || lastPress == 0L) return 1f
        return 0.88f + 0.12f * easeOut(progress(now, lastPress, POPUP_MS))
    }

    fun popupAnimating(now: Long): Boolean = level > 0 && lastPress != 0L && now - lastPress < POPUP_MS

    fun clear() {
        for (i in 0 until MAX) start[i] = 0L
        lastPress = 0L
    }

    companion object {
        const val MAX = 6
        const val POPUP_MS = 70

        fun progress(now: Long, start: Long, durMs: Int): Float =
            ((now - start).toFloat() / durMs).coerceIn(0f, 1f)

        fun easeOut(p: Float): Float = 1f - (1f - p) * (1f - p)

        fun durationFor(level: Int): Int = when (level) {
            1 -> 150
            2 -> 210
            else -> 280
        }

        fun alphaFor(level: Int): Int = when (level) {
            1 -> 70
            2 -> 110
            else -> 150
        }

        /** theme-এ `effect` দেওয়া থাকলে সেটা, নইলে style দেখে। */
        fun kindFor(theme: KeyboardTheme): Kind = theme.effect ?: when (theme.style) {
            KeyStyle.NEON -> Kind.GLOW
            KeyStyle.PIXEL, KeyStyle.BLOCK -> Kind.FLASH
            else -> Kind.RIPPLE
        }
    }
}
