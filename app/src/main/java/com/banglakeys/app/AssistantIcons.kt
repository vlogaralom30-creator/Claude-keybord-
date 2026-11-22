package com.banglakeys.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Pure code-built Canvas vector icons (emoji ছাড়া crisp vector drawable)।
 * minSdk 24+ compatible, কোনো XML vector বা external library ছাড়াই।
 */
object AssistantIcons {

    enum class Kind {
        BOT,
        CHECK_CIRCLE,
        ALERT_CIRCLE,
        ACCESSIBILITY,
        APP_INFO,
        SHIELD,
        OVERLAY,
        PLAY,
        STOP,
        GLOBE,
        SWIPE_UP,
        FOLDER,
        ALARM,
        LOCK,
        MESSAGE,
        SPARKLE,
        TIMER,
        KEYBOARD,
        BACK
    }

    fun drawable(ctx: Context, kind: Kind, color: Int, sizeDp: Int = 20): Drawable {
        val px = (sizeDp * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        return VectorIconDrawable(kind, color, px)
    }

    private class VectorIconDrawable(
        private val kind: Kind,
        private val color: Int,
        private val sizePx: Int
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@VectorIconDrawable.color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = this@VectorIconDrawable.color
            style = Paint.Style.FILL
        }
        private val path = Path()
        private val rect = RectF()

        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val s = minOf(w, h)
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val u = s / 24f // 24x24 virtual viewBox

            strokePaint.strokeWidth = (1.9f * u).coerceAtLeast(1.5f)

            when (kind) {
                Kind.BOT -> {
                    // Antenna
                    canvas.drawLine(cx, cy - 9.5f * u, cx, cy - 6.5f * u, strokePaint)
                    canvas.drawCircle(cx, cy - 10f * u, 1.3f * u, fillPaint)
                    // Head rounded rect
                    rect.set(cx - 8f * u, cy - 6.5f * u, cx + 8f * u, cy + 7.5f * u)
                    canvas.drawRoundRect(rect, 3.5f * u, 3.5f * u, strokePaint)
                    // Side ears
                    canvas.drawLine(cx - 10f * u, cy - 1f * u, cx - 8f * u, cy - 1f * u, strokePaint)
                    canvas.drawLine(cx + 8f * u, cy - 1f * u, cx + 10f * u, cy - 1f * u, strokePaint)
                    // Eyes
                    canvas.drawCircle(cx - 3.2f * u, cy - 0.5f * u, 1.35f * u, fillPaint)
                    canvas.drawCircle(cx + 3.2f * u, cy - 0.5f * u, 1.35f * u, fillPaint)
                    // Voice mouth line
                    canvas.drawLine(cx - 3f * u, cy + 3.8f * u, cx + 3f * u, cy + 3.8f * u, strokePaint)
                }

                Kind.CHECK_CIRCLE -> {
                    canvas.drawCircle(cx, cy, 9f * u, strokePaint)
                    path.reset()
                    path.moveTo(cx - 4.2f * u, cy + 0.3f * u)
                    path.lineTo(cx - 1.2f * u, cy + 3.3f * u)
                    path.lineTo(cx + 4.5f * u, cy - 3.2f * u)
                    canvas.drawPath(path, strokePaint)
                }

                Kind.ALERT_CIRCLE -> {
                    canvas.drawCircle(cx, cy, 9f * u, strokePaint)
                    canvas.drawLine(cx, cy - 4.5f * u, cx, cy + 0.8f * u, strokePaint)
                    canvas.drawCircle(cx, cy + 4.2f * u, 1.15f * u, fillPaint)
                }

                Kind.ACCESSIBILITY -> {
                    canvas.drawCircle(cx, cy - 6.8f * u, 1.9f * u, fillPaint)
                    // Outstretched arms
                    canvas.drawLine(cx - 7.5f * u, cy - 2.5f * u, cx + 7.5f * u, cy - 2.5f * u, strokePaint)
                    // Torso + legs
                    canvas.drawLine(cx, cy - 2.5f * u, cx, cy + 3.2f * u, strokePaint)
                    canvas.drawLine(cx, cy + 3.2f * u, cx - 4.2f * u, cy + 9f * u, strokePaint)
                    canvas.drawLine(cx, cy + 3.2f * u, cx + 4.2f * u, cy + 9f * u, strokePaint)
                }

                Kind.APP_INFO -> {
                    canvas.drawCircle(cx, cy, 9f * u, strokePaint)
                    canvas.drawCircle(cx, cy - 4f * u, 1.15f * u, fillPaint)
                    canvas.drawLine(cx, cy - 0.5f * u, cx, cy + 5f * u, strokePaint)
                }

                Kind.SHIELD -> {
                    path.reset()
                    path.moveTo(cx, cy - 9.5f * u)
                    path.lineTo(cx + 7.5f * u, cy - 6.2f * u)
                    path.lineTo(cx + 7.5f * u, cy + 0.5f * u)
                    path.quadTo(cx + 7f * u, cy + 6.8f * u, cx, cy + 9.8f * u)
                    path.quadTo(cx - 7f * u, cy + 6.8f * u, cx - 7.5f * u, cy + 0.5f * u)
                    path.lineTo(cx - 7.5f * u, cy - 6.2f * u)
                    path.close()
                    canvas.drawPath(path, strokePaint)
                    path.reset()
                    path.moveTo(cx - 3.2f * u, cy + 0.2f * u)
                    path.lineTo(cx - 0.8f * u, cy + 2.6f * u)
                    path.lineTo(cx + 3.6f * u, cy - 2.4f * u)
                    canvas.drawPath(path, strokePaint)
                }

                Kind.OVERLAY -> {
                    rect.set(cx - 8.5f * u, cy - 8.5f * u, cx + 3.5f * u, cy + 3.5f * u)
                    canvas.drawRoundRect(rect, 2.2f * u, 2.2f * u, strokePaint)
                    rect.set(cx - 2.5f * u, cy - 2.5f * u, cx + 8.5f * u, cy + 8.5f * u)
                    canvas.drawRoundRect(rect, 2.2f * u, 2.2f * u, strokePaint)
                }

                Kind.PLAY -> {
                    path.reset()
                    path.moveTo(cx - 4.5f * u, cy - 6.8f * u)
                    path.lineTo(cx + 6.8f * u, cy)
                    path.lineTo(cx - 4.5f * u, cy + 6.8f * u)
                    path.close()
                    canvas.drawPath(path, fillPaint)
                }

                Kind.STOP -> {
                    rect.set(cx - 5.5f * u, cy - 5.5f * u, cx + 5.5f * u, cy + 5.5f * u)
                    canvas.drawRoundRect(rect, 1.8f * u, 1.8f * u, fillPaint)
                }

                Kind.GLOBE -> {
                    canvas.drawCircle(cx, cy, 8.8f * u, strokePaint)
                    canvas.drawLine(cx - 8.8f * u, cy, cx + 8.8f * u, cy, strokePaint)
                    rect.set(cx - 4.2f * u, cy - 8.8f * u, cx + 4.2f * u, cy + 8.8f * u)
                    canvas.drawOval(rect, strokePaint)
                }

                Kind.SWIPE_UP -> {
                    canvas.drawLine(cx, cy + 8f * u, cx, cy - 7f * u, strokePaint)
                    path.reset()
                    path.moveTo(cx - 4.5f * u, cy - 2.5f * u)
                    path.lineTo(cx, cy - 7.5f * u)
                    path.lineTo(cx + 4.5f * u, cy - 2.5f * u)
                    canvas.drawPath(path, strokePaint)
                    canvas.drawCircle(cx, cy + 8f * u, 2f * u, fillPaint)
                }

                Kind.FOLDER -> {
                    path.reset()
                    path.moveTo(cx - 9f * u, cy - 6f * u)
                    path.lineTo(cx - 2f * u, cy - 6f * u)
                    path.lineTo(cx + 0.5f * u, cy - 3.5f * u)
                    path.lineTo(cx + 9f * u, cy - 3.5f * u)
                    path.lineTo(cx + 9f * u, cy + 7f * u)
                    path.lineTo(cx - 9f * u, cy + 7f * u)
                    path.close()
                    canvas.drawPath(path, strokePaint)
                }

                Kind.ALARM -> {
                    canvas.drawCircle(cx, cy + 1f * u, 7.5f * u, strokePaint)
                    canvas.drawLine(cx - 7.5f * u, cy - 6.5f * u, cx - 4.5f * u, cy - 8.8f * u, strokePaint)
                    canvas.drawLine(cx + 7.5f * u, cy - 6.5f * u, cx + 4.5f * u, cy - 8.8f * u, strokePaint)
                    canvas.drawLine(cx, cy - 3.2f * u, cx, cy + 1f * u, strokePaint)
                    canvas.drawLine(cx, cy + 1f * u, cx + 3.2f * u, cy + 3f * u, strokePaint)
                }

                Kind.LOCK -> {
                    rect.set(cx - 6.5f * u, cy - 1f * u, cx + 6.5f * u, cy + 8.5f * u)
                    canvas.drawRoundRect(rect, 2.2f * u, 2.2f * u, strokePaint)
                    rect.set(cx - 4f * u, cy - 8f * u, cx + 4f * u, cy + 1f * u)
                    canvas.drawArc(rect, 180f, 180f, false, strokePaint)
                    canvas.drawCircle(cx, cy + 3.8f * u, 1.4f * u, fillPaint)
                }

                Kind.MESSAGE -> {
                    path.reset()
                    path.moveTo(cx - 8.5f * u, cy - 7f * u)
                    path.lineTo(cx + 8.5f * u, cy - 7f * u)
                    path.lineTo(cx + 8.5f * u, cy + 4.5f * u)
                    path.lineTo(cx - 3f * u, cy + 4.5f * u)
                    path.lineTo(cx - 7.5f * u, cy + 8.5f * u)
                    path.lineTo(cx - 7.5f * u, cy + 4.5f * u)
                    path.lineTo(cx - 8.5f * u, cy + 4.5f * u)
                    path.close()
                    canvas.drawPath(path, strokePaint)
                    canvas.drawLine(cx - 4.5f * u, cy - 2.5f * u, cx + 4.5f * u, cy - 2.5f * u, strokePaint)
                    canvas.drawLine(cx - 4.5f * u, cy + 0.8f * u, cx + 2f * u, cy + 0.8f * u, strokePaint)
                }

                Kind.SPARKLE -> {
                    path.reset()
                    path.moveTo(cx, cy - 9f * u)
                    path.quadTo(cx + 0.6f * u, cy - 0.6f * u, cx + 9f * u, cy)
                    path.quadTo(cx + 0.6f * u, cy + 0.6f * u, cx, cy + 9f * u)
                    path.quadTo(cx - 0.6f * u, cy + 0.6f * u, cx - 9f * u, cy)
                    path.quadTo(cx - 0.6f * u, cy - 0.6f * u, cx, cy - 9f * u)
                    path.close()
                    canvas.drawPath(path, strokePaint)
                }

                Kind.TIMER -> {
                    canvas.drawLine(cx - 3f * u, cy - 9.5f * u, cx + 3f * u, cy - 9.5f * u, strokePaint)
                    canvas.drawCircle(cx, cy + 1f * u, 7.8f * u, strokePaint)
                    canvas.drawLine(cx, cy + 1f * u, cx + 3.5f * u, cy - 2.5f * u, strokePaint)
                }

                Kind.KEYBOARD -> {
                    rect.set(cx - 9.5f * u, cy - 6.2f * u, cx + 9.5f * u, cy + 6.2f * u)
                    canvas.drawRoundRect(rect, 2.2f * u, 2.2f * u, strokePaint)
                    val xs = listOf(-5.5f, -1.8f, 1.8f, 5.5f)
                    for (xOff in xs) {
                        canvas.drawCircle(cx + xOff * u, cy - 2.2f * u, 1.0f * u, fillPaint)
                    }
                    canvas.drawLine(cx - 4.5f * u, cy + 2.5f * u, cx + 4.5f * u, cy + 2.5f * u, strokePaint)
                }

                Kind.BACK -> {
                    canvas.drawLine(cx - 6f * u, cy, cx + 7f * u, cy, strokePaint)
                    path.reset()
                    path.moveTo(cx - 1f * u, cy - 5.5f * u)
                    path.lineTo(cx - 6.5f * u, cy)
                    path.lineTo(cx - 1f * u, cy + 5.5f * u)
                    canvas.drawPath(path, strokePaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
