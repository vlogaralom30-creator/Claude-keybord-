package com.banglakeys.app.robot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * ব্যবহারকারীর রেফারেন্স শীটের ১২টি EMO ফেস — সবগুলো HD vector path দিয়ে আঁকা।
 * প্রতিটি ফেসে মসৃণ [blink], চোখের [gazeX]/[gazeY] নড়াচড়া, bounce [scaleX]/[scaleY],
 * এবং ঐচ্ছিক neon [glow] হ্যালো সাপোর্ট আছে।
 */
object FaceRenderer {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5EE8FF.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val coralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF5A76.toInt()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val mouthFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0A1420.toInt()
        style = Paint.Style.FILL
    }

    fun draw(
        canvas: Canvas,
        face: FaceId,
        cx: Float,
        cy: Float,
        plateW: Float,
        plateH: Float,
        eyeGap: Float,
        eyeSize: Float,
        blink: Float,
        eyePaint: Paint,
        platePaint: Paint? = null,
        plateOutline: Paint? = null,
        gazeX: Float = 0f,
        gazeY: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        glow: Boolean = false,
    ) {
        if (plateW <= 0f || plateH <= 0f || eyeSize <= 0f) return

        if (platePaint != null) {
            val r = plateH * 0.24f
            val rect = RectF(cx - plateW / 2f, cy - plateH / 2f, cx + plateW / 2f, cy + plateH / 2f)
            canvas.drawRoundRect(rect, r, r, platePaint)
            if (plateOutline != null) canvas.drawRoundRect(rect, r, r, plateOutline)
        }

        val eyeCx = cx + gazeX
        val eyeCy = cy + gazeY
        val ex1 = eyeCx - eyeGap / 2f
        val ex2 = eyeCx + eyeGap / 2f
        val s = eyeSize
        val baseAlpha = eyePaint.alpha
        val baseColor = eyePaint.color

        eyePaint.style = Paint.Style.FILL
        eyePaint.strokeCap = Paint.Cap.ROUND
        eyePaint.strokeJoin = Paint.Join.ROUND

        fun drawPathWithGlow(
            anchorX: Float,
            anchorY: Float,
            squashBlink: Boolean,
            paint: Paint,
            block: (Path) -> Unit,
        ) {
            val p = Path()
            block(p)
            canvas.save()
            val sy = if (squashBlink) ((1f - blink).coerceAtLeast(0.045f)) * scaleY else scaleY
            canvas.scale(scaleX, sy, anchorX, anchorY)

            val pAlpha = paint.alpha
            val pColor = paint.color
            if (glow && pAlpha > 20) {
                val isStroked = paint.style == Paint.Style.STROKE
                val coreStroke = if (isStroked) paint.strokeWidth else 0f

                glowPaint.color = pColor
                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeCap = Paint.Cap.ROUND
                glowPaint.strokeJoin = Paint.Join.ROUND

                glowPaint.alpha = (pAlpha * 0.16f).toInt().coerceIn(8, 255)
                glowPaint.strokeWidth = (coreStroke + s * 0.44f).coerceAtLeast(4f)
                canvas.drawPath(p, glowPaint)

                glowPaint.alpha = (pAlpha * 0.36f).toInt().coerceIn(14, 255)
                glowPaint.strokeWidth = (coreStroke + s * 0.19f).coerceAtLeast(2.5f)
                canvas.drawPath(p, glowPaint)
            }

            canvas.drawPath(p, paint)
            canvas.restore()
        }

        fun drawEyePath(anchorX: Float, block: (Path) -> Unit) =
            drawPathWithGlow(anchorX, eyeCy, squashBlink = true, paint = eyePaint, block = block)

        val mEyeY = eyeCy - s * 0.16f
        val mouthY = eyeCy + s * 0.48f
        val mGap = eyeGap * 0.96f
        val mx1 = eyeCx - mGap / 2f
        val mx2 = eyeCx + mGap / 2f

        fun drawUpperEye(anchorX: Float, block: (Path) -> Unit) =
            drawPathWithGlow(anchorX, mEyeY, squashBlink = true, paint = eyePaint, block = block)

        fun drawMouth(block: (Path) -> Unit) =
            drawPathWithGlow(eyeCx, mouthY, squashBlink = false, paint = eyePaint, block = block)

        fun drawBlush(alpha: Int = 70) {
            if (plateW < 84f) return
            coralPaint.color = 0xFFFF6B9C.toInt()
            coralPaint.style = Paint.Style.FILL
            coralPaint.alpha = ((alpha / 255f) * baseAlpha).toInt().coerceIn(0, 255)
            val rx = s * 0.30f
            val ry = s * 0.14f
            val by = mEyeY + s * 0.42f
            canvas.drawOval(RectF(mx1 - rx, by - ry, mx1 + rx, by + ry), coralPaint)
            canvas.drawOval(RectF(mx2 - rx, by - ry, mx2 + rx, by + ry), coralPaint)
        }

        fun drawSmileCurve(y: Float, w: Float = s * 0.36f, h: Float = s * 0.24f) {
            strokeStyle(eyePaint, s * 0.15f)
            drawMouth {
                val r = RectF(eyeCx - w, y - h, eyeCx + w, y + h)
                it.addArc(r, 18f, 144f)
            }
        }

        fun drawFrownCurve(y: Float, w: Float = s * 0.36f, h: Float = s * 0.24f) {
            strokeStyle(eyePaint, s * 0.15f)
            drawMouth {
                val r = RectF(eyeCx - w, y - h * 0.15f, eyeCx + w, y + h * 1.45f)
                it.addArc(r, 200f, 140f)
            }
        }

        fun drawCatMouth(y: Float) {
            strokeStyle(eyePaint, s * 0.14f)
            val w = s * 0.24f
            val h = s * 0.20f
            drawMouth {
                it.moveTo(eyeCx - w * 1.25f, y - h * 0.15f)
                it.quadTo(eyeCx - w * 0.62f, y + h, eyeCx, y - h * 0.08f)
                it.quadTo(eyeCx + w * 0.62f, y + h, eyeCx + w * 1.25f, y - h * 0.15f)
            }
        }

        fun drawKiss3Mouth(y: Float) {
            strokeStyle(eyePaint, s * 0.14f)
            val w = s * 0.16f
            val h = s * 0.26f
            drawMouth {
                it.moveTo(eyeCx - w * 0.35f, y - h * 0.72f)
                it.quadTo(eyeCx + w * 0.85f, y - h * 0.36f, eyeCx - w * 0.15f, y)
                it.quadTo(eyeCx + w * 0.85f, y + h * 0.36f, eyeCx - w * 0.35f, y + h * 0.72f)
            }
        }

        fun drawOpenSmileMouth(y: Float, w: Float = s * 0.38f, h: Float = s * 0.34f) {
            val rect = RectF(eyeCx - w, y - h * 0.55f, eyeCx + w, y + h * 0.85f)
            val mouthPath = Path().apply {
                moveTo(rect.left, y - h * 0.15f)
                lineTo(rect.right, y - h * 0.15f)
                arcTo(rect, 0f, 180f, false)
                close()
            }
            mouthFillPaint.alpha = (baseAlpha * 0.85f).toInt().coerceIn(0, 255)
            canvas.drawPath(mouthPath, mouthFillPaint)

            coralPaint.color = 0xFFFF6B9C.toInt()
            coralPaint.style = Paint.Style.FILL
            coralPaint.alpha = (baseAlpha * 0.90f).toInt().coerceIn(0, 255)
            val tongueRect = RectF(eyeCx - w * 0.56f, y + h * 0.04f, eyeCx + w * 0.56f, y + h * 0.78f)
            canvas.drawOval(tongueRect, coralPaint)

            strokeStyle(eyePaint, s * 0.12f)
            drawMouth { it.addPath(mouthPath) }
        }

        fun drawOpenUpsetMouth(y: Float, w: Float = s * 0.32f, h: Float = s * 0.28f) {
            val rect = RectF(eyeCx - w, y - h * 0.50f, eyeCx + w, y + h * 0.78f)
            val mouthPath = Path().apply {
                moveTo(rect.left, y + h * 0.25f)
                arcTo(rect, 180f, 180f, false)
                close()
            }
            coralPaint.color = 0xFFFF5A76.toInt()
            coralPaint.style = Paint.Style.FILL
            coralPaint.alpha = (baseAlpha * 0.88f).toInt().coerceIn(0, 255)
            canvas.drawPath(mouthPath, coralPaint)

            strokeStyle(eyePaint, s * 0.12f)
            drawMouth { it.addPath(mouthPath) }
        }

        fun drawChevron(x: Float, y: Float, pointRight: Boolean) {
            strokeStyle(eyePaint, s * 0.16f)
            val dir = if (pointRight) 1f else -1f
            drawUpperEye(x) {
                it.moveTo(x - dir * s * 0.28f, y - s * 0.22f)
                it.lineTo(x + dir * s * 0.22f, y)
                it.lineTo(x - dir * s * 0.28f, y + s * 0.22f)
            }
        }

        fun drawRoundEye(x: Float, y: Float, r: Float) {
            eyePaint.style = Paint.Style.FILL
            drawUpperEye(x) { it.addCircle(x, y, r, Path.Direction.CW) }
        }

        when (face) {
            // Row 1, Col 1: (◡ ◡) — relaxed outward-curved closed crescent arcs
            FaceId.REST -> {
                strokeStyle(eyePaint, s * 0.24f)
                for ((x, tilt) in listOf(ex1 to -9f, ex2 to 9f)) {
                    canvas.save()
                    canvas.rotate(tilt, x, eyeCy)
                    val r = RectF(x - s * 0.88f, eyeCy - s * 0.62f, x + s * 0.88f, eyeCy + s * 0.26f)
                    drawEyePath(x) { it.addArc(r, 18f, 144f) }
                    canvas.restore()
                }
            }

            // Row 1, Col 2: (ˊ| ˊ|) — tall vertical pill eyes + raised curved eyebrows
            FaceId.CURIOUS -> {
                for ((x, tilt) in listOf(ex1 to -12f, ex2 to 12f)) {
                    eyePaint.style = Paint.Style.FILL
                    val r = RectF(x - s * 0.17f, eyeCy - s * 0.38f, x + s * 0.17f, eyeCy + s * 0.66f)
                    drawEyePath(x) { it.addRoundRect(r, s * 0.13f, s * 0.13f, Path.Direction.CW) }

                    strokeStyle(eyePaint, s * 0.15f)
                    canvas.save()
                    canvas.rotate(tilt, x, eyeCy - s * 0.66f)
                    val brow = RectF(x - s * 0.42f, eyeCy - s * 0.84f, x + s * 0.42f, eyeCy - s * 0.32f)
                    drawEyePath(x) { it.addArc(brow, 200f, 140f) }
                    canvas.restore()
                }
            }

            // Row 1, Col 3: (\▀ ■) — winking asymmetric smirk + big rounded square right eye
            FaceId.SLY -> {
                eyePaint.style = Paint.Style.FILL
                drawEyePath(ex1) {
                    val leftWedge = RectF(ex1 - s * 0.32f, eyeCy - s * 0.14f, ex1 + s * 0.42f, eyeCy + s * 0.26f)
                    it.addRoundRect(leftWedge, s * 0.13f, s * 0.13f, Path.Direction.CW)
                }
                strokeStyle(eyePaint, s * 0.22f)
                drawEyePath(ex1) {
                    it.moveTo(ex1 - s * 0.62f, eyeCy - s * 0.26f)
                    it.lineTo(ex1 + s * 0.48f, eyeCy + s * 0.04f)
                }

                eyePaint.style = Paint.Style.FILL
                drawEyePath(ex2) {
                    val r = RectF(ex2 - s * 0.48f, eyeCy - s * 0.46f, ex2 + s * 0.48f, eyeCy + s * 0.46f)
                    it.addRoundRect(r, s * 0.24f, s * 0.24f, Path.Direction.CW)
                }
            }

            // Row 2, Col 1: (◣ ◢) — half-hooded lower blocks + flared upward-outer angry brows
            FaceId.ANGRY -> {
                for ((x, dir) in listOf(ex1 to -1f, ex2 to 1f)) {
                    eyePaint.style = Paint.Style.FILL
                    val lowerBlock = RectF(x - s * 0.44f, eyeCy - s * 0.05f, x + s * 0.44f, eyeCy + s * 0.48f)
                    drawEyePath(x) { it.addRoundRect(lowerBlock, s * 0.18f, s * 0.18f, Path.Direction.CW) }

                    strokeStyle(eyePaint, s * 0.21f)
                    drawEyePath(x) {
                        it.moveTo(x + dir * s * 0.68f, eyeCy - s * 0.46f)
                        it.lineTo(x + dir * s * 0.18f, eyeCy - s * 0.06f)
                        it.lineTo(x - dir * s * 0.34f, eyeCy - s * 0.02f)
                    }
                }
            }

            // Row 2, Col 2: (-■ ■-) — slanted skeptical brows across uneven eyes
            FaceId.MEH -> {
                eyePaint.style = Paint.Style.FILL
                drawEyePath(ex1) {
                    val lb = RectF(ex1 - s * 0.36f, eyeCy - s * 0.08f, ex1 + s * 0.30f, eyeCy + s * 0.34f)
                    it.addRoundRect(lb, s * 0.12f, s * 0.12f, Path.Direction.CW)
                }
                strokeStyle(eyePaint, s * 0.19f)
                drawEyePath(ex1) {
                    it.moveTo(ex1 - s * 0.64f, eyeCy + s * 0.06f)
                    it.lineTo(ex1 + s * 0.34f, eyeCy - s * 0.16f)
                }

                eyePaint.style = Paint.Style.FILL
                drawEyePath(ex2) {
                    val rb = RectF(ex2 - s * 0.44f, eyeCy - s * 0.24f, ex2 + s * 0.38f, eyeCy + s * 0.42f)
                    it.addRoundRect(rb, s * 0.14f, s * 0.14f, Path.Direction.CW)
                }
                strokeStyle(eyePaint, s * 0.19f)
                drawEyePath(ex2) {
                    it.moveTo(ex2 - s * 0.44f, eyeCy - s * 0.24f)
                    it.lineTo(ex2 + s * 0.66f, eyeCy - s * 0.46f)
                }
            }

            // Row 2, Col 3: (| |) — classic EMO tall rounded vertical pills
            FaceId.NORMAL -> {
                eyePaint.style = Paint.Style.FILL
                for (x in listOf(ex1, ex2)) {
                    val r = RectF(x - s * 0.18f, eyeCy - s * 0.56f, x + s * 0.18f, eyeCy + s * 0.56f)
                    drawEyePath(x) { it.addRoundRect(r, s * 0.14f, s * 0.14f, Path.Direction.CW) }
                }
            }

            // Row 3, Col 1: (◠ ◠) — joyful upward arched curves
            FaceId.SMILE -> {
                strokeStyle(eyePaint, s * 0.24f)
                for (x in listOf(ex1, ex2)) {
                    val r = RectF(x - s * 0.72f, eyeCy - s * 0.28f, x + s * 0.72f, eyeCy + s * 0.92f)
                    drawEyePath(x) { it.addArc(r, 200f, 140f) }
                }
            }

            // Row 3, Col 2: (︶ ︶) — gentle downward peaceful U-arcs
            FaceId.CALM -> {
                strokeStyle(eyePaint, s * 0.24f)
                for (x in listOf(ex1, ex2)) {
                    val r = RectF(x - s * 0.72f, eyeCy - s * 0.62f, x + s * 0.72f, eyeCy + s * 0.44f)
                    drawEyePath(x) { it.addArc(r, 15f, 150f) }
                }
            }

            // Row 3, Col 3: (■ ■) — bold wide rounded square alert eyes
            FaceId.SQUEEZE -> {
                eyePaint.style = Paint.Style.FILL
                for (x in listOf(ex1, ex2)) {
                    val r = RectF(x - s * 0.36f, eyeCy - s * 0.42f, x + s * 0.36f, eyeCy + s * 0.42f)
                    drawEyePath(x) { it.addRoundRect(r, s * 0.20f, s * 0.20f, Path.Direction.CW) }
                }
            }

            // Row 4, Col 1: (✕ ✕) — crisp X eyes
            FaceId.DIZZY -> {
                strokeStyle(eyePaint, s * 0.24f)
                for (x in listOf(ex1, ex2)) {
                    val k = s * 0.46f
                    drawEyePath(x) {
                        it.moveTo(x - k, eyeCy - k); it.lineTo(x + k, eyeCy + k)
                        it.moveTo(x - k, eyeCy + k); it.lineTo(x + k, eyeCy - k)
                    }
                }
            }

            // Row 4, Col 2: (●──●) — circular eyes bridged by a horizontal line
            FaceId.STARE -> {
                val r = s * 0.29f
                eyePaint.style = Paint.Style.FILL
                drawEyePath(ex1) { it.addCircle(ex1, eyeCy, r, Path.Direction.CW) }
                drawEyePath(ex2) { it.addCircle(ex2, eyeCy, r, Path.Direction.CW) }

                strokeStyle(eyePaint, s * 0.14f)
                drawEyePath(eyeCx) {
                    it.moveTo(ex1, eyeCy)
                    it.lineTo(ex2, eyeCy)
                }
            }

            // Row 4, Col 3: (＞ ＜) — crisp energetic chevrons
            FaceId.JOY -> {
                strokeStyle(eyePaint, s * 0.23f)
                drawEyePath(ex1) {
                    it.moveTo(ex1 - s * 0.44f, eyeCy - s * 0.42f)
                    it.lineTo(ex1 + s * 0.42f, eyeCy)
                    it.lineTo(ex1 - s * 0.44f, eyeCy + s * 0.42f)
                }
                drawEyePath(ex2) {
                    it.moveTo(ex2 + s * 0.44f, eyeCy - s * 0.42f)
                    it.lineTo(ex2 - s * 0.42f, eyeCy)
                    it.lineTo(ex2 + s * 0.44f, eyeCy + s * 0.42f)
                }
            }

            // =========================================================================
            // Expressive 20 (Reference 2 — 5x4 Multi-Reaction Sheet)
            // =========================================================================

            // 1. PLEAD: glistening teary oval eyes + soft blush + sad frown
            FaceId.PLEAD -> {
                drawBlush(75)
                strokeStyle(eyePaint, s * 0.15f)
                for ((x, tilt) in listOf(mx1 to -16f, mx2 to 16f)) {
                    drawUpperEye(x) {
                        val r = RectF(x - s * 0.24f, mEyeY - s * 0.28f, x + s * 0.24f, mEyeY + s * 0.28f)
                        it.addOval(r, Path.Direction.CW)
                    }
                    // Teary inner brow flick
                    drawUpperEye(x) {
                        it.moveTo(x - tilt * 0.01f * s, mEyeY - s * 0.46f)
                        it.lineTo(x + (if (tilt < 0) -s * 0.26f else s * 0.26f), mEyeY - s * 0.16f)
                    }
                    tearPaint.style = Paint.Style.FILL
                    tearPaint.alpha = (baseAlpha * 0.85f).toInt().coerceIn(0, 255)
                    canvas.drawCircle(x + s * 0.07f, mEyeY - s * 0.08f, s * 0.09f, tearPaint)
                }
                drawFrownCurve(mouthY)
            }

            // 2. SMUG: half-lidded cool brows + small pupils + smirk smile
            FaceId.SMUG -> {
                for (x in listOf(mx1, mx2)) {
                    strokeStyle(eyePaint, s * 0.16f)
                    drawUpperEye(x) {
                        it.moveTo(x - s * 0.34f, mEyeY - s * 0.06f)
                        it.lineTo(x + s * 0.34f, mEyeY - s * 0.06f)
                    }
                    eyePaint.style = Paint.Style.FILL
                    drawUpperEye(x) {
                        val r = RectF(x - s * 0.18f, mEyeY - s * 0.06f, x + s * 0.18f, mEyeY + s * 0.22f)
                        it.addArc(r, 0f, 180f)
                    }
                }
                drawSmileCurve(mouthY, w = s * 0.32f, h = s * 0.20f)
            }

            // 3. SWIRL: twin spirals + wavy dizzy frown
            FaceId.SWIRL -> {
                strokeStyle(eyePaint, s * 0.13f)
                for ((x, dir) in listOf(mx1 to 1f, mx2 to -1f)) {
                    drawUpperEye(x) {
                        val steps = 28
                        val maxR = s * 0.34f
                        for (i in 0..steps) {
                            val t = i.toFloat() / steps
                            val ang = dir * t * (Math.PI.toFloat() * 4.2f)
                            val rad = t * maxR
                            val px = x + kotlin.math.cos(ang) * rad
                            val py = mEyeY + kotlin.math.sin(ang) * rad
                            if (i == 0) it.moveTo(px, py) else it.lineTo(px, py)
                        }
                    }
                }
                drawFrownCurve(mouthY, w = s * 0.34f, h = s * 0.22f)
            }

            // 4. MISCHIEF: angled mischievous brows + shiny round eyes + :3 cat mouth
            FaceId.MISCHIEF -> {
                drawRoundEye(mx1, mEyeY + s * 0.06f, s * 0.22f)
                drawRoundEye(mx2, mEyeY + s * 0.06f, s * 0.22f)
                strokeStyle(eyePaint, s * 0.15f)
                drawUpperEye(mx1) {
                    it.moveTo(mx1 - s * 0.34f, mEyeY - s * 0.34f)
                    it.lineTo(mx1 + s * 0.18f, mEyeY - s * 0.10f)
                }
                drawUpperEye(mx2) {
                    it.moveTo(mx2 + s * 0.34f, mEyeY - s * 0.34f)
                    it.lineTo(mx2 - s * 0.18f, mEyeY - s * 0.10f)
                }
                drawCatMouth(mouthY)
            }

            // 5. WINK: left happy wink arc + right round eye + smile
            FaceId.WINK -> {
                drawBlush(68)
                strokeStyle(eyePaint, s * 0.16f)
                drawUpperEye(mx1) {
                    val r = RectF(mx1 - s * 0.28f, mEyeY - s * 0.12f, mx1 + s * 0.28f, mEyeY + s * 0.36f)
                    it.addArc(r, 195f, 150f)
                }
                drawRoundEye(mx2, mEyeY, s * 0.22f)
                drawSmileCurve(mouthY)
            }

            // 6. SWEAT: flat tired eyes + cyan sweatdrop on right + frown
            FaceId.SWEAT -> {
                strokeStyle(eyePaint, s * 0.16f)
                for (x in listOf(mx1, mx2)) {
                    drawUpperEye(x) {
                        it.moveTo(x - s * 0.32f, mEyeY)
                        it.lineTo(x + s * 0.32f, mEyeY)
                    }
                }
                // Cyan sweat drop beside right eye
                tearPaint.style = Paint.Style.FILL
                tearPaint.alpha = (baseAlpha * 0.92f).toInt().coerceIn(0, 255)
                val sx = mx2 + s * 0.18f
                val sy = mEyeY + s * 0.26f
                val sr = s * 0.14f
                val dropPath = Path().apply {
                    moveTo(sx, sy - sr * 1.4f)
                    quadTo(sx + sr * 1.1f, sy + sr * 0.3f, sx, sy + sr)
                    quadTo(sx - sr * 1.1f, sy + sr * 0.3f, sx, sy - sr * 1.4f)
                    close()
                }
                canvas.drawPath(dropPath, tearPaint)
                drawFrownCurve(mouthY)
            }

            // 7. FURIOUS: sharp inward brows + coral fiery pupils + `#` anger vein + frown
            FaceId.FURIOUS -> {
                strokeStyle(eyePaint, s * 0.16f)
                drawUpperEye(mx1) {
                    it.moveTo(mx1 - s * 0.38f, mEyeY - s * 0.22f)
                    it.lineTo(mx1 + s * 0.24f, mEyeY + s * 0.04f)
                }
                drawUpperEye(mx2) {
                    it.moveTo(mx2 + s * 0.38f, mEyeY - s * 0.22f)
                    it.lineTo(mx2 - s * 0.24f, mEyeY + s * 0.04f)
                }
                coralPaint.color = 0xFFFF5A76.toInt()
                coralPaint.style = Paint.Style.FILL
                coralPaint.alpha = baseAlpha
                canvas.drawCircle(mx1 + s * 0.02f, mEyeY + s * 0.12f, s * 0.15f, coralPaint)
                canvas.drawCircle(mx2 - s * 0.02f, mEyeY + s * 0.12f, s * 0.15f, coralPaint)

                // `#` anger vein top-right
                coralPaint.style = Paint.Style.STROKE
                coralPaint.strokeWidth = (s * 0.09f).coerceAtLeast(1.8f)
                val ax = mx2 + s * 0.28f
                val ay = mEyeY - s * 0.44f
                val ar = s * 0.14f
                canvas.drawArc(RectF(ax - ar * 1.4f, ay - ar * 1.4f, ax, ay), 15f, 75f, false, coralPaint)
                canvas.drawArc(RectF(ax, ay - ar * 1.4f, ax + ar * 1.4f, ay), 90f, 75f, false, coralPaint)
                canvas.drawArc(RectF(ax, ay, ax + ar * 1.4f, ay + ar * 1.4f), 195f, 75f, false, coralPaint)
                canvas.drawArc(RectF(ax - ar * 1.4f, ay, ax, ay + ar * 1.4f), 285f, 75f, false, coralPaint)

                drawFrownCurve(mouthY)
            }

            // 8. CRY_RIVER: flat T-shut eyes + twin streaming cyan tears + sad frown
            FaceId.CRY_RIVER -> {
                tearPaint.style = Paint.Style.FILL
                tearPaint.alpha = (baseAlpha * 0.85f).toInt().coerceIn(0, 255)
                val tw = s * 0.14f
                for (x in listOf(mx1, mx2)) {
                    canvas.drawRoundRect(
                        RectF(x - tw, mEyeY - s * 0.04f, x + tw, mEyeY + s * 0.56f),
                        tw * 0.5f,
                        tw * 0.5f,
                        tearPaint
                    )
                    strokeStyle(eyePaint, s * 0.16f)
                    drawUpperEye(x) {
                        it.moveTo(x - s * 0.34f, mEyeY - s * 0.04f)
                        it.lineTo(x + s * 0.34f, mEyeY - s * 0.04f)
                    }
                }
                drawFrownCurve(mouthY)
            }

            // 9. HYPED: intense brows + ringed eyes + wide laughing D-mouth
            FaceId.HYPED -> {
                drawBlush(62)
                strokeStyle(eyePaint, s * 0.15f)
                for ((x, dir) in listOf(mx1 to -1f, mx2 to 1f)) {
                    drawUpperEye(x) {
                        it.addCircle(x, mEyeY + s * 0.06f, s * 0.25f, Path.Direction.CW)
                    }
                    drawUpperEye(x) {
                        it.moveTo(x + dir * s * 0.34f, mEyeY - s * 0.36f)
                        it.lineTo(x - dir * s * 0.24f, mEyeY - s * 0.18f)
                    }
                }
                drawOpenSmileMouth(mouthY, w = s * 0.40f, h = s * 0.34f)
            }

            // 10. HMPH: angled pout brows + round eyes + `3` pout mouth
            FaceId.HMPH -> {
                drawRoundEye(mx1, mEyeY + s * 0.04f, s * 0.21f)
                drawRoundEye(mx2, mEyeY + s * 0.04f, s * 0.21f)
                strokeStyle(eyePaint, s * 0.15f)
                drawUpperEye(mx1) {
                    it.moveTo(mx1 - s * 0.34f, mEyeY - s * 0.36f)
                    it.lineTo(mx1 + s * 0.20f, mEyeY - s * 0.14f)
                }
                drawUpperEye(mx2) {
                    it.moveTo(mx2 + s * 0.34f, mEyeY - s * 0.36f)
                    it.lineTo(mx2 - s * 0.20f, mEyeY - s * 0.14f)
                }
                drawKiss3Mouth(mouthY)
            }

            // 11. GRUMPY: wide round eyes + asymmetric crooked pout
            FaceId.GRUMPY -> {
                drawRoundEye(mx1, mEyeY, s * 0.22f)
                drawRoundEye(mx2, mEyeY, s * 0.22f)
                strokeStyle(eyePaint, s * 0.14f)
                drawMouth {
                    it.moveTo(eyeCx - s * 0.34f, mouthY + s * 0.08f)
                    it.quadTo(eyeCx + s * 0.06f, mouthY - s * 0.22f, eyeCx + s * 0.34f, mouthY + s * 0.04f)
                    it.moveTo(eyeCx + s * 0.24f, mouthY - s * 0.08f)
                    it.lineTo(eyeCx + s * 0.42f, mouthY - s * 0.18f)
                }
            }

            // 12. UWU: `> <` chevron eyes + `:3` cat mouth + blush
            FaceId.UWU -> {
                drawBlush(78)
                drawChevron(mx1, mEyeY, pointRight = true)
                drawChevron(mx2, mEyeY, pointRight = false)
                drawCatMouth(mouthY)
            }

            // 13. GIGGLE: `> <` chevron eyes + wide open laughing D-mouth
            FaceId.GIGGLE -> {
                drawBlush(72)
                drawChevron(mx1, mEyeY, pointRight = true)
                drawChevron(mx2, mEyeY, pointRight = false)
                drawOpenSmileMouth(mouthY)
            }

            // 14. SHOCKED: wide open O-ring eyes + flat stunned mouth
            FaceId.SHOCKED -> {
                strokeStyle(eyePaint, s * 0.16f)
                for (x in listOf(mx1, mx2)) {
                    drawUpperEye(x) {
                        it.addCircle(x, mEyeY, s * 0.27f, Path.Direction.CW)
                    }
                }
                drawMouth {
                    it.moveTo(eyeCx - s * 0.32f, mouthY + s * 0.04f)
                    it.lineTo(eyeCx + s * 0.32f, mouthY + s * 0.04f)
                }
            }

            // 15. NERVOUS: round eyes + jagged zigzag mouth
            FaceId.NERVOUS -> {
                drawRoundEye(mx1, mEyeY, s * 0.21f)
                drawRoundEye(mx2, mEyeY, s * 0.21f)
                strokeStyle(eyePaint, s * 0.13f)
                val zw = s * 0.44f
                val zh = s * 0.11f
                drawMouth {
                    it.moveTo(eyeCx - zw, mouthY)
                    it.lineTo(eyeCx - zw * 0.6f, mouthY + zh)
                    it.lineTo(eyeCx - zw * 0.2f, mouthY - zh)
                    it.lineTo(eyeCx + zw * 0.2f, mouthY + zh)
                    it.lineTo(eyeCx + zw * 0.6f, mouthY - zh)
                    it.lineTo(eyeCx + zw, mouthY)
                }
            }

            // 16. KISS_SHY: `> <` chevron eyes + `3` kiss mouth + blush
            FaceId.KISS_SHY -> {
                drawBlush(85)
                drawChevron(mx1, mEyeY, pointRight = true)
                drawChevron(mx2, mEyeY, pointRight = false)
                drawKiss3Mouth(mouthY)
            }

            // 17. SHOUT: fierce angled brows + round eyes + wide coral upset mouth
            FaceId.SHOUT -> {
                drawRoundEye(mx1, mEyeY + s * 0.06f, s * 0.22f)
                drawRoundEye(mx2, mEyeY + s * 0.06f, s * 0.22f)
                strokeStyle(eyePaint, s * 0.15f)
                drawUpperEye(mx1) {
                    it.moveTo(mx1 - s * 0.32f, mEyeY - s * 0.34f)
                    it.lineTo(mx1 + s * 0.18f, mEyeY - s * 0.14f)
                }
                drawUpperEye(mx2) {
                    it.moveTo(mx2 + s * 0.32f, mEyeY - s * 0.34f)
                    it.lineTo(mx2 - s * 0.18f, mEyeY - s * 0.14f)
                }
                drawOpenUpsetMouth(mouthY, w = s * 0.36f, h = s * 0.30f)
            }

            // 18. WINK_KISS: `>` wink eye + round eye + `3` kiss mouth + blush
            FaceId.WINK_KISS -> {
                drawBlush(80)
                drawChevron(mx1, mEyeY, pointRight = true)
                drawRoundEye(mx2, mEyeY, s * 0.22f)
                drawKiss3Mouth(mouthY)
            }

            // 19. BORED: half-lidded flat heavy eyes + small open exasperated mouth
            FaceId.BORED -> {
                for (x in listOf(mx1, mx2)) {
                    strokeStyle(eyePaint, s * 0.16f)
                    drawUpperEye(x) {
                        it.moveTo(x - s * 0.34f, mEyeY - s * 0.04f)
                        it.lineTo(x + s * 0.34f, mEyeY - s * 0.04f)
                    }
                    eyePaint.style = Paint.Style.FILL
                    drawUpperEye(x) {
                        val r = RectF(x - s * 0.16f, mEyeY - s * 0.04f, x + s * 0.16f, mEyeY + s * 0.18f)
                        it.addArc(r, 0f, 180f)
                    }
                }
                drawOpenUpsetMouth(mouthY, w = s * 0.30f, h = s * 0.24f)
            }

            // 20. BEAMING: `^ ^` cheerful arched eyes + wide open happy mouth + blush
            FaceId.BEAMING -> {
                drawBlush(80)
                strokeStyle(eyePaint, s * 0.16f)
                for (x in listOf(mx1, mx2)) {
                    drawUpperEye(x) {
                        val r = RectF(x - s * 0.26f, mEyeY - s * 0.16f, x + s * 0.26f, mEyeY + s * 0.34f)
                        it.addArc(r, 200f, 140f)
                    }
                }
                drawOpenSmileMouth(mouthY, w = s * 0.40f, h = s * 0.36f)
            }
        }

        eyePaint.color = baseColor
        eyePaint.alpha = baseAlpha
        eyePaint.style = Paint.Style.FILL
    }

    private fun strokeStyle(p: Paint, width: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = width.coerceAtLeast(2f)
    }
}
