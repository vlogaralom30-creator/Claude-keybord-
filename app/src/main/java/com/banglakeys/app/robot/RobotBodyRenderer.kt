package com.banglakeys.app.robot

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * ৬০fps সফট-3D ফুল-বডি কিউট EMO রোবট রেন্ডারার।
 * রেফারেন্স ক্যারেক্টারের মতো চাবি বডি, স্প্রিং বল অ্যান্টেনা, স্টাবি হাত-পা,
 * গ্রাউন্ড শ্যাডো এবং ভিসরের ভেতরে লাইভ HD চোখ / ৩২টি ফেস + পার্টিকেল আঁকে।
 */
object RobotBodyRenderer {

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bodyRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val limbRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val visorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val visorOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val faceEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val motionLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val collarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun draw(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        maxW: Float,
        maxH: Float,
        skin: RobotBodySkin,
        pose: RobotBodyPose,
        hdEyesEnabled: Boolean,
        hdStyle: HdEyeStyle,
        face: FaceId,
        blink: Float = 0f,
        gazeNormX: Float = 0f,
        gazeNormY: Float = 0f,
        isCrying: Boolean = false,
        effect: BodyEffect = BodyEffect.NONE,
        effectAgeMs: Long = 0L,
        nowMs: Long = System.currentTimeMillis(),
        visorBounceX: Float = 1f,
        visorBounceY: Float = 1f,
    ): RectF {
        if (maxW <= 0f || maxH <= 0f) return RectF()

        val unit = min(maxW * 0.90f, maxH * 0.84f).coerceAtLeast(60f)
        val floorY = cy + maxH * 0.41f

        // 1. Dynamic Ground Drop Shadow
        val shRx = unit * 0.37f * pose.shadowScaleX
        val shRy = (unit * 0.072f).coerceAtLeast(6f)
        val shBaseAlpha = ((skin.shadowColor ushr 24) and 0xFF)
        val shRgb = skin.shadowColor and 0x00FFFFFF
        val shCenterAlpha = (shBaseAlpha * pose.shadowAlpha).toInt().coerceIn(12, 160)
        shadowPaint.shader = RadialGradient(
            cx,
            floorY,
            shRx.coerceAtLeast(10f),
            intArrayOf(
                shRgb or (shCenterAlpha shl 24),
                shRgb or ((shCenterAlpha / 3) shl 24),
                0x00000000,
            ),
            floatArrayOf(0f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.save()
        canvas.scale(1f, shRy / shRx.coerceAtLeast(1f), cx, floorY)
        canvas.drawCircle(cx, floorY, shRx, shadowPaint)
        canvas.restore()
        shadowPaint.shader = null

        // Body dimensions & anchor
        val bodyW = unit * 0.68f * pose.squashX
        val bodyH = unit * 0.60f * pose.squashY
        val legStandH = unit * 0.13f * (1f - pose.legsTuckedNorm * 0.82f)
        val liftPx = pose.jumpLiftNorm * unit * 0.68f
        val bodyBottomY = floorY - legStandH * 0.55f - liftPx
        val torsoCx = cx
        val torsoCy = bodyBottomY - bodyH * 0.50f

        canvas.save()
        canvas.rotate(pose.bodyTiltDeg, cx, bodyBottomY)

        // 2. Stubby Legs (behind/under lower torso)
        drawLegs(
            canvas = canvas,
            torsoCx = torsoCx,
            bodyBottomY = bodyBottomY,
            bodyW = bodyW,
            unit = unit,
            skin = skin,
            pose = pose,
        )

        // 3. Bouncy Ball Antenna (top-left of head dome like reference character)
        drawAntenna(
            canvas = canvas,
            torsoCx = torsoCx,
            torsoTopY = torsoCy - bodyH * 0.48f,
            bodyW = bodyW,
            unit = unit,
            skin = skin,
            pose = pose,
        )

        // 4. Chubby Rounded 3D Torso Silhouette
        val torsoPath = buildChubbyTorsoPath(torsoCx, torsoCy, bodyW, bodyH, pose.legsTuckedNorm)
        bodyPaint.shader = RadialGradient(
            torsoCx - bodyW * 0.22f,
            torsoCy - bodyH * 0.30f,
            (maxOf(bodyW, bodyH) * 0.92f).coerceAtLeast(16f),
            intArrayOf(skin.bodyHighlight, skin.bodyCore, skin.bodyShadow),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(torsoPath, bodyPaint)
        bodyPaint.shader = null

        bodyRimPaint.color = skin.bodyRim
        bodyRimPaint.alpha = if (skin.isLightVisor) 115 else 190
        bodyRimPaint.strokeWidth = (unit * (if (skin.isLightVisor) 0.011f else 0.015f)).coerceAtLeast(1.6f)
        canvas.drawPath(torsoPath, bodyRimPaint)

        // 5. Recessed Pill Visor + Live HD Eyes / Classic Faces + Particles
        val visorW = bodyW * 0.72f
        val visorH = bodyH * (0.48f + pose.legsTuckedNorm * 0.08f)
        val visorCx = torsoCx + gazeNormX * bodyW * 0.018f
        val visorCy = torsoCy - bodyH * (0.04f - pose.legsTuckedNorm * 0.04f) + gazeNormY * bodyH * 0.015f
        val visorRect = RectF(
            visorCx - visorW / 2f,
            visorCy - visorH / 2f,
            visorCx + visorW / 2f,
            visorCy + visorH / 2f,
        )
        val visorRadius = visorH * 0.46f

        visorPaint.shader = LinearGradient(
            visorCx,
            visorRect.top,
            visorCx,
            visorRect.bottom,
            skin.visorTop,
            skin.visorBottom,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(visorRect, visorRadius, visorRadius, visorPaint)
        visorPaint.shader = null

        visorOutlinePaint.color = skin.visorOutline
        visorOutlinePaint.strokeWidth = (unit * 0.012f).coerceAtLeast(1.5f)
        canvas.drawRoundRect(visorRect, visorRadius, visorRadius, visorOutlinePaint)

        // Subtle cheek blush inside visor when happy/curious/petted
        if (pose.showCheekBlush) {
            blushPaint.color = skin.blushColor
            blushPaint.alpha = if (skin.isLightVisor) 115 else 85
            val cheekY = visorCy + visorH * 0.19f
            val cheekDx = visorW * 0.30f
            val cheekRx = visorW * 0.085f
            val cheekRy = visorH * 0.075f
            canvas.drawOval(RectF(visorCx - cheekDx - cheekRx, cheekY - cheekRy, visorCx - cheekDx + cheekRx, cheekY + cheekRy), blushPaint)
            canvas.drawOval(RectF(visorCx + cheekDx - cheekRx, cheekY - cheekRy, visorCx + cheekDx + cheekRx, cheekY + cheekRy), blushPaint)
        }

        val eyeGap = visorW * 0.43f
        val eyeRadius = min(visorH * 0.29f, visorW * 0.17f).coerceAtLeast(8f)

        if (hdEyesEnabled) {
            HdEyeRenderer.draw(
                canvas = canvas,
                style = hdStyle,
                cx = visorCx,
                cy = visorCy,
                plateW = visorW,
                plateH = visorH,
                eyeGap = eyeGap,
                eyeRadius = eyeRadius,
                blink = blink,
                gazeNormX = gazeNormX,
                gazeNormY = gazeNormY,
                scaleX = visorBounceX,
                scaleY = visorBounceY,
                accentColor = skin.eyeAccent,
                platePaint = null,
                plateOutline = null,
                nowMs = nowMs,
                glow = !skin.isLightVisor,
                lightVisor = skin.isLightVisor,
            )
        } else {
            faceEyePaint.color = skin.eyeAccent
            FaceRenderer.draw(
                canvas = canvas,
                face = face,
                cx = visorCx,
                cy = visorCy,
                plateW = visorW,
                plateH = visorH,
                eyeGap = eyeGap * 0.92f,
                eyeSize = visorH * 0.25f,
                blink = blink,
                eyePaint = faceEyePaint,
                platePaint = null,
                plateOutline = null,
                gazeX = gazeNormX * (visorW * 0.09f),
                gazeY = gazeNormY * (visorH * 0.09f),
                scaleX = visorBounceX,
                scaleY = visorBounceY,
                glow = !skin.isLightVisor,
            )
        }

        EmoParticles.draw(
            canvas = canvas,
            cx = visorCx,
            cy = visorCy,
            plateW = visorW,
            plateH = visorH,
            eyeGap = eyeGap,
            isCrying = isCrying,
            mood = if (hdEyesEnabled && hdStyle == HdEyeStyle.GOOGLY) FaceId.SWIRL else face,
            effect = effect,
            effectAgeMs = effectAgeMs,
            nowMs = nowMs,
        )

        // 6. Stubby Front Arms + Celebratory Wave Motion Lines
        drawArms(
            canvas = canvas,
            torsoCx = torsoCx,
            torsoCy = torsoCy,
            bodyW = bodyW,
            bodyH = bodyH,
            unit = unit,
            skin = skin,
            pose = pose,
            nowMs = nowMs,
        )

        canvas.restore()

        return RectF(
            torsoCx - bodyW * 0.56f,
            torsoCy - bodyH * 0.70f,
            torsoCx + bodyW * 0.56f,
            floorY + shRy,
        )
    }

    private fun buildChubbyTorsoPath(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        tucked: Float,
    ): Path {
        val hw = w / 2f
        val hh = h / 2f
        val topY = cy - hh
        val botY = cy + hh
        val bottomRound = hw * (0.28f + tucked * 0.34f)
        return Path().apply {
            // Smooth chubby pear-capsule dome matching the reference plush robot shape
            moveTo(cx, topY)
            cubicTo(
                cx + hw * 0.72f, topY,
                cx + hw * 1.02f, cy - hh * 0.36f,
                cx + hw, cy + hh * 0.26f,
            )
            cubicTo(
                cx + hw * 0.98f, botY - bottomRound * 0.35f,
                cx + hw * 0.78f, botY,
                cx + hw * 0.32f, botY - hh * (0.04f * (1f - tucked)),
            )
            // Subtle inner arch between legs on bottom hem (flattens in belly-flop)
            quadTo(cx, botY - hh * (0.14f * (1f - tucked)), cx - hw * 0.32f, botY - hh * (0.04f * (1f - tucked)))
            cubicTo(
                cx - hw * 0.78f, botY,
                cx - hw * 0.98f, botY - bottomRound * 0.35f,
                cx - hw, cy + hh * 0.26f,
            )
            cubicTo(
                cx - hw * 1.02f, cy - hh * 0.36f,
                cx - hw * 0.72f, topY,
                cx, topY,
            )
            close()
        }
    }

    private fun drawAntenna(
        canvas: Canvas,
        torsoCx: Float,
        torsoTopY: Float,
        bodyW: Float,
        unit: Float,
        skin: RobotBodySkin,
        pose: RobotBodyPose,
    ) {
        val baseX = torsoCx - bodyW * 0.22f
        val baseY = torsoTopY + unit * 0.025f
        val stemLen = unit * 0.135f * (1f + pose.antennaBob * 0.45f)
        val ballR = (unit * 0.068f).coerceAtLeast(6f)

        canvas.save()
        canvas.rotate(pose.antennaAngleDeg, baseX, baseY)

        // Stem collar ring
        collarPaint.color = skin.bodyShadow
        canvas.drawOval(
            RectF(baseX - ballR * 0.65f, baseY - ballR * 0.22f, baseX + ballR * 0.65f, baseY + ballR * 0.24f),
            collarPaint,
        )

        // Thick soft stem
        val stemW = (ballR * 0.44f).coerceAtLeast(3f)
        limbPaint.shader = LinearGradient(
            baseX - stemW, baseY - stemLen,
            baseX + stemW, baseY,
            skin.bodyHighlight,
            skin.bodyShadow,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(
            RectF(baseX - stemW / 2f, baseY - stemLen, baseX + stemW / 2f, baseY),
            stemW / 2f,
            stemW / 2f,
            limbPaint,
        )
        limbPaint.shader = null

        // 3D Ball tip
        val tipY = baseY - stemLen - ballR * 0.45f
        limbPaint.shader = RadialGradient(
            baseX - ballR * 0.32f,
            tipY - ballR * 0.34f,
            ballR * 1.5f,
            intArrayOf(skin.bodyHighlight, skin.limbCore, skin.limbShadow),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(baseX, tipY, ballR, limbPaint)
        limbPaint.shader = null

        if (!skin.isLightVisor) {
            // Glowing rim on Classic Cyan antenna orb
            limbRimPaint.color = skin.bodyRim
            limbRimPaint.alpha = 200
            limbRimPaint.strokeWidth = (ballR * 0.16f).coerceAtLeast(1.5f)
            canvas.drawCircle(baseX, tipY, ballR, limbRimPaint)
        }

        canvas.restore()
    }

    private fun drawLegs(
        canvas: Canvas,
        torsoCx: Float,
        bodyBottomY: Float,
        bodyW: Float,
        unit: Float,
        skin: RobotBodySkin,
        pose: RobotBodyPose,
    ) {
        if (pose.legsTuckedNorm > 0.92f) return
        val legScale = (1f - pose.legsTuckedNorm * 0.72f).coerceAtLeast(0.25f)
        val legW = unit * 0.165f
        val legH = unit * 0.17f * legScale
        val gap = bodyW * 0.25f

        for (isRight in listOf(false, true)) {
            val stepLift = (if (isRight) pose.rightLegStep else pose.leftLegStep) * unit * 0.08f
            val lx = torsoCx + (if (isRight) gap else -gap)
            val ly = bodyBottomY - legH * 0.25f - stepLift
            val rect = RectF(lx - legW / 2f, ly - legH * 0.35f, lx + legW / 2f, ly + legH * 0.65f)
            val r = legW * 0.48f

            limbPaint.shader = RadialGradient(
                lx - legW * 0.18f,
                ly,
                legH * 1.1f,
                intArrayOf(skin.limbCore, skin.limbShadow),
                floatArrayOf(0.15f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(rect, r, r, limbPaint)
            limbPaint.shader = null

            if (!skin.isLightVisor) {
                limbRimPaint.color = skin.bodyRim
                limbRimPaint.alpha = 150
                limbRimPaint.strokeWidth = (unit * 0.01f).coerceAtLeast(1.3f)
                canvas.drawRoundRect(rect, r, r, limbRimPaint)
            }
        }
    }

    private fun drawArms(
        canvas: Canvas,
        torsoCx: Float,
        torsoCy: Float,
        bodyW: Float,
        bodyH: Float,
        unit: Float,
        skin: RobotBodySkin,
        pose: RobotBodyPose,
        nowMs: Long,
    ) {
        val armW = unit * 0.145f
        val armH = unit * 0.225f
        val shoulderY = torsoCy + bodyH * 0.14f

        for (isRight in listOf(false, true)) {
            val raise = if (isRight) pose.rightArmRaise else pose.leftArmRaise
            val fwd = if (isRight) pose.rightArmForward else pose.leftArmForward
            val sideSign = if (isRight) 1f else -1f

            val shoulderX = torsoCx + sideSign * bodyW * (0.45f - fwd * 0.18f)
            val armY = shoulderY - raise.coerceAtLeast(0f) * bodyH * 0.14f + fwd * bodyH * 0.14f
            val angleDeg = sideSign * (-raise * 150f)

            canvas.save()
            canvas.rotate(angleDeg, shoulderX, armY)

            // Soft drop shadow behind stubby arm for 3D depth
            shadowPaint.color = skin.shadowColor
            val shRect = RectF(
                shoulderX - armW * 0.52f,
                armY - armH * 0.20f,
                shoulderX + armW * 0.52f,
                armY + armH * 0.80f,
            )
            canvas.drawRoundRect(shRect, armW * 0.48f, armW * 0.48f, shadowPaint)

            val armRect = RectF(
                shoulderX - armW * 0.50f,
                armY - armH * 0.28f,
                shoulderX + armW * 0.50f,
                armY + armH * 0.72f,
            )
            limbPaint.shader = RadialGradient(
                shoulderX - armW * 0.18f,
                armY - armH * 0.08f,
                armH * 1.15f,
                intArrayOf(skin.bodyHighlight, skin.limbCore, skin.limbShadow),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(armRect, armW * 0.50f, armW * 0.50f, limbPaint)
            limbPaint.shader = null

            if (!skin.isLightVisor) {
                limbRimPaint.color = skin.bodyRim
                limbRimPaint.alpha = 185
                limbRimPaint.strokeWidth = (unit * 0.011f).coerceAtLeast(1.4f)
                canvas.drawRoundRect(armRect, armW * 0.50f, armW * 0.50f, limbRimPaint)
            }

            // Cute celebratory wave motion lines (\ | /) near waving hand (Pose 2 of reference)
            if (isRight && pose.waveLinesAlpha > 0.05f && raise > 0.45f) {
                val pulse = 0.78f + 0.22f * sin((nowMs % 600L) / 600f * 2f * PI.toFloat())
                motionLinePaint.color = skin.motionLineColor
                motionLinePaint.alpha = (pose.waveLinesAlpha * 230).toInt().coerceIn(0, 255)
                motionLinePaint.strokeWidth = (unit * 0.018f).coerceAtLeast(2.2f)
                val tipX = shoulderX
                val tipY = armRect.bottom + armW * 0.35f
                val len = armW * 0.65f * pulse
                // Draw 2-3 playful burst ticks radiating outward
                for (tick in listOf(-26f, 8f, 38f)) {
                    val rad = (tick + 65f) * PI.toFloat() / 180f
                    val sx = tipX + cos(rad) * (armW * 0.55f)
                    val sy = tipY + sin(rad) * (armW * 0.55f)
                    val ex = sx + cos(rad) * len
                    val ey = sy + sin(rad) * len
                    canvas.drawLine(sx, sy, ex, ey, motionLinePaint)
                }
            }

            canvas.restore()
        }
    }
}
