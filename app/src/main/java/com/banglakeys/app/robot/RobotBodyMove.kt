package com.banglakeys.app.robot

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * ফুল-বডি রোবটের কিউট অ্যানিমেটেড মুভ ক্যাটালগ।
 */
enum class RobotBodyMove(
    val titleBn: String,
    val titleEn: String,
    val badge: String,
    val previewFace: FaceId,
    val previewHdEye: HdEyeStyle,
) {
    IDLE(
        titleBn = "দাঁড়িয়ে",
        titleEn = "Idle",
        badge = "🤖",
        previewFace = FaceId.NORMAL,
        previewHdEye = HdEyeStyle.SPARKLE,
    ),
    JUMP_WAVE(
        titleBn = "লাফ ও হাই",
        titleEn = "Jump",
        badge = "🙌",
        previewFace = FaceId.BEAMING,
        previewHdEye = HdEyeStyle.PEEK,
    ),
    HEAD_TILT(
        titleBn = "মাথা কাত",
        titleEn = "Tilt",
        badge = "🥺",
        previewFace = FaceId.CURIOUS,
        previewHdEye = HdEyeStyle.GLANCE,
    ),
    BELLY_FLOP(
        titleBn = "ঘুমন্ত চ্যাপ্টা",
        titleEn = "Sleepy",
        badge = "😴",
        previewFace = FaceId.REST,
        previewHdEye = HdEyeStyle.CHILL,
    ),
    WALK_SHUFFLE(
        titleBn = "হাঁটাচলা",
        titleEn = "Walk",
        badge = "🐾",
        previewFace = FaceId.SMILE,
        previewHdEye = HdEyeStyle.SPARKLE,
    ),
    DIZZY_WOBBLE(
        titleBn = "ঘোরাঘুরি",
        titleEn = "Dizzy",
        badge = "😵‍💫",
        previewFace = FaceId.SWIRL,
        previewHdEye = HdEyeStyle.GOOGLY,
    ),
    CRY_SIT(
        titleBn = "কান্না",
        titleEn = "Cry",
        badge = "😢",
        previewFace = FaceId.CRY_RIVER,
        previewHdEye = HdEyeStyle.PINPOINT,
    );
}

/**
 * কী-বোর্ড EMO থিমের ভিসর অভিনয় মোড:
 * - [AUTO_UNIFIED]: পরিস্থিতি বুঝে ৩২টি ফেস, ৮টি HD চোখ ও ফুল-বডি মুভ একসাথে অভিনয় করে।
 * - [HD_EYES]: ফুল-বডি ভিসরে সবসময় ৮টি অ্যানিমেটেড HD চোখ দেখায়।
 * - [CLASSIC_32]: ফুল-বডি ভিসরে সবসময় ৩২টি ক্লাসিক/এক্সপ্রেসিভ ফেস দেখায়।
 */
enum class EmoVisualMode(
    val titleBn: String,
    val titleEn: String,
    val badge: String,
) {
    AUTO_UNIFIED("পরিস্থিতি অটো", "Smart Auto", "🎭"),
    HD_EYES("৮টি HD চোখ", "8 HD Eyes", "👀"),
    CLASSIC_32("৩২টি ফেস", "32 Faces", "😀");
}

/**
 * ৬০fps প্রসিডিউরাল বডি পোজ প্যারামিটার।
 */
data class RobotBodyPose(
    val jumpLiftNorm: Float = 0f,
    val squashX: Float = 1f,
    val squashY: Float = 1f,
    val bodyTiltDeg: Float = 0f,
    val antennaAngleDeg: Float = 0f,
    val antennaBob: Float = 0f,
    val leftArmRaise: Float = 0f,
    val leftArmForward: Float = 0f,
    val rightArmRaise: Float = 0f,
    val rightArmForward: Float = 0f,
    val leftLegStep: Float = 0f,
    val rightLegStep: Float = 0f,
    val legsTuckedNorm: Float = 0f,
    val waveLinesAlpha: Float = 0f,
    val showCheekBlush: Boolean = false,
    val shadowScaleX: Float = 1f,
    val shadowAlpha: Float = 1f,
)

/**
 * রোবটের মুড-টু-মুভ ম্যাপিং, ৬০fps স্প্রিং পোজ স্যাম্পলিং ও স্মুথ ব্লেন্ডিং ইঞ্জিন।
 */
object RobotBodyMotionEngine {

    /**
     * পরিস্থিতি অনুযায়ী ৮টি HD চোখ নাকি ৩২টি মুখ-সহ ফেস রেন্ডার হবে তা নির্ধারণ করে।
     */
    fun shouldUseHdEyes(
        face: FaceId,
        mode: EmoVisualMode,
        hasForcedHdEye: Boolean = false,
    ): Boolean {
        if (hasForcedHdEye) return true
        return when (mode) {
            EmoVisualMode.HD_EYES -> true
            EmoVisualMode.CLASSIC_32 -> false
            EmoVisualMode.AUTO_UNIFIED -> when (face) {
                FaceId.NORMAL,
                FaceId.CURIOUS,
                FaceId.CALM,
                FaceId.REST,
                FaceId.BORED,
                FaceId.STARE,
                FaceId.HYPED,
                FaceId.SHOCKED,
                FaceId.DIZZY,
                FaceId.SWIRL,
                FaceId.SLY -> true
                else -> false
            }
        }
    }

    fun resolveSituationalHdEye(
        face: FaceId,
        nowMs: Long,
        isVoiceListening: Boolean = false,
        isIdlePlay: Boolean = false,
    ): HdEyeStyle {
        if (isVoiceListening) return HdEyeStyle.GLANCE
        if (isIdlePlay && face == FaceId.NORMAL) {
            return when (((nowMs / 4500L) % 4L).toInt()) {
                0 -> HdEyeStyle.SPARKLE
                1 -> HdEyeStyle.GLANCE
                2 -> HdEyeStyle.PEEK
                else -> HdEyeStyle.CHILL
            }
        }
        return HdEyeStyle.fromFace(face)
    }

    fun resolveAutoMove(brain: RobotBrain, body: EmoBodyState): RobotBodyMove = when {
        brain.isCrying -> RobotBodyMove.CRY_SIT
        brain.isFreeFalling -> RobotBodyMove.JUMP_WAVE
        brain.sleeping -> RobotBodyMove.BELLY_FLOP
        brain.mood == FaceId.DIZZY || brain.mood == FaceId.SWIRL -> RobotBodyMove.DIZZY_WOBBLE
        brain.mood == FaceId.CURIOUS || brain.mood == FaceId.STARE || brain.mood == FaceId.NERVOUS ->
            RobotBodyMove.HEAD_TILT
        brain.mood == FaceId.JOY ||
            brain.mood == FaceId.BEAMING ||
            brain.mood == FaceId.SMILE ||
            brain.mood == FaceId.WINK ||
            brain.mood == FaceId.GIGGLE ||
            brain.mood == FaceId.UWU ||
            brain.mood == FaceId.KISS_SHY ||
            brain.mood == FaceId.WINK_KISS -> RobotBodyMove.JUMP_WAVE
        brain.mood == FaceId.HYPED || brain.mood == FaceId.MISCHIEF -> RobotBodyMove.WALK_SHUFFLE
        brain.mood == FaceId.REST || brain.mood == FaceId.BORED ->
            if (brain.needs.energy < 40) RobotBodyMove.BELLY_FLOP else RobotBodyMove.HEAD_TILT
        body.isNearEar -> RobotBodyMove.HEAD_TILT
        else -> RobotBodyMove.IDLE
    }

    fun resolveKeyboardMove(
        brain: RobotBrain,
        body: EmoBodyState,
        nowMs: Long,
        lastInteractionMs: Long,
        isVoiceListening: Boolean,
        messageSentAtMs: Long,
        celebrationAtMs: Long,
    ): RobotBodyMove {
        if (brain.isCrying) return RobotBodyMove.CRY_SIT
        if (brain.isFreeFalling) return RobotBodyMove.JUMP_WAVE
        if (brain.sleeping) return RobotBodyMove.BELLY_FLOP
        if (isVoiceListening) return RobotBodyMove.HEAD_TILT
        if (messageSentAtMs > 0L && nowMs - messageSentAtMs in 0L..1350L) {
            return RobotBodyMove.JUMP_WAVE
        }
        if (celebrationAtMs > 0L && nowMs - celebrationAtMs in 0L..2100L) {
            return RobotBodyMove.JUMP_WAVE
        }
        val base = resolveAutoMove(brain, body)
        // Idle থাকা অবস্থায় কী-বোর্ডের নিচে রোবট নিজের মতো খেলাধুলা ও অভিনয় করে
        if (base == RobotBodyMove.IDLE && brain.mood == FaceId.NORMAL && nowMs - lastInteractionMs > 2800L) {
            return when (((nowMs / 4500L) % 4L).toInt()) {
                0 -> RobotBodyMove.IDLE
                1 -> RobotBodyMove.HEAD_TILT
                2 -> RobotBodyMove.JUMP_WAVE
                else -> RobotBodyMove.WALK_SHUFFLE
            }
        }
        return base
    }

    fun samplePose(
        move: RobotBodyMove,
        nowMs: Long,
        tiltRollDeg: Float = 0f,
        tiltPitchDeg: Float = 0f,
        tapHopElapsedMs: Long = -1L,
        petWiggleElapsedMs: Long = -1L,
    ): RobotBodyPose {
        val t = (nowMs % 120_000L) / 1000f
        val rollBias = (tiltRollDeg * 0.42f).coerceIn(-12f, 12f)
        val pitchSquish = (abs(tiltPitchDeg) * 0.003f).coerceIn(0f, 0.06f)

        val basePose = when (move) {
            RobotBodyMove.IDLE -> {
                val breath = sin(t * 2.4f)
                val sway = sin(t * 1.15f)
                RobotBodyPose(
                    jumpLiftNorm = max(0f, breath) * 0.015f,
                    squashX = 1f - breath * 0.024f,
                    squashY = 1f + breath * 0.026f,
                    bodyTiltDeg = sway * 2.4f + rollBias,
                    antennaAngleDeg = -8f + sin(t * 2.4f - 0.8f) * 9f - rollBias * 0.6f,
                    antennaBob = breath * 0.08f,
                    leftArmRaise = -0.18f + sin(t * 2.4f - 0.5f) * 0.08f,
                    leftArmForward = 0.25f,
                    rightArmRaise = -0.18f + sin(t * 2.4f + 0.5f) * 0.08f,
                    rightArmForward = 0.25f,
                    leftLegStep = 0f,
                    rightLegStep = 0f,
                    legsTuckedNorm = 0f,
                    waveLinesAlpha = 0f,
                    showCheekBlush = false,
                    shadowScaleX = 1f - breath * 0.03f,
                    shadowAlpha = 0.92f,
                )
            }

            RobotBodyMove.JUMP_WAVE -> {
                val cycle = (t * 1.35f) % 1f
                val jumpArc = sin(cycle * PI.toFloat()).coerceAtLeast(0f)
                val squashWave = if (cycle < 0.14f) sin((cycle / 0.14f) * PI.toFloat()) else 0f
                val waveSweep = sin(t * 11.5f)
                RobotBodyPose(
                    jumpLiftNorm = jumpArc * 0.28f,
                    squashX = 1f + squashWave * 0.14f - jumpArc * 0.07f,
                    squashY = 1f - squashWave * 0.12f + jumpArc * 0.08f,
                    bodyTiltDeg = -4.5f + sin(t * 2.7f) * 3.5f + rollBias * 0.6f,
                    antennaAngleDeg = -14f - jumpArc * 10f + sin(t * 8f) * 9f,
                    antennaBob = jumpArc * 0.18f,
                    leftArmRaise = 0.15f + jumpArc * 0.25f,
                    leftArmForward = 0.1f,
                    rightArmRaise = 0.86f + waveSweep * 0.18f,
                    rightArmForward = -0.15f,
                    leftLegStep = jumpArc * 0.35f,
                    rightLegStep = jumpArc * 0.22f,
                    legsTuckedNorm = jumpArc * 0.22f,
                    waveLinesAlpha = (0.45f + 0.55f * abs(waveSweep)).coerceIn(0f, 1f),
                    showCheekBlush = true,
                    shadowScaleX = (1f - jumpArc * 0.38f).coerceIn(0.55f, 1.15f),
                    shadowAlpha = (1f - jumpArc * 0.52f).coerceIn(0.35f, 1f),
                )
            }

            RobotBodyMove.HEAD_TILT -> {
                val breath = sin(t * 2.1f)
                val nod = sin(t * 1.4f)
                RobotBodyPose(
                    jumpLiftNorm = max(0f, breath) * 0.01f,
                    squashX = 1f - breath * 0.018f,
                    squashY = 1f + breath * 0.02f,
                    bodyTiltDeg = 14.5f + nod * 3.2f + rollBias * 0.55f,
                    antennaAngleDeg = 12f + sin(t * 2.8f - 0.9f) * 10f,
                    antennaBob = breath * 0.06f,
                    leftArmRaise = 0.18f + nod * 0.06f,
                    leftArmForward = 0.05f,
                    rightArmRaise = 0.22f - nod * 0.06f,
                    rightArmForward = 0.05f,
                    leftLegStep = 0f,
                    rightLegStep = 0.05f,
                    legsTuckedNorm = 0f,
                    waveLinesAlpha = 0f,
                    showCheekBlush = true,
                    shadowScaleX = 0.98f,
                    shadowAlpha = 0.95f,
                )
            }

            RobotBodyMove.BELLY_FLOP -> {
                val snore = sin(t * 1.6f)
                RobotBodyPose(
                    jumpLiftNorm = -0.13f,
                    squashX = 1.24f + snore * 0.035f,
                    squashY = 0.64f - snore * 0.03f,
                    bodyTiltDeg = sin(t * 0.8f) * 1.5f + rollBias * 0.2f,
                    antennaAngleDeg = 18f + sin(t * 1.6f - 0.6f) * 6f,
                    antennaBob = -0.05f + snore * 0.04f,
                    leftArmRaise = -0.38f,
                    leftArmForward = 0.68f,
                    rightArmRaise = -0.38f,
                    rightArmForward = 0.68f,
                    leftLegStep = 0f,
                    rightLegStep = 0f,
                    legsTuckedNorm = 1f,
                    waveLinesAlpha = 0f,
                    showCheekBlush = false,
                    shadowScaleX = 1.28f,
                    shadowAlpha = 1f,
                )
            }

            RobotBodyMove.WALK_SHUFFLE -> {
                val phase = t * 5.4f
                val step = sin(phase)
                val hop = abs(step)
                RobotBodyPose(
                    jumpLiftNorm = hop * 0.09f,
                    squashX = 1f + (1f - hop) * 0.05f - hop * 0.04f,
                    squashY = 1f - (1f - hop) * 0.04f + hop * 0.05f,
                    bodyTiltDeg = step * 7.5f + rollBias * 0.5f,
                    antennaAngleDeg = -step * 14f - 6f,
                    antennaBob = hop * 0.12f,
                    leftArmRaise = -0.05f - step * 0.45f,
                    leftArmForward = 0.12f,
                    rightArmRaise = -0.05f + step * 0.45f,
                    rightArmForward = 0.12f,
                    leftLegStep = max(0f, step) * 0.65f,
                    rightLegStep = max(0f, -step) * 0.65f,
                    legsTuckedNorm = 0f,
                    waveLinesAlpha = 0f,
                    showCheekBlush = true,
                    shadowScaleX = 1f - hop * 0.14f,
                    shadowAlpha = 0.85f - hop * 0.15f,
                )
            }

            RobotBodyMove.DIZZY_WOBBLE -> {
                val sway = sin(t * 4.4f)
                val wobble = cos(t * 3.1f)
                RobotBodyPose(
                    jumpLiftNorm = abs(wobble) * 0.03f,
                    squashX = 1f + sway * 0.05f,
                    squashY = 1f - sway * 0.05f,
                    bodyTiltDeg = sway * 13f + rollBias * 0.5f,
                    antennaAngleDeg = -sway * 26f,
                    antennaBob = wobble * 0.14f,
                    leftArmRaise = 0.32f + sway * 0.35f,
                    leftArmForward = 0.1f,
                    rightArmRaise = 0.32f - sway * 0.35f,
                    rightArmForward = 0.1f,
                    leftLegStep = max(0f, sway) * 0.2f,
                    rightLegStep = max(0f, -sway) * 0.2f,
                    legsTuckedNorm = 0f,
                    waveLinesAlpha = 0f,
                    showCheekBlush = false,
                    shadowScaleX = 1.02f,
                    shadowAlpha = 0.9f,
                )
            }

            RobotBodyMove.CRY_SIT -> {
                val sob = abs(sin(t * 5.2f))
                val shiver = sin(t * 22f) * 1.8f
                RobotBodyPose(
                    jumpLiftNorm = -0.07f + sob * 0.02f,
                    squashX = 1.08f + sob * 0.04f,
                    squashY = 0.86f - sob * 0.04f,
                    bodyTiltDeg = shiver + rollBias * 0.3f,
                    antennaAngleDeg = 22f + sob * 8f,
                    antennaBob = -0.08f,
                    leftArmRaise = 0.16f + sob * 0.14f,
                    leftArmForward = 0.58f,
                    rightArmRaise = 0.16f + sob * 0.14f,
                    rightArmForward = 0.58f,
                    leftLegStep = 0f,
                    rightLegStep = 0f,
                    legsTuckedNorm = 0.55f,
                    waveLinesAlpha = 0f,
                    showCheekBlush = false,
                    shadowScaleX = 1.12f,
                    shadowAlpha = 0.95f,
                )
            }
        }

        // Apply interactive tap hop + pet wiggle overlays
        var out = basePose.copy(
            squashX = (basePose.squashX + pitchSquish).coerceIn(0.8f, 1.35f),
            squashY = (basePose.squashY - pitchSquish).coerceIn(0.55f, 1.2f),
        )
        if (tapHopElapsedMs in 0L..460L) {
            val p = tapHopElapsedMs / 460f
            val arc = sin(p * PI.toFloat())
            out = out.copy(
                jumpLiftNorm = out.jumpLiftNorm + arc * 0.16f,
                squashX = out.squashX - arc * 0.06f,
                squashY = out.squashY + arc * 0.08f,
                rightArmRaise = max(out.rightArmRaise, 0.72f * arc),
                waveLinesAlpha = max(out.waveLinesAlpha, arc * 0.85f),
                shadowScaleX = (out.shadowScaleX - arc * 0.2f).coerceAtLeast(0.55f),
            )
        }
        if (petWiggleElapsedMs in 0L..800L) {
            val p = petWiggleElapsedMs / 800f
            val wiggle = sin(p * PI.toFloat() * 4f) * (1f - p)
            out = out.copy(
                bodyTiltDeg = out.bodyTiltDeg + wiggle * 9f,
                antennaAngleDeg = out.antennaAngleDeg - wiggle * 16f,
                showCheekBlush = true,
            )
        }
        return out
    }

    fun lerpPose(a: RobotBodyPose, b: RobotBodyPose, t: Float): RobotBodyPose {
        val k = t.coerceIn(0f, 1f)
        fun mix(x: Float, y: Float) = x + (y - x) * k
        return RobotBodyPose(
            jumpLiftNorm = mix(a.jumpLiftNorm, b.jumpLiftNorm),
            squashX = mix(a.squashX, b.squashX),
            squashY = mix(a.squashY, b.squashY),
            bodyTiltDeg = mix(a.bodyTiltDeg, b.bodyTiltDeg),
            antennaAngleDeg = mix(a.antennaAngleDeg, b.antennaAngleDeg),
            antennaBob = mix(a.antennaBob, b.antennaBob),
            leftArmRaise = mix(a.leftArmRaise, b.leftArmRaise),
            leftArmForward = mix(a.leftArmForward, b.leftArmForward),
            rightArmRaise = mix(a.rightArmRaise, b.rightArmRaise),
            rightArmForward = mix(a.rightArmForward, b.rightArmForward),
            leftLegStep = mix(a.leftLegStep, b.leftLegStep),
            rightLegStep = mix(a.rightLegStep, b.rightLegStep),
            legsTuckedNorm = mix(a.legsTuckedNorm, b.legsTuckedNorm),
            waveLinesAlpha = mix(a.waveLinesAlpha, b.waveLinesAlpha),
            showCheekBlush = if (k < 0.5f) a.showCheekBlush else b.showCheekBlush,
            shadowScaleX = mix(a.shadowScaleX, b.shadowScaleX),
            shadowAlpha = mix(a.shadowAlpha, b.shadowAlpha),
        )
    }
}
