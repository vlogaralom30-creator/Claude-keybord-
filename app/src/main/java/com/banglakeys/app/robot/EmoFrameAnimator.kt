package com.banglakeys.app.robot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * 60fps ইউনিফাইড EMO রোবট ফ্রেম অ্যানিমেটর।
 * - কী-বোর্ডের গ্লাস কী-গুলোর নিচে কিউট 3D ফুল-বডি রোবট (Purple Lavender 3D / Classic Cyan) আঁকে
 * - পরিস্থিতি বুঝে ৩২টি ফেস রিয়্যাকশন, ৮টি HD অ্যানিমেটেড চোখ, এবং ফুল-বডি মুভ একসাথে সমন্বয় করে
 * - কী-প্রেস অনুযায়ী চোখের gaze tracking, ট্যাপ হপ, সেন্সর টিল্ট ও idle খেলাধুলা চালায়।
 */
class EmoFrameAnimator(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    val brain: RobotBrain = RobotBrain(nowMs),
) {

    private val blink = BlinkAnimator(nowMs)
    private val typingMood = TypingMoodTracker()

    var privacyMode: Boolean = false

    var currentMove: RobotBodyMove = RobotBodyMove.IDLE
        private set
    var currentUsingHdEyes: Boolean = true
        private set
    var currentHdStyle: HdEyeStyle = HdEyeStyle.SPARKLE
        private set

    private var lastMood: FaceId = brain.mood
    private var moodChangedAtMs: Long = 0L
    private var sleepFactor: Float = 0f

    private var targetGazeNormX: Float = 0f
    private var targetGazeNormY: Float = 0f
    private var curGazeNormX: Float = 0f
    private var curGazeNormY: Float = 0f
    private var lastGazeInputMs: Long = 0L
    private var lastInteractionMs: Long = nowMs()
    private var localTapHopAtMs: Long = -10_000L
    private var localPetWiggleAtMs: Long = -10_000L
    private var messageSentAtMs: Long = -10_000L
    private var celebrationAtMs: Long = -10_000L
    private var lastFrameMs: Long = nowMs()

    private var curPose: RobotBodyPose = RobotBodyMotionEngine.samplePose(RobotBodyMove.IDLE, lastFrameMs)

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var auraW = 0f
    private var auraH = 0f
    private var auraCx = 0f
    private var auraCy = 0f
    private var auraGlowColor = 0
    private var auraShader: RadialGradient? = null

    fun onKeyTap(normX: Float, normY: Float, isBackspace: Boolean) {
        val now = nowMs()
        targetGazeNormX = normX.coerceIn(-1f, 1f)
        targetGazeNormY = normY.coerceIn(-1f, 1f)
        lastGazeInputMs = now
        lastInteractionMs = now

        if (now - localTapHopAtMs >= 320L) {
            localTapHopAtMs = now
        }

        if (privacyMode) return
        typingMood.onKeyTap(isBackspace, now)?.let { ev ->
            brain.handle(ev)
            if (ev == RobotEvent.TypingFast) {
                celebrationAtMs = now
            }
        }
    }

    fun onBlankTap() {
        val now = nowMs()
        lastInteractionMs = now
        localTapHopAtMs = now
        localPetWiggleAtMs = now
        if (privacyMode) return
        brain.handle(RobotEvent.Tap)
        celebrationAtMs = now
    }

    fun onWordCommitted(word: String, isPassword: Boolean) {
        if (privacyMode || isPassword) return
        val now = nowMs()
        lastInteractionMs = now
        typingMood.onWordCommitted(word)?.let { ev ->
            brain.handle(ev)
            when (ev) {
                RobotEvent.KeywordLove,
                RobotEvent.KeywordLaugh,
                RobotEvent.KeywordKiss,
                RobotEvent.KeywordCute -> {
                    celebrationAtMs = now
                    localTapHopAtMs = now
                    localPetWiggleAtMs = now
                }
                RobotEvent.KeywordShock -> localTapHopAtMs = now
                else -> {}
            }
        }
    }

    fun onMessageSent(isPassword: Boolean) {
        if (privacyMode || isPassword) return
        val now = nowMs()
        lastInteractionMs = now
        messageSentAtMs = now
        localTapHopAtMs = now
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
        localTapHopAtMs = -10_000L
        localPetWiggleAtMs = -10_000L
        messageSentAtMs = -10_000L
        celebrationAtMs = -10_000L
        currentMove = RobotBodyMove.IDLE
        currentUsingHdEyes = true
        currentHdStyle = HdEyeStyle.SPARKLE
        curPose = RobotBodyMotionEngine.samplePose(RobotBodyMove.IDLE, nowMs())
    }

    /** কী-বোর্ড ব্যাকগ্রাউন্ডের জন্য ৬০fps ফুল-বডি + ৮টি HD চোখ + ৩২টি রিয়্যাকশন ফ্রেম আঁকে। */
    fun drawBackdrop(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        areaW: Float,
        areaH: Float,
        accentColor: Int = 0xFF36D1FF.toInt(),
        skin: RobotBodySkin = RobotBodySkin.LAVENDER_3D,
        visualMode: EmoVisualMode = EmoVisualMode.AUTO_UNIFIED,
        bodyState: EmoBodyState = EmoBodyState(),
        isVoiceListening: Boolean = false,
        forcedMove: RobotBodyMove? = null,
        forcedHdEye: HdEyeStyle? = null,
        externalTapHopAtMs: Long = -10_000L,
        externalPetWiggleAtMs: Long = -10_000L,
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
            targetGazeNormX = sin(idlePhase) * 0.34f
            targetGazeNormY = sin(idlePhase * 0.5f) * 0.14f
        }
        val lerpRate = (dt * 12f).coerceIn(0f, 1f)
        curGazeNormX += (targetGazeNormX - curGazeNormX) * lerpRate
        curGazeNormY += (targetGazeNormY - curGazeNormY) * lerpRate

        // Blend typing gaze with live accelerometer tilt gaze if available
        val combinedGazeX = (curGazeNormX + bodyState.tiltGazeX * 0.55f).coerceIn(-1f, 1f)
        val combinedGazeY = (curGazeNormY + bodyState.tiltGazeY * 0.55f).coerceIn(-1f, 1f)

        // Smooth sleep blend
        val targetSleep = if (!privacyMode && brain.sleeping) 1f else 0f
        sleepFactor += (targetSleep - sleepFactor) * (dt * 6f).coerceIn(0f, 1f)

        // Visor bounce overshoot animation on mood switch (~240ms)
        val elapsedMood = (now - moodChangedAtMs).coerceAtLeast(0L)
        val (visorScaleX, visorScaleY) = if (elapsedMood < 240L) {
            val t = elapsedMood / 240f
            val wave = sin(t * PI.toFloat())
            (1f - 0.08f * wave) to (1f + 0.11f * wave)
        } else {
            val breath = sin(((now % 3400L) / 3400f) * 2f * PI.toFloat()) * 0.016f
            1f to (1f + breath)
        }

        val glowColor = if (skin == RobotBodySkin.LAVENDER_3D) 0xFFC4A1FF.toInt() else accentColor
        if (auraShader == null || auraW != areaW || auraH != areaH || auraCx != cx || auraCy != cy || auraGlowColor != glowColor) {
            auraW = areaW
            auraH = areaH
            auraCx = cx
            auraCy = cy
            auraGlowColor = glowColor
            val radius = (minOf(areaW, areaH) * 0.85f).coerceAtLeast(24f)
            auraShader = RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(
                    (glowColor and 0x00FFFFFF) or (0x34 shl 24),
                    (glowColor and 0x00FFFFFF) or (0x10 shl 24),
                    0x00000000,
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        auraPaint.shader = auraShader
        canvas.drawRect(cx - areaW / 2f, cy - areaH / 2f, cx + areaW / 2f, cy + areaH / 2f, auraPaint)

        // 1. Situational full-body move resolution
        val targetMove = when {
            privacyMode -> RobotBodyMove.IDLE
            forcedMove != null -> forcedMove
            else -> RobotBodyMotionEngine.resolveKeyboardMove(
                brain = brain,
                body = bodyState,
                nowMs = now,
                lastInteractionMs = lastInteractionMs,
                isVoiceListening = isVoiceListening,
                messageSentAtMs = messageSentAtMs,
                celebrationAtMs = celebrationAtMs,
            )
        }
        currentMove = targetMove

        val tapHopAt = max(localTapHopAtMs, externalTapHopAtMs)
        val petWiggleAt = max(localPetWiggleAtMs, externalPetWiggleAtMs)
        val tapElapsed = if (tapHopAt > 0L && now >= tapHopAt) now - tapHopAt else -1L
        val petElapsed = if (petWiggleAt > 0L && now >= petWiggleAt) now - petWiggleAt else -1L

        val rawTargetPose = RobotBodyMotionEngine.samplePose(
            move = targetMove,
            nowMs = now,
            tiltRollDeg = bodyState.rollDeg,
            tiltPitchDeg = bodyState.pitchDeg,
            tapHopElapsedMs = tapElapsed,
            petWiggleElapsedMs = petElapsed,
        )
        curPose = RobotBodyMotionEngine.lerpPose(curPose, rawTargetPose, (dt * 9.5f).coerceIn(0.08f, 1f))

        // 2. Situational 8 HD Eyes vs 32 Face Reactions inside full-body visor
        val isIdlePlay = (now - lastInteractionMs > 2800L) && forcedMove == null && !isVoiceListening
        val useHdEyes = if (privacyMode) {
            true
        } else {
            RobotBodyMotionEngine.shouldUseHdEyes(
                face = activeMood,
                mode = visualMode,
                hasForcedHdEye = forcedHdEye != null,
            )
        }
        val activeHdStyle = when {
            privacyMode -> HdEyeStyle.CHILL
            forcedHdEye != null -> forcedHdEye
            else -> RobotBodyMotionEngine.resolveSituationalHdEye(
                face = activeMood,
                nowMs = now,
                isVoiceListening = isVoiceListening,
                isIdlePlay = isIdlePlay,
            )
        }
        currentUsingHdEyes = useHdEyes
        currentHdStyle = activeHdStyle

        val blinkProgress = maxOf(blink.tick(), sleepFactor * 0.78f)

        val effectAgeMs = if (bodyState.lastEffectAtMs > 0L && now >= bodyState.lastEffectAtMs) {
            now - bodyState.lastEffectAtMs
        } else {
            99_999L
        }
        val activeEffect = when {
            privacyMode -> BodyEffect.NONE
            celebrationAtMs > 0L && now - celebrationAtMs in 0L..2100L -> BodyEffect.HEARTS
            bodyState.lastEffect != BodyEffect.NONE && effectAgeMs <= 2600L -> bodyState.lastEffect
            else -> BodyEffect.NONE
        }
        val finalEffectAge = if (activeEffect == BodyEffect.HEARTS && celebrationAtMs > 0L && now - celebrationAtMs in 0L..2100L) {
            now - celebrationAtMs
        } else {
            effectAgeMs
        }

        RobotBodyRenderer.draw(
            canvas = canvas,
            cx = cx,
            cy = cy + areaH * 0.02f,
            maxW = areaW * 0.96f,
            maxH = areaH * 0.94f,
            skin = skin,
            pose = curPose,
            hdEyesEnabled = useHdEyes,
            hdStyle = activeHdStyle,
            face = activeMood,
            blink = blinkProgress,
            gazeNormX = combinedGazeX,
            gazeNormY = combinedGazeY,
            isCrying = !privacyMode && (brain.isCrying || activeMood == FaceId.CRY_RIVER),
            effect = activeEffect,
            effectAgeMs = finalEffectAge,
            nowMs = now,
            visorBounceX = visorScaleX,
            visorBounceY = visorScaleY,
        )
    }
}
