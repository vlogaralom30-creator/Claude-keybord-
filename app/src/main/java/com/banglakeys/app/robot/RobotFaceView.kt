package com.banglakeys.app.robot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.min

/**
 * Phase 1 — `RobotBrain`-চালিত Face View।
 * ট্যাপ করলে `RobotEvent.Tap` brain-কে পাঠায়, brain নিজে mood, speech bubble,
 * এবং Energy/Bond/Fun state আপডেট করে।
 */
class RobotFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    val brain: RobotBrain get() = EmoBrainHub.brain
    private val blink = BlinkAnimator()
    private val hubListener: () -> Unit = { postInvalidateOnAnimation() }

    private val density = resources.displayMetrics.density
    private fun sp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A0F18")
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4436D1FF")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#36D1FF")
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A9C2D6")
        textAlign = Paint.Align.CENTER
        textSize = sp(13f)
    }
    private val barLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB8CC")
        textAlign = Paint.Align.LEFT
        textSize = sp(11.5f)
    }
    private val barValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6F4FF")
        textAlign = Paint.Align.RIGHT
        textSize = sp(11.5f)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#112636")
    }
    private val bubbleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6636D1FF")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6F8FF")
        textAlign = Paint.Align.CENTER
        textSize = sp(13.5f)
    }
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#182230") }
    private val barEnergyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#36D1FF") }
    private val barBondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6B9C") }
    private val barFunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#22E58A") }

    init {
        isClickable = true
        isFocusable = true
        isLongClickable = true
        cameraDistance = 12000f * density
        contentDescription = "EMO রোবট ফেস — ট্যাপ বা ধরে রেখে আদর করুন"
        setOnClickListener {
            EmoBrainHub.handleEvent(RobotEvent.Tap)
            invalidate()
        }
        setOnLongClickListener {
            EmoBrainHub.handleEvent(RobotEvent.LongPress)
            invalidate()
            true
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        EmoBrainHub.addListener(hubListener)
    }

    override fun onDetachedFromWindow() {
        EmoBrainHub.removeListener(hubListener)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        EmoBrainHub.tick()
        val body = EmoBrainHub.bodyState
        if (body.sensorsLive) {
            rotationX = (-body.pitchDeg * 0.45f).coerceIn(-12f, 12f)
            rotationY = (body.rollDeg * 0.45f).coerceIn(-12f, 12f)
        } else {
            rotationX = 0f
            rotationY = 0f
        }

        val padH = 18f * density
        val topBubbleBandH = 44f * density
        val bottomBarsH = 74f * density
        val labelZoneH = 38f * density

        val availFaceH = (height - topBubbleBandH - labelZoneH - bottomBarsH).coerceAtLeast(80f * density)
        val cx = width / 2f
        val plateW = min(width * 0.72f, availFaceH * 1.65f)
        val plateH = min(availFaceH * 0.86f, plateW * 0.56f)
        val cy = topBubbleBandH + availFaceH / 2f
        val eyeSize = plateH * 0.25f
        val eyeGap = plateW * 0.39f
        val gazeShiftX = body.combinedGazeX() * (plateW * 0.085f)
        val gazeShiftY = body.combinedGazeY() * (plateH * 0.085f)

        FaceRenderer.draw(
            canvas = canvas,
            face = brain.mood,
            cx = cx,
            cy = cy,
            plateW = plateW,
            plateH = plateH,
            eyeGap = eyeGap,
            eyeSize = eyeSize,
            blink = if (brain.sleeping) 0.8f else blink.tick(),
            eyePaint = eyePaint,
            platePaint = platePaint,
            plateOutline = outlinePaint,
            gazeX = gazeShiftX,
            gazeY = gazeShiftY,
            glow = true,
        )

        val nowMs = System.currentTimeMillis()
        EmoParticles.draw(
            canvas = canvas,
            cx = cx,
            cy = cy,
            plateW = plateW,
            plateH = plateH,
            eyeGap = eyeGap,
            isCrying = brain.isCrying,
            mood = brain.mood,
            effect = body.lastEffect,
            effectAgeMs = nowMs - body.lastEffectAtMs,
            nowMs = nowMs,
        )

        val statusSuffix = when {
            brain.isCrying -> " · 😢 ব্যথা পেয়েছে — ট্যাপ করে আদর করো"
            brain.sleeping -> " · 💤 ঘুমাচ্ছে"
            body.isNearEar -> " · 🤫 ফিসফিস মোড"
            else -> ""
        }
        canvas.drawText(
            "${brain.mood.name} (${brain.mood.bn})$statusSuffix",
            cx,
            cy + plateH / 2f + 22f * density,
            labelPaint
        )

        brain.speechText?.let {
            val bubbleBottom = (cy - plateH / 2f - 8f * density).coerceAtLeast(36f * density)
            drawBubble(canvas, it, cx, bubbleBottom)
        }

        drawNeedsBars(canvas, padH, height - bottomBarsH, width - padH * 2f)

        if (isAttachedToWindow) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawBubble(canvas: Canvas, text: String, cx: Float, bottomY: Float) {
        val padH = 14f * density
        val bubbleH = 32f * density
        val maxW = (width - 24f * density).coerceAtLeast(80f)
        val rawTextW = bubbleTextPaint.measureText(text)
        val w = (rawTextW + padH * 2f).coerceAtMost(maxW)
        val topY = (bottomY - bubbleH).coerceAtLeast(4f * density)
        val rect = RectF(cx - w / 2f, topY, cx + w / 2f, topY + bubbleH)
        canvas.drawRoundRect(rect, 14f * density, 14f * density, bubblePaint)
        canvas.drawRoundRect(rect, 14f * density, 14f * density, bubbleStrokePaint)
        val fm = bubbleTextPaint.fontMetrics
        val textY = rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cx, textY, bubbleTextPaint)
    }

    private fun drawNeedsBars(canvas: Canvas, left: Float, top: Float, totalW: Float) {
        val barH = 8f * density
        val rowPitch = 22f * density
        val labelColW = 54f * density
        val valueColW = 30f * density
        val barLeft = left + labelColW
        val barRight = (left + totalW - valueColW).coerceAtLeast(barLeft + 40f * density)
        val barW = barRight - barLeft

        val rows = listOf(
            "Energy" to (brain.needs.energy to barEnergyPaint),
            "Bond" to (brain.needs.bond to barBondPaint),
            "Fun" to (brain.needs.funLevel to barFunPaint),
        )
        rows.forEachIndexed { i, (label, pair) ->
            val (value, paint) = pair
            val rowTop = top + i * rowPitch
            val barTop = rowTop + (rowPitch - barH) / 2f
            val fm = barLabelPaint.fontMetrics
            val textY = barTop + barH / 2f - (fm.ascent + fm.descent) / 2f

            canvas.drawText(label, left, textY, barLabelPaint)
            canvas.drawRoundRect(
                RectF(barLeft, barTop, barLeft + barW, barTop + barH),
                barH / 2f,
                barH / 2f,
                barBgPaint
            )
            val fillW = barW * (value / 100f).coerceIn(0f, 1f)
            if (fillW > 0f) {
                canvas.drawRoundRect(
                    RectF(barLeft, barTop, barLeft + fillW, barTop + barH),
                    barH / 2f,
                    barH / 2f,
                    paint
                )
            }
            canvas.drawText("$value", left + totalW, textY, barValuePaint)
        }
    }
}
