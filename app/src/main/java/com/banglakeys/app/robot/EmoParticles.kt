package com.banglakeys.app.robot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * EMO ভিসরের ওপর লাইভ পার্টিকেল ওভারলে:
 * - কান্না (TEARS): চোখের নিচে ঝরে পড়া সায়ান অশ্রুবিন্দু
 * - মাথা ঘোরা (STARS): ভিসরের চারপাশে ঘূর্ণায়মান তারা
 * - ভালোবাসা / ফিঙ্গারপ্রিন্ট ম্যাচ (HEARTS): ভেসে ওঠা হৃদয় চিহ্ন
 * - কানের কাছে ফিসফিস (WHISPER): মৃদু সাউন্ড ওয়েভ
 */
object EmoParticles {

    private val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5EE8FF.toInt()
        style = Paint.Style.FILL
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFC857.toInt()
        style = Paint.Style.FILL
    }
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF6B9C.toInt()
        style = Paint.Style.FILL
    }
    private val whisperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF36D1FF.toInt()
        style = Paint.Style.STROKE
    }

    fun draw(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        plateW: Float,
        plateH: Float,
        eyeGap: Float,
        isCrying: Boolean,
        mood: FaceId,
        effect: BodyEffect,
        effectAgeMs: Long,
        nowMs: Long,
    ) {
        if (isCrying) {
            drawTears(canvas, cx, cy, plateH, eyeGap, nowMs)
        }
        val activeEffect = when {
            isCrying -> BodyEffect.NONE
            mood == FaceId.DIZZY || mood == FaceId.SWIRL -> BodyEffect.STARS
            effect != BodyEffect.NONE && effectAgeMs in 0L..2600L -> effect
            else -> BodyEffect.NONE
        }
        when (activeEffect) {
            BodyEffect.STARS -> drawSpinningStars(canvas, cx, cy, plateW, plateH, nowMs)
            BodyEffect.HEARTS -> drawRisingHearts(canvas, cx, cy, plateW, plateH, effectAgeMs)
            BodyEffect.WHISPER -> drawWhisperRings(canvas, cx, cy, plateW, plateH, nowMs)
            BodyEffect.TEARS -> if (!isCrying) drawTears(canvas, cx, cy, plateH, eyeGap, nowMs)
            BodyEffect.NONE -> {}
        }
    }

    private fun drawTears(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        plateH: Float,
        eyeGap: Float,
        nowMs: Long,
    ) {
        val cycleMs = 950L
        val eyeY = cy + plateH * 0.08f
        val fallDist = plateH * 0.36f
        val r = (plateH * 0.055f).coerceAtLeast(5f)

        for (side in listOf(-1f, 1f)) {
            for (dropIdx in 0..1) {
                val offset = (nowMs + dropIdx * (cycleMs / 2) + if (side > 0f) 140L else 0L) % cycleMs
                val t = offset.toFloat() / cycleMs
                val x = cx + side * (eyeGap * 0.5f) + sin(t * PI * 2f).toFloat() * (r * 0.45f)
                val y = eyeY + t * fallDist
                val alpha = ((1f - t * t) * 245).toInt().coerceIn(0, 255)
                tearPaint.alpha = alpha
                val path = Path().apply {
                    moveTo(x, y - r * 1.45f)
                    quadTo(x + r * 1.15f, y + r * 0.3f, x, y + r)
                    quadTo(x - r * 1.15f, y + r * 0.3f, x, y - r * 1.45f)
                    close()
                }
                canvas.drawPath(path, tearPaint)
            }
        }
    }

    private fun drawSpinningStars(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        plateW: Float,
        plateH: Float,
        nowMs: Long,
    ) {
        val baseAngle = (nowMs % 2400L) / 2400f * (2f * PI.toFloat())
        val rx = plateW * 0.42f
        val ry = plateH * 0.22f
        val starR = (plateH * 0.065f).coerceAtLeast(6f)
        for (i in 0 until 4) {
            val a = baseAngle + i * (PI.toFloat() / 2f)
            val sx = cx + cos(a) * rx
            val sy = cy - plateH * 0.32f + sin(a) * ry * 0.45f
            starPaint.alpha = (165 + (sin(a) * 80).toInt()).coerceIn(60, 255)
            val path = Path().apply {
                moveTo(sx, sy - starR)
                lineTo(sx + starR * 0.28f, sy - starR * 0.28f)
                lineTo(sx + starR, sy)
                lineTo(sx + starR * 0.28f, sy + starR * 0.28f)
                lineTo(sx, sy + starR)
                lineTo(sx - starR * 0.28f, sy + starR * 0.28f)
                lineTo(sx - starR, sy)
                lineTo(sx - starR * 0.28f, sy - starR * 0.28f)
                close()
            }
            canvas.drawPath(path, starPaint)
        }
    }

    private fun drawRisingHearts(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        plateW: Float,
        plateH: Float,
        ageMs: Long,
    ) {
        val t = (ageMs.toFloat() / 2400f).coerceIn(0f, 1f)
        val size = (plateH * 0.09f).coerceAtLeast(8f)
        val offsets = listOf(-0.32f, 0.0f, 0.30f)
        offsets.forEachIndexed { idx, relX ->
            val phase = ((t + idx * 0.16f) % 1f)
            val hx = cx + relX * plateW + sin(phase * PI * 3f).toFloat() * 10f
            val hy = cy + plateH * 0.12f - phase * (plateH * 0.65f)
            heartPaint.alpha = ((1f - phase) * 240).toInt().coerceIn(0, 255)
            val path = Path().apply {
                moveTo(hx, hy + size * 0.6f)
                cubicTo(
                    hx - size * 1.2f, hy - size * 0.1f,
                    hx - size * 0.6f, hy - size * 1.0f,
                    hx, hy - size * 0.35f,
                )
                cubicTo(
                    hx + size * 0.6f, hy - size * 1.0f,
                    hx + size * 1.2f, hy - size * 0.1f,
                    hx, hy + size * 0.6f,
                )
                close()
            }
            canvas.drawPath(path, heartPaint)
        }
    }

    private fun drawWhisperRings(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        plateW: Float,
        plateH: Float,
        nowMs: Long,
    ) {
        val phase = (nowMs % 1400L) / 1400f
        whisperPaint.strokeWidth = (plateH * 0.015f).coerceAtLeast(2f)
        for (ring in 0..1) {
            val p = (phase + ring * 0.5f) % 1f
            val pad = p * (plateW * 0.12f)
            whisperPaint.alpha = ((1f - p) * 140).toInt().coerceIn(0, 255)
            val rect = RectF(
                cx - plateW * 0.48f - pad,
                cy - plateH * 0.46f - pad * 0.5f,
                cx + plateW * 0.48f + pad,
                cy + plateH * 0.46f + pad * 0.5f,
            )
            canvas.drawRoundRect(rect, plateH * 0.24f, plateH * 0.24f, whisperPaint)
        }
    }
}
