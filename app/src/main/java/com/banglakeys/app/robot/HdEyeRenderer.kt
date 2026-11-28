package com.banglakeys.app.robot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Classic EMO Cyan Glow থিমে ৮টি HD কার্টুন চোখের রেন্ডারার।
 * প্রতিটি চোখে থাকে:
 * - নরম সায়ান নিয়ন আভা (multi-layer outer glow)
 * - 3D রেডিয়াল সায়ান-আইস স্ক্লেরা গ্রেডিয়েন্ট + ক্রিস্প নিয়ন রিং
 * - গ্লসি অবসিডিয়ান পিউপিল + ডাবল স্পেকুলার ক্যাচলাইট
 * - ৬০fps প্রসিডিউরাল অ্যানিমেশন (gaze sweep, googly orbit, sparkle pulse, lid blink)
 */
object HdEyeRenderer {

    private const val CYAN_CORE = 0xFF36D1FF.toInt()
    private const val CYAN_BRIGHT = 0xFF9DF2FF.toInt()
    private const val PUPIL_DARK = 0xFF040A12.toInt()
    private const val LID_DARK = 0xFF070D15.toInt()
    private const val CATCHLIGHT_WHITE = 0xFFF2FCFF.toInt()

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val scleraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val lidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LID_DARK
        style = Paint.Style.FILL
    }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PUPIL_DARK
        style = Paint.Style.FILL
    }
    private val pupilRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val catchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CATCHLIGHT_WHITE
        style = Paint.Style.FILL
    }

    fun draw(
        canvas: Canvas,
        style: HdEyeStyle,
        cx: Float,
        cy: Float,
        plateW: Float,
        plateH: Float,
        eyeGap: Float,
        eyeRadius: Float,
        blink: Float = 0f,
        gazeNormX: Float = 0f,
        gazeNormY: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        accentColor: Int = CYAN_CORE,
        platePaint: Paint? = null,
        plateOutline: Paint? = null,
        nowMs: Long = System.currentTimeMillis(),
        glow: Boolean = true,
        lightVisor: Boolean = false,
    ) {
        if (plateW <= 0f || plateH <= 0f || eyeRadius <= 0f) return

        if (platePaint != null) {
            val r = plateH * 0.24f
            val rect = RectF(cx - plateW / 2f, cy - plateH / 2f, cx + plateW / 2f, cy + plateH / 2f)
            canvas.drawRoundRect(rect, r, r, platePaint)
            if (plateOutline != null) canvas.drawRoundRect(rect, r, r, plateOutline)
        }

        val tSec = (nowMs % 60_000L) / 1000f
        val breath = 1f + sin(tSec * 2.4f) * 0.018f
        val r = eyeRadius * breath

        val ex1 = cx - eyeGap / 2f
        val ex2 = cx + eyeGap / 2f
        val clampedBlink = blink.coerceIn(0f, 1f)
        val clampedGazeX = gazeNormX.coerceIn(-1f, 1f)
        val clampedGazeY = gazeNormY.coerceIn(-1f, 1f)

        val rgb = accentColor and 0x00FFFFFF
        val brightAccent = blendColor(accentColor, 0xFFFFFFFF.toInt(), 0.52f)
        val midSclera = blendColor(accentColor, 0xFF0A2438.toInt(), 0.22f)
        val edgeSclera = blendColor(accentColor, 0xFF061726.toInt(), 0.56f)

        for (isRight in listOf(false, true)) {
            val ex = if (isRight) ex2 else ex1
            val ey = cy

            // Style-specific micro-motion offset
            val styleBounceY = when (style) {
                HdEyeStyle.PEEK -> -kotlin.math.abs(sin(tSec * 4.2f)) * r * 0.09f
                HdEyeStyle.PINPOINT -> sin(tSec * 28f + if (isRight) 1.2f else 0f) * r * 0.025f
                else -> 0f
            }
            val styleBounceX = when (style) {
                HdEyeStyle.PINPOINT -> cos(tSec * 33f + if (isRight) 0.7f else 0f) * r * 0.025f
                else -> 0f
            }

            val drawX = ex + clampedGazeX * r * 0.14f + styleBounceX
            val drawY = ey + clampedGazeY * r * 0.12f + styleBounceY

            canvas.save()
            val blinkScaleY = ((1f - clampedBlink * 0.93f).coerceAtLeast(0.06f)) * scaleY
            canvas.scale(scaleX, blinkScaleY, drawX, drawY)

            // 1. Outer Glow Aura (only on dark cyber visor)
            if (glow && !lightVisor) {
                val auraR = r * 1.48f
                auraPaint.shader = RadialGradient(
                    drawX,
                    drawY,
                    auraR,
                    intArrayOf(
                        rgb or (0x4D shl 24),
                        rgb or (0x1E shl 24),
                        0x00000000,
                    ),
                    floatArrayOf(0.45f, 0.76f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(drawX, drawY, auraR, auraPaint)
                auraPaint.shader = null
            }

            if (lightVisor) {
                drawLightVisorEye(
                    canvas = canvas,
                    style = style,
                    ex = drawX,
                    ey = drawY,
                    r = r,
                    isRight = isRight,
                    gazeX = clampedGazeX,
                    gazeY = clampedGazeY,
                    tSec = tSec,
                    inkColor = accentColor,
                )
            } else {
                // 2. Eye Silhouette Path (Full circle, Peek dome, or Fierce slanted dome)
                val eyePath = buildEyeContourPath(style, drawX, drawY, r, isRight)

                canvas.save()
                canvas.clipPath(eyePath)

                // 3. Dimensional Glowing Sclera Gradient
                scleraPaint.shader = RadialGradient(
                    drawX - r * 0.16f,
                    drawY - r * 0.22f,
                    r * 1.15f,
                    intArrayOf(
                        brightAccent,
                        midSclera,
                        edgeSclera,
                    ),
                    floatArrayOf(0f, 0.58f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(drawX - r * 1.2f, drawY - r * 1.2f, drawX + r * 1.2f, drawY + r * 1.2f, scleraPaint)
                scleraPaint.shader = null

                // 4. Pupil & Catchlights by Style
                drawPupilsAndLids(
                    canvas = canvas,
                    style = style,
                    ex = drawX,
                    ey = drawY,
                    r = r,
                    isRight = isRight,
                    gazeX = clampedGazeX,
                    gazeY = clampedGazeY,
                    tSec = tSec,
                    accentColor = accentColor,
                    brightAccent = brightAccent,
                )

                canvas.restore() // End clipPath

                // 5. Crisp Glowing Outer Rim Border
                if (glow) {
                    rimPaint.color = rgb or (0x55 shl 24)
                    rimPaint.strokeWidth = (r * 0.14f).coerceAtLeast(3f)
                    canvas.drawPath(eyePath, rimPaint)
                }
                rimPaint.color = brightAccent
                rimPaint.strokeWidth = (r * 0.085f).coerceAtLeast(2.2f)
                canvas.drawPath(eyePath, rimPaint)
            }

            canvas.restore()
        }
    }

    private fun buildEyeContourPath(
        style: HdEyeStyle,
        cx: Float,
        cy: Float,
        r: Float,
        isRight: Boolean,
    ): Path = Path().apply {
        when (style) {
            HdEyeStyle.PEEK -> {
                // Half-dome with flat bottom line (Row 4 Left of reference)
                val bottomY = cy + r * 0.52f
                val domeRect = RectF(cx - r, cy - r * 0.92f, cx + r, cy + r * 1.04f)
                arcTo(domeRect, 180f, 180f, true)
                lineTo(cx + r, bottomY)
                lineTo(cx - r, bottomY)
                close()
            }
            HdEyeStyle.FIERCE -> {
                // Sharp inward-slanted angry lid (Row 4 Right of reference)
                val oval = RectF(cx - r, cy - r, cx + r, cy + r)
                if (!isRight) {
                    // Left eye: top-left high, slanted down toward inner-right
                    moveTo(cx - r * 0.94f, cy - r * 0.56f)
                    lineTo(cx + r * 0.96f, cy - r * 0.08f)
                    arcTo(oval, -5f, 195f, false)
                    close()
                } else {
                    // Right eye: inner-left low, slanted up toward top-right
                    moveTo(cx - r * 0.96f, cy - r * 0.08f)
                    lineTo(cx + r * 0.94f, cy - r * 0.56f)
                    arcTo(oval, -26f, 200f, false)
                    close()
                }
            }
            else -> {
                addCircle(cx, cy, r, Path.Direction.CW)
            }
        }
    }

    private fun drawPupilsAndLids(
        canvas: Canvas,
        style: HdEyeStyle,
        ex: Float,
        ey: Float,
        r: Float,
        isRight: Boolean,
        gazeX: Float,
        gazeY: Float,
        tSec: Float,
        accentColor: Int,
        brightAccent: Int,
    ) {
        when (style) {
            // Row 1 Left: Side-glancing glossy cartoon eyes
            HdEyeStyle.GLANCE -> {
                val idleSweepX = sin(tSec * 1.85f) * 0.42f
                val gx = (gazeX * 0.75f + idleSweepX * (1f - kotlin.math.abs(gazeX) * 0.6f)).coerceIn(-0.6f, 0.6f)
                val gy = (gazeY * 0.45f).coerceIn(-0.4f, 0.4f)
                val pr = r * 0.44f
                val px = ex + gx * r * 0.58f
                val py = ey + gy * r * 0.45f
                drawGlossyPupil(canvas, px, py, pr, accentColor, bigHighlight = true, microSparkle = true)
            }

            // Row 1 Right: Half-lidded Chill / Sleepy eyes
            HdEyeStyle.CHILL -> {
                val lidDrop = sin(tSec * 1.4f) * 0.04f
                val lidBottom = ey - r * (0.04f - lidDrop)
                val pr = r * 0.30f
                val px = ex + (gazeX * 0.35f - 0.14f) * r * 0.55f
                val py = lidBottom + pr * 0.85f + gazeY * r * 0.12f
                drawGlossyPupil(canvas, px, py, pr, accentColor, bigHighlight = true, microSparkle = false)

                // Dark upper eyelid + glowing curved lid arch
                lidPaint.alpha = 238
                canvas.drawRect(ex - r * 1.1f, ey - r * 1.1f, ex + r * 1.1f, lidBottom, lidPaint)
                rimPaint.color = brightAccent
                rimPaint.strokeWidth = (r * 0.085f).coerceAtLeast(2f)
                val lidPath = Path().apply {
                    moveTo(ex - r * 1.05f, lidBottom)
                    quadTo(ex, lidBottom - r * 0.16f, ex + r * 1.05f, lidBottom)
                }
                canvas.drawPath(lidPath, rimPaint)
            }

            // Row 2 Left: Kawaii Sparkle / Puppy-dog wide glossy iris
            HdEyeStyle.SPARKLE -> {
                val pulse = 1f + sin(tSec * 3.8f) * 0.03f
                val pr = r * 0.66f * pulse
                val px = ex + gazeX * r * 0.24f
                val py = ey + gazeY * r * 0.20f
                drawGlossyPupil(canvas, px, py, pr, accentColor, bigHighlight = true, microSparkle = true, kawaii = true)
            }

            // Row 2 Right: Googly / Dizzy counter-orbiting small pupils
            HdEyeStyle.GOOGLY -> {
                val angle = tSec * 3.2f * (if (isRight) -1f else 1f) + (if (isRight) 0.85f else 3.8f)
                val orbitR = r * 0.42f
                val pr = r * 0.27f
                val px = ex + cos(angle) * orbitR + gazeX * r * 0.18f
                val py = ey + sin(angle) * orbitR + gazeY * r * 0.18f
                drawGlossyPupil(canvas, px, py, pr, accentColor, bigHighlight = true, microSparkle = false)
            }

            // Row 3 Left: Shocked wide-open sclera with tiny pinpoint pupil
            HdEyeStyle.PINPOINT -> {
                val pulse = 1f + sin(tSec * 7.5f) * 0.08f
                val pr = (r * 0.105f * pulse).coerceAtLeast(2.5f)
                val px = ex + gazeX * r * 0.30f
                val py = ey + gazeY * r * 0.30f
                pupilPaint.color = PUPIL_DARK
                canvas.drawCircle(px, py, pr, pupilPaint)

                // Subtle shock echo ring inside sclera
                pupilRimPaint.color = brightAccent
                pupilRimPaint.alpha = 110
                pupilRimPaint.strokeWidth = (r * 0.035f).coerceAtLeast(1f)
                canvas.drawCircle(px, py, pr * 2.5f, pupilRimPaint)
                pupilRimPaint.alpha = 255
            }

            // Row 3 Right: Deep Focused dark iris + glossy highlight + glowing center pinpoint
            HdEyeStyle.FOCUSED -> {
                val pr = r * 0.74f
                val px = ex + gazeX * r * 0.18f
                val py = ey + gazeY * r * 0.18f
                drawGlossyPupil(canvas, px, py, pr, accentColor, bigHighlight = true, microSparkle = false, kawaii = true)

                // Sharp luminous center pinpoint dot
                val dotPulse = 1f + sin(tSec * 5f) * 0.15f
                catchPaint.color = brightAccent
                catchPaint.alpha = 245
                canvas.drawCircle(px, py, (r * 0.085f * dotPulse).coerceAtLeast(2.2f), catchPaint)
                catchPaint.color = CATCHLIGHT_WHITE
                catchPaint.alpha = 255
            }

            // Row 4 Left: Happy Peeking Domes
            HdEyeStyle.PEEK -> {
                val pr = r * 0.44f
                val lookX = gazeX * r * 0.32f + sin(tSec * 2.2f) * r * 0.10f
                val px = ex + lookX
                val py = ey + r * 0.16f + gazeY * r * 0.16f
                drawGlossyPupil(canvas, px, py, pr, accentColor, bigHighlight = true, microSparkle = true)
            }

            // Row 4 Right: Fierce / Angry slanted eyes
            HdEyeStyle.FIERCE -> {
                val pr = r * 0.43f
                val inward = if (isRight) -r * 0.10f else r * 0.10f
                val px = ex + inward + gazeX * r * 0.28f
                val py = ey + r * 0.08f + gazeY * r * 0.22f
                drawGlossyPupil(canvas, px, py, pr, accentColor, bigHighlight = true, microSparkle = false)
            }
        }
    }

    private fun drawGlossyPupil(
        canvas: Canvas,
        px: Float,
        py: Float,
        pr: Float,
        accentColor: Int,
        bigHighlight: Boolean,
        microSparkle: Boolean,
        kawaii: Boolean = false,
    ) {
        pupilPaint.color = PUPIL_DARK
        canvas.drawCircle(px, py, pr, pupilPaint)

        // Subtle cyan rim inside pupil edge for HD depth
        pupilRimPaint.color = accentColor
        pupilRimPaint.alpha = 140
        pupilRimPaint.strokeWidth = (pr * 0.08f).coerceAtLeast(1.2f)
        canvas.drawCircle(px, py, pr * 0.94f, pupilRimPaint)
        pupilRimPaint.alpha = 255

        if (bigHighlight) {
            catchPaint.color = CATCHLIGHT_WHITE
            catchPaint.alpha = 245
            val hr = if (kawaii) pr * 0.30f else pr * 0.36f
            val hx = px - pr * 0.42f
            val hy = py - pr * 0.42f
            canvas.drawCircle(hx, hy, hr, catchPaint)
        }
        if (microSparkle) {
            catchPaint.color = CYAN_BRIGHT
            catchPaint.alpha = 215
            canvas.drawCircle(px + pr * 0.38f, py + pr * 0.34f, (pr * 0.14f).coerceAtLeast(1.8f), catchPaint)
        }
        catchPaint.alpha = 255
    }

    private fun drawLightVisorEye(
        canvas: Canvas,
        style: HdEyeStyle,
        ex: Float,
        ey: Float,
        r: Float,
        isRight: Boolean,
        gazeX: Float,
        gazeY: Float,
        tSec: Float,
        inkColor: Int,
    ) {
        val inkHighlight = blendColor(inkColor, 0xFF6A4C93.toInt(), 0.28f)
        when (style) {
            // Happy squint arch (^ ^) matching the joyful jumping buddy reference
            HdEyeStyle.PEEK -> {
                rimPaint.color = inkColor
                rimPaint.strokeWidth = (r * 0.25f).coerceAtLeast(4f)
                val p = Path().apply {
                    moveTo(ex - r * 0.62f, ey + r * 0.14f)
                    quadTo(ex, ey - r * 0.62f, ex + r * 0.62f, ey + r * 0.14f)
                }
                canvas.drawPath(p, rimPaint)
            }
            // Peaceful closed sleepy eye (︶ ︶) matching the belly-flop sleepy reference
            HdEyeStyle.CHILL -> {
                rimPaint.color = inkColor
                rimPaint.strokeWidth = (r * 0.23f).coerceAtLeast(3.8f)
                val p = Path().apply {
                    moveTo(ex - r * 0.62f, ey - r * 0.06f)
                    quadTo(ex, ey + r * 0.36f, ex + r * 0.62f, ey - r * 0.06f)
                }
                canvas.drawPath(p, rimPaint)
            }
            // Dizzy orbiting white-sclera + dark pupils
            HdEyeStyle.GOOGLY -> {
                scleraPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(ex, ey, r * 0.88f, scleraPaint)
                rimPaint.color = inkColor
                rimPaint.strokeWidth = (r * 0.12f).coerceAtLeast(2.5f)
                canvas.drawCircle(ex, ey, r * 0.88f, rimPaint)

                val angle = tSec * 3.4f * (if (isRight) -1f else 1f) + (if (isRight) 0.85f else 3.8f)
                val orbitR = r * 0.34f
                val px = ex + cos(angle) * orbitR + gazeX * r * 0.14f
                val py = ey + sin(angle) * orbitR + gazeY * r * 0.14f
                pupilPaint.color = inkColor
                canvas.drawCircle(px, py, r * 0.30f, pupilPaint)
                catchPaint.color = CATCHLIGHT_WHITE
                canvas.drawCircle(px - r * 0.10f, py - r * 0.10f, (r * 0.10f).coerceAtLeast(1.8f), catchPaint)
            }
            // Shocked white sclera + tiny pinpoint pupil
            HdEyeStyle.PINPOINT -> {
                scleraPaint.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(ex, ey, r * 0.88f, scleraPaint)
                rimPaint.color = inkColor
                rimPaint.strokeWidth = (r * 0.12f).coerceAtLeast(2.5f)
                canvas.drawCircle(ex, ey, r * 0.88f, rimPaint)
                pupilPaint.color = inkColor
                canvas.drawCircle(ex + gazeX * r * 0.28f, ey + gazeY * r * 0.28f, (r * 0.14f).coerceAtLeast(2.8f), pupilPaint)
            }
            // Fierce dark espresso eyes with inward angry slant
            HdEyeStyle.FIERCE -> {
                val eyePath = buildEyeContourPath(HdEyeStyle.FIERCE, ex, ey, r * 0.90f, isRight)
                pupilPaint.shader = RadialGradient(
                    ex, ey - r * 0.2f, r,
                    intArrayOf(inkHighlight, inkColor),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawPath(eyePath, pupilPaint)
                pupilPaint.shader = null
                catchPaint.color = CATCHLIGHT_WHITE
                canvas.drawCircle(ex - r * 0.22f, ey - r * 0.12f, (r * 0.19f).coerceAtLeast(2.2f), catchPaint)
            }
            // Classic soft-3D dark glossy oval eyes with double white catchlights (GLANCE, SPARKLE, FOCUSED)
            else -> {
                val ovalW = r * 0.78f
                val ovalH = r * 0.92f
                val idleSweepX = if (style == HdEyeStyle.GLANCE) sin(tSec * 1.85f) * 0.24f else 0f
                val ox = ex + (gazeX * 0.35f + idleSweepX) * r * 0.42f
                val oy = ey + gazeY * r * 0.25f

                pupilPaint.shader = RadialGradient(
                    ox - ovalW * 0.2f,
                    oy - ovalH * 0.25f,
                    ovalH * 1.25f,
                    intArrayOf(inkHighlight, inkColor),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
                val rect = RectF(ox - ovalW, oy - ovalH, ox + ovalW, oy + ovalH)
                canvas.drawOval(rect, pupilPaint)
                pupilPaint.shader = null

                // Crisp glossy specular catchlights just like the soft 3D character reference
                catchPaint.color = 0xFFFFFFFF.toInt()
                catchPaint.alpha = 248
                val bigR = if (style == HdEyeStyle.SPARKLE) r * 0.27f else r * 0.23f
                canvas.drawCircle(ox + ovalW * 0.26f, oy - ovalH * 0.32f, bigR.coerceAtLeast(2.4f), catchPaint)

                if (style == HdEyeStyle.SPARKLE || style == HdEyeStyle.FOCUSED) {
                    catchPaint.alpha = 210
                    canvas.drawCircle(ox - ovalW * 0.28f, oy + ovalH * 0.28f, (r * 0.11f).coerceAtLeast(1.6f), catchPaint)
                }
                catchPaint.alpha = 255
            }
        }
    }

    private fun blendColor(a: Int, b: Int, ratioB: Float): Int {
        val t = ratioB.coerceIn(0f, 1f)
        val inv = 1f - t
        val r = (((a shr 16) and 0xFF) * inv + ((b shr 16) and 0xFF) * t).toInt().coerceIn(0, 255)
        val g = (((a shr 8) and 0xFF) * inv + ((b shr 8) and 0xFF) * t).toInt().coerceIn(0, 255)
        val bl = ((a and 0xFF) * inv + (b and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
