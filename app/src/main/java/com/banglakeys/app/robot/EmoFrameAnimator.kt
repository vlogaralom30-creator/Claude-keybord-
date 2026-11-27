package com.banglakeys.app.robot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin

/**
 * 60fps EMO রোবট ফ্রেম অ্যানিমেটর।
 * - `RobotBrain`-এর সাথে যুক্ত হয়ে mood change bounce, মসৃণ blink, idle breathing/sway,
 *   এবং কী-প্রেস অনুযায়ী চোখের gaze tracking রেন্ডার করে।
 */
class EmoFrameAnimator(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    val brain: RobotBrain = RobotBrain(nowMs),
) {

    private val blink = BlinkAnimator(nowMs)
    private val typingMood = TypingMoodTracker()

    var privacyMode: Boolean = false

    private var lastMood: FaceId = brain.mood
    private var moodChangedAtMs: Long = 0L
    private var sleepFactor: Float = 0f

    private var targetGazeNormX: Float = 0f
    private var targetGazeNormY: Float = 0f
    private var curGazeNormX: Float = 0f
    private var curGazeNormY: Float = 0f
    private var lastGazeInputMs: Long = 0L
    private var lastFrameMs: Long = nowMs()

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF36D1FF.toInt()
    }
    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A0F18.toInt()
        style = Paint.Style.FILL
    }
    private val plateOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4436D1FF.toInt()
        style = Paint.Style.STROKE
    }
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var auraW = 0f
    private var auraH = 0f
    private var auraCx = 0f
    private var auraCy = 0f
    private var auraShader: RadialGradient? = null

    fun onKeyTap(normX: Float, normY: Float, isBackspace: Boolean) {
        val now = nowMs()
        targetGazeNormX = normX.coerceIn(-1f, 1f)
        targetGazeNormY = normY.coerceIn(-1f, 1f)
        lastGazeInputMs = now

        if (privacyMode) return
        typingMood.onKeyTap(isBackspace, now)?.let { ev ->
            brain.handle(ev)
        }
    }

    fun onBlankTap() {
        if (privacyMode) return
        brain.handle(RobotEvent.Tap)
    }

    fun onWordCommitted(word: String, isPassword: Boolean) {
        if (privacyMode || isPassword) return
        typingMood.onWordCommitted(word)?.let { ev ->
            brain.handle(ev)
        }
    }

    fun onMessageSent(isPassword: Boolean) {
        if (privacyMode || isPassword) return
        brain.handle(RobotEvent.MessageSent)
    }

    fun reset() {
        brain.resetMemory()
        typingMood.reset()
        lastMood = brain.mood
        targetGazeNormX = 0f
        targetGazeNormY = 0f
        curGazeNormX = 0f
        curGazeNormY = 0f
    }

    /** কী-বোর্ড ব্যাকগ্রাউন্ডের জন্য ফ্রেম আঁকে। */
    fun drawBackdrop(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        areaW: Float,
        areaH: Float,
        accentColor: Int = 0xFF36D1FF.toInt(),
    ) {
        if (areaW <= 0f || areaH <= 0f) return
        val now = nowMs()
        val dt = ((now - lastFrameMs).coerceIn(1L, 64L)) / 1000f
        lastFrameMs = now

        if (!privacyMode) {
            brain.tick()
        }
        val activeMood = if (privacyMode) FaceId.CALM else brain.mood
        if (activeMood != lastMood) {
            lastMood = activeMood
            moodChangedAtMs = now
        }

        // Smooth gaze return after 1.1s idle, plus gentle idle curiosity look
        if (now - lastGazeInputMs > 1100L) {
            val idlePhase = (now % 9000L) / 9000f * 2f * PI.toFloat()
            targetGazeNormX = sin(idlePhase) * 0.28f
            targetGazeNormY = sin(idlePhase * 0.5f) * 0.10f
        }
        val lerpRate = (dt * 12f).coerceIn(0f, 1f)
        curGazeNormX += (targetGazeNormX - curGazeNormX) * lerpRate
        curGazeNormY += (targetGazeNormY - curGazeNormY) * lerpRate

        // Smooth sleep blend
        val targetSleep = if (!privacyMode && brain.sleeping) 1f else 0f
        sleepFactor += (targetSleep - sleepFactor) * (dt * 6f).coerceIn(0f, 1f)

        // Bounce overshoot animation on mood switch (~240ms)
        val elapsedMood = (now - moodChangedAtMs).coerceAtLeast(0L)
        val (scaleX, scaleY) = if (elapsedMood < 240L) {
            val t = elapsedMood / 240f
            val wave = sin(t * PI.toFloat())
            (1f - 0.10f * wave) to (1f + 0.14f * wave)
        } else {
            // Subtle breathing sway
            val breath = sin(((now % 3400L) / 3400f) * 2f * PI.toFloat()) * 0.018f
            1f to (1f + breath)
        }

        val plateW = (areaW * 0.72f).coerceAtMost(areaH * 2.15f)
        val plateH = (areaH * 0.78f).coerceAtMost(plateW * 0.54f)
        val eyeSize = plateH * 0.29f
        val eyeGap = plateW * 0.42f

        // Subtle cyan radial backlight behind the EMO visor
        if (auraShader == null || auraW != areaW || auraH != areaH || auraCx != cx || auraCy != cy) {
            auraW = areaW
            auraH = areaH
            auraCx = cx
            auraCy = cy
            auraShader = RadialGradient(
                cx,
                cy,
                (plateW * 0.72f).coerceAtLeast(16f),
                intArrayOf(
                    (accentColor and 0x00FFFFFF) or (0x2A shl 24),
                    (accentColor and 0x00FFFFFF) or (0x0A shl 24),
                    0x00000000
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        auraPaint.shader = auraShader
        canvas.drawRect(cx - areaW / 2f, cy - areaH / 2f, cx + areaW / 2f, cy + areaH / 2f, auraPaint)

        eyePaint.color = accentColor
        eyePaint.alpha = 255
        plateOutlinePaint.color = (accentColor and 0x00FFFFFF) or (0x45 shl 24)
        plateOutlinePaint.strokeWidth = (plateH * 0.016f).coerceIn(1.5f, 4f)

        val blinkProgress = maxOf(blink.tick(), sleepFactor * 0.78f)
        val maxShiftX = plateW * 0.065f
        val maxShiftY = plateH * 0.055f

        FaceRenderer.draw(
            canvas = canvas,
            face = activeMood,
            cx = cx,
            cy = cy,
            plateW = plateW,
            plateH = plateH,
            eyeGap = eyeGap,
            eyeSize = eyeSize,
            blink = blinkProgress,
            eyePaint = eyePaint,
            platePaint = platePaint,
            plateOutline = plateOutlinePaint,
            gazeX = curGazeNormX * maxShiftX,
            gazeY = curGazeNormY * maxShiftY,
            scaleX = scaleX,
            scaleY = scaleY,
            glow = true,
        )
    }
}
