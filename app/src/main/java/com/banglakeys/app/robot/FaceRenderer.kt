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

        fun drawEyePath(anchorX: Float, block: (Path) -> Unit) {
            val p = Path()
            block(p)
            canvas.save()
            val squashY = ((1f - blink).coerceAtLeast(0.045f)) * scaleY
            canvas.scale(scaleX, squashY, anchorX, eyeCy)

            if (glow && baseAlpha > 20) {
                val isStroked = eyePaint.style == Paint.Style.STROKE
                val coreStroke = if (isStroked) eyePaint.strokeWidth else 0f

                glowPaint.color = baseColor
                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeCap = Paint.Cap.ROUND
                glowPaint.strokeJoin = Paint.Join.ROUND

                // Outer halo
                glowPaint.alpha = (baseAlpha * 0.16f).toInt().coerceIn(8, 255)
                glowPaint.strokeWidth = (coreStroke + s * 0.44f).coerceAtLeast(4f)
                canvas.drawPath(p, glowPaint)

                // Mid halo
                glowPaint.alpha = (baseAlpha * 0.36f).toInt().coerceIn(14, 255)
                glowPaint.strokeWidth = (coreStroke + s * 0.19f).coerceAtLeast(2.5f)
                canvas.drawPath(p, glowPaint)
            }

            canvas.drawPath(p, eyePaint)
            canvas.restore()
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
                // Left winking angled wedge + brow slant
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

                // Right bold rounded square eye
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
                // Left smaller hooded wedge
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

                // Right taller block + upward outer brow
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
                // Left '>'
                drawEyePath(ex1) {
                    it.moveTo(ex1 - s * 0.44f, eyeCy - s * 0.42f)
                    it.lineTo(ex1 + s * 0.42f, eyeCy)
                    it.lineTo(ex1 - s * 0.44f, eyeCy + s * 0.42f)
                }
                // Right '<'
                drawEyePath(ex2) {
                    it.moveTo(ex2 + s * 0.44f, eyeCy - s * 0.42f)
                    it.lineTo(ex2 - s * 0.42f, eyeCy)
                    it.lineTo(ex2 + s * 0.44f, eyeCy + s * 0.42f)
                }
            }
        }
    }

    private fun strokeStyle(p: Paint, width: Float) {
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = width.coerceAtLeast(2f)
    }
}
