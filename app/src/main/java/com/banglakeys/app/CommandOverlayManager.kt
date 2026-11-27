package com.banglakeys.app

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.banglakeys.app.robot.BlinkAnimator
import com.banglakeys.app.robot.EmoBrainHub
import com.banglakeys.app.robot.FaceId
import com.banglakeys.app.robot.FaceRenderer
import kotlin.math.hypot

/**
 * স্ক্রিনের বাম বা ডান পাশে ভাসমান Draggable Side-Edge Assistant Overlay।
 *
 * - Idle / passive Continuous standby অবস্থায় স্ক্রিনের কিনারায় ছোট slim tab হিসেবে থাকে,
 *   ফলে উপরের হেডার, সার্চ বার বা গেম/ভিডিওর কোনো বাটন ঢাকে না।
 * - যেকোনো দিকে টেনে বাম বা ডান কিনারায় ও পছন্দমতো উচ্চতায় সেট করা যায় (মনে রাখে)।
 * - কমান্ড শুনলে, confirm চাইলে, flow চললে বা ফলাফল দেখাতে স্বয়ংক্রিয়ভাবে পাশ থেকে বড় কার্ড খোলে
 *   এবং কাজ শেষে আবার ছোট side tab-এ ফিরে যায়।
 */
class CommandOverlayManager(
    private val ctx: Context,
    private val onConfirmClick: () -> Unit,
    private val onCancelClick: () -> Unit,
    private val onStopFlowClick: () -> Unit,
    private val onPttTriggerClick: () -> Unit,
    private val onDismissOrSleepClick: () -> Unit
) {

    sealed class OverlayState {
        data class Dormant(val isBangla: Boolean) : OverlayState()
        data class Listening(
            val partialText: String,
            val awakeSec: Int?,
            val isPtt: Boolean
        ) : OverlayState()
        data class AwaitingConfirm(
            val heardText: String,
            val actionSummary: String,
            val question: String
        ) : OverlayState()
        data class RunningFlow(
            val appLabel: String,
            val stepStatus: String
        ) : OverlayState()
        data class ResultFeedback(
            val heardText: String?,
            val commandSummary: String?,
            val message: String,
            val isSuccess: Boolean,
            val canRetry: Boolean
        ) : OverlayState()
    }

    private data class HudPalette(
        val surface: Int,
        val surfaceAlt: Int,
        val border: Int,
        val text: Int,
        val subText: Int,
        val primary: Int,
        val primarySoft: Int,
        val ok: Int,
        val okSoft: Int,
        val warn: Int,
        val warnSoft: Int,
        val danger: Int,
        val dangerSoft: Int
    )

    private val wm: WindowManager? =
        ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var rootContainer: LinearLayout? = null
    private var currentLp: WindowManager.LayoutParams? = null
    private var attached = false
    private var currentState: OverlayState? = null
    private var userExpanded = false
    private var autoResetRunnable: Runnable? = null

    fun isOverlayPermittedAndEnabled(): Boolean =
        AssistantPrefs.overlayHudEnabled(ctx) && AssistantSetup.canOverlay(ctx)

    fun currentOverlayState(): OverlayState? = currentState

    fun isExpanded(): Boolean = userExpanded

    fun isCompactTab(state: OverlayState): Boolean {
        if (userExpanded) return false
        return when (state) {
            is OverlayState.Dormant -> true
            is OverlayState.Listening -> !state.isPtt && state.awakeSec == null && state.partialText.isBlank()
            else -> false
        }
    }

    fun render(state: OverlayState) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { render(state) }
            return
        }
        cancelAutoReset()
        if (state is OverlayState.Dormant || state is OverlayState.ResultFeedback) {
            userExpanded = false
        }
        currentState = state

        if (!isOverlayPermittedAndEnabled()) {
            detach()
            return
        }

        ensureAttached(state)
        val container = rootContainer ?: return
        bindState(container, state)
        syncWindowPosition(state)
    }

    fun renderResultWithAutoReset(
        state: OverlayState.ResultFeedback,
        resetAfterMs: Long = 2600L,
        nextStateSupplier: () -> OverlayState?
    ) {
        render(state)
        if (resetAfterMs <= 0L) return
        val r = Runnable {
            userExpanded = false
            val next = nextStateSupplier()
            if (next != null) render(next) else hide()
        }
        autoResetRunnable = r
        handler.postDelayed(r, resetAfterMs)
    }

    fun refreshPosition() {
        val state = currentState ?: return
        if (attached) syncWindowPosition(state)
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { hide() }
            return
        }
        cancelAutoReset()
        userExpanded = false
        currentState = null
        detach()
    }

    /** Robolectric বা UI preview-র জন্য সরাসরি root View তৈরি করে দেয়। */
    fun buildViewForState(state: OverlayState): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        bindState(box, state)
        return box
    }

    private fun cancelAutoReset() {
        autoResetRunnable?.let { handler.removeCallbacks(it) }
        autoResetRunnable = null
    }

    private fun screenW(): Int = ctx.resources.displayMetrics.widthPixels.coerceAtLeast(dp(240))
    private fun screenH(): Int = ctx.resources.displayMetrics.heightPixels.coerceAtLeast(dp(360))

    private fun verticalBounds(): Pair<Int, Int> {
        val minY = statusBarTopOffset() + dp(12)
        val maxY = (screenH() - dp(150)).coerceAtLeast(minY + dp(40))
        return minY to maxY
    }

    private fun expandedCardWidth(): Int =
        minOf(screenW() - dp(28), dp(296)).coerceAtLeast(dp(210))

    private fun computeYForRatio(ratio: Float): Int {
        val (minY, maxY) = verticalBounds()
        return (minY + (maxY - minY) * ratio.coerceIn(
            AssistantPrefs.MIN_OVERLAY_Y_RATIO,
            AssistantPrefs.MAX_OVERLAY_Y_RATIO
        )).toInt().coerceIn(minY, maxY)
    }

    private fun ensureAttached(state: OverlayState) {
        val manager = wm ?: return
        if (attached && rootContainer != null && currentLp != null) return

        val container = rootContainer ?: LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootContainer = container

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        populateDockParams(lp, state)
        currentLp = lp

        try {
            manager.addView(container, lp)
            attached = true
        } catch (e: Exception) {
            AssistantLog.w("CommandOverlayManager addView failed", e)
            attached = false
        }
    }

    private fun populateDockParams(lp: WindowManager.LayoutParams, state: OverlayState) {
        val edge = AssistantPrefs.overlayEdge(ctx)
        val compact = isCompactTab(state)
        lp.width = if (compact) WindowManager.LayoutParams.WRAP_CONTENT else expandedCardWidth()
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        lp.gravity = Gravity.TOP or if (edge == OverlayEdge.LEFT) Gravity.LEFT else Gravity.RIGHT
        lp.x = if (compact) 0 else dp(10)
        lp.y = computeYForRatio(AssistantPrefs.overlayYRatio(ctx))
    }

    private fun syncWindowPosition(state: OverlayState) {
        val manager = wm ?: return
        val container = rootContainer ?: return
        val lp = currentLp ?: return
        if (!attached) return
        populateDockParams(lp, state)
        try {
            manager.updateViewLayout(container, lp)
        } catch (e: Exception) {
            AssistantLog.w("CommandOverlayManager updateViewLayout failed", e)
        }
    }

    private fun detach() {
        val manager = wm
        val container = rootContainer
        if (attached && manager != null && container != null) {
            try {
                manager.removeViewImmediate(container)
            } catch (_: Exception) {
            }
        }
        attached = false
        rootContainer = null
        currentLp = null
    }

    private fun statusBarTopOffset(): Int {
        val resId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) ctx.resources.getDimensionPixelSize(resId) else dp(24)
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun palette(): HudPalette = HudPalette(
        surface = 0xF5080C12.toInt(),
        surfaceAlt = 0xFF101824.toInt(),
        border = 0x5536D1FF,
        text = 0xFFF1F8FF.toInt(),
        subText = 0xFF8FB4CE.toInt(),
        primary = 0xFF36D1FF.toInt(),
        primarySoft = 0xFF0E2938.toInt(),
        ok = 0xFF22E58A.toInt(),
        okSoft = 0xFF0D2E20.toInt(),
        warn = 0xFFFFC857.toInt(),
        warnSoft = 0xFF35280D.toInt(),
        danger = 0xFFFF5A6E.toInt(),
        dangerSoft = 0xFF351218.toInt()
    )

    private fun pillBg(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun edgeTabBg(fill: Int, stroke: Int, edge: OverlayEdge): GradientDrawable {
        val r = dp(18).toFloat()
        val flat = dp(2).toFloat()
        val radii = if (edge == OverlayEdge.RIGHT) {
            floatArrayOf(r, r, flat, flat, flat, flat, r, r)
        } else {
            floatArrayOf(flat, flat, r, r, r, r, flat, flat)
        }
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = radii
            setColor(fill)
            setStroke(dp(1), stroke)
        }
    }

    private fun rippleView(view: View, fill: Int, radiusDp: Int, stroke: Int? = null) {
        val base = pillBg(fill, radiusDp, stroke)
        view.background = RippleDrawable(ColorStateList.valueOf(0x3336D1FF), base, null)
        view.isClickable = true
        view.isFocusable = false
    }

    private fun makeVisor(
        face: FaceId,
        accent: Int,
        sleeping: Boolean = false,
        wDp: Int = 46,
        hDp: Int = 32,
        marginEndDp: Int = 8,
        followBrain: Boolean = false,
    ): EmoHudFaceView = EmoHudFaceView(ctx, face, accent, sleeping, followBrain).apply {
        layoutParams = LinearLayout.LayoutParams(dp(wDp), dp(hDp)).apply {
            marginEnd = dp(marginEndDp)
        }
    }

    private class EmoHudFaceView(
        context: Context,
        private val face: FaceId,
        accentColor: Int,
        private val sleeping: Boolean = false,
        private val followBrain: Boolean = false,
    ) : View(context) {
        private val blink = BlinkAnimator()
        private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF060A10.toInt()
        }
        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentColor and 0x00FFFFFF) or (0x77 shl 24)
            style = Paint.Style.STROKE
            strokeWidth = (1.3f * context.resources.displayMetrics.density).coerceAtLeast(1f)
        }
        private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            val w = width.toFloat()
            val h = height.toFloat()
            val activeFace = if (followBrain) EmoBrainHub.brain.mood else face
            val isSleep = if (followBrain) EmoBrainHub.brain.sleeping else sleeping
            FaceRenderer.draw(
                canvas = canvas,
                face = activeFace,
                cx = w / 2f,
                cy = h / 2f,
                plateW = w * 0.94f,
                plateH = h * 0.92f,
                eyeGap = w * 0.41f,
                eyeSize = h * 0.34f,
                blink = if (isSleep) 0.78f else blink.tick(),
                eyePaint = eyePaint,
                platePaint = platePaint,
                plateOutline = outlinePaint,
                glow = true,
            )
            if (isAttachedToWindow) {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun bindState(root: LinearLayout, state: OverlayState) {
        root.removeAllViews()
        val p = palette()

        if (isCompactTab(state)) {
            bindEdgeTab(root, p, state)
            return
        }

        val borderColor = when (state) {
            is OverlayState.AwaitingConfirm -> p.warn
            is OverlayState.RunningFlow -> p.primary
            is OverlayState.ResultFeedback -> if (state.isSuccess) p.ok else p.danger
            is OverlayState.Listening -> p.primary
            is OverlayState.Dormant -> p.border
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = pillBg(p.surface, 20, borderColor)
            elevation = dp(10).toFloat()
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        when (state) {
            is OverlayState.Dormant -> bindDormantExpanded(card, p, state)
            is OverlayState.Listening -> bindListening(card, p, state)
            is OverlayState.AwaitingConfirm -> bindConfirm(card, p, state)
            is OverlayState.RunningFlow -> bindRunning(card, p, state)
            is OverlayState.ResultFeedback -> bindFeedback(card, p, state)
        }

        attachDragSupport(card, onTap = null)
        root.addView(card)
    }

    /**
     * Idle / standby অবস্থায় স্ক্রিনের কিনারায় থাকা ছোট slim edge tab।
     * ট্যাপ করলে কমান্ড বা প্যানেল খোলে; টেনে বাম/ডান কিনারায় যেকোনো উচ্চতায় সরানো যায়।
     */
    private fun bindEdgeTab(root: LinearLayout, p: HudPalette, state: OverlayState) {
        val edge = AssistantPrefs.overlayEdge(ctx)
        val isListeningStandby = state is OverlayState.Listening
        val dotColor = if (isListeningStandby) p.ok else p.primary
        val stroke = if (isListeningStandby) p.primary else p.border
        val edgeFace = if (isListeningStandby) FaceId.NORMAL else EmoBrainHub.brain.mood

        val tab = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = dp(44)
            minimumHeight = dp(64)
            setPadding(dp(6), dp(8), dp(6), dp(8))
            background = edgeTabBg(p.surface, stroke, edge)
            elevation = dp(8).toFloat()
            contentDescription = if (isListeningStandby) "EMO listening tab" else "Tap to talk to EMO"
        }

        // ছোট ২-ডট গ্রিপ যা বোঝায় এটি ড্র্যাগ করা যাবে
        val gripRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        repeat(2) { idx ->
            gripRow.addView(View(ctx).apply {
                background = pillBg(p.border, 99)
                layoutParams = LinearLayout.LayoutParams(dp(4), dp(4)).apply {
                    if (idx == 0) marginEnd = dp(3)
                }
            })
        }
        tab.addView(gripRow)

        tab.addView(
            makeVisor(
                face = edgeFace,
                accent = p.primary,
                sleeping = !isListeningStandby && EmoBrainHub.brain.sleeping,
                wDp = 36,
                hDp = 24,
                marginEndDp = 0,
                followBrain = !isListeningStandby,
            )
        )

        // লাইভ স্ট্যাটাস ডট
        tab.addView(View(ctx).apply {
            background = pillBg(dotColor, 99)
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { topMargin = dp(4) }
        })

        attachDragSupport(tab) {
            if (state is OverlayState.Dormant) {
                onPttTriggerClick()
            } else {
                userExpanded = true
                render(state)
            }
        }
        root.addView(tab)
    }

    private fun collapseToEdgeTab() {
        val state = currentState ?: return
        userExpanded = false
        if (state is OverlayState.Listening && (state.isPtt || state.awakeSec != null)) {
            onDismissOrSleepClick()
        } else {
            render(state)
        }
    }

    private fun attachDragSupport(view: View, onTap: (() -> Unit)?) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slopPx = dp(8).toFloat()

        view.setOnTouchListener { v, ev ->
            val manager = wm
            val container = rootContainer
            val lp = currentLp
            if (manager == null || container == null || lp == null || !attached) {
                if (ev.actionMasked == MotionEvent.ACTION_UP && onTap != null) {
                    onTap()
                    return@setOnTouchListener true
                }
                return@setOnTouchListener false
            }

            val sw = screenW()
            val (minY, maxY) = verticalBounds()
            val cw = container.width.coerceAtLeast(v.width).coerceAtLeast(dp(40))

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    val currentEdge = AssistantPrefs.overlayEdge(ctx)
                    startX = if ((lp.gravity and Gravity.RIGHT) == Gravity.RIGHT) {
                        (sw - cw - lp.x).coerceIn(0, (sw - cw).coerceAtLeast(0))
                    } else if ((lp.gravity and Gravity.LEFT) == Gravity.LEFT) {
                        lp.x.coerceIn(0, (sw - cw).coerceAtLeast(0))
                    } else {
                        if (currentEdge == OverlayEdge.RIGHT) (sw - cw) else 0
                    }
                    startY = lp.y.coerceIn(minY, maxY)
                    dragging = false
                    return@setOnTouchListener onTap != null
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > slopPx) {
                        dragging = true
                        lp.gravity = Gravity.TOP or Gravity.LEFT
                    }
                    if (dragging) {
                        lp.x = (startX + dx.toInt()).coerceIn(0, (sw - cw).coerceAtLeast(0))
                        lp.y = (startY + dy.toInt()).coerceIn(minY, maxY)
                        try {
                            manager.updateViewLayout(container, lp)
                        } catch (_: Exception) {
                        }
                        return@setOnTouchListener true
                    }
                    return@setOnTouchListener false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val centerX = lp.x + cw / 2
                        val newEdge = if (centerX < sw / 2) OverlayEdge.LEFT else OverlayEdge.RIGHT
                        val denom = (maxY - minY).coerceAtLeast(1).toFloat()
                        val newRatio = ((lp.y - minY) / denom).coerceIn(
                            AssistantPrefs.MIN_OVERLAY_Y_RATIO,
                            AssistantPrefs.MAX_OVERLAY_Y_RATIO
                        )
                        AssistantPrefs.setOverlayEdge(ctx, newEdge)
                        AssistantPrefs.setOverlayYRatio(ctx, newRatio)
                        currentState?.let {
                            bindState(container, it)
                            syncWindowPosition(it)
                        }
                        dragging = false
                        return@setOnTouchListener true
                    } else if (ev.actionMasked == MotionEvent.ACTION_UP && onTap != null) {
                        onTap()
                        return@setOnTouchListener true
                    }
                    return@setOnTouchListener false
                }
            }
            false
        }
    }

    private fun bindDormantExpanded(card: LinearLayout, p: HudPalette, state: OverlayState.Dormant) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
        }

        val tapArea = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            rippleView(this, p.surfaceAlt, 14)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnClickListener { onPttTriggerClick() }
        }

        tapArea.addView(
            makeVisor(
                face = EmoBrainHub.brain.mood,
                accent = p.primary,
                sleeping = EmoBrainHub.brain.sleeping,
                wDp = 44,
                hDp = 30,
                marginEndDp = 8,
            )
        )
        tapArea.addView(TextView(ctx).apply {
            text = if (state.isBangla) "EMO-কে বলতে ট্যাপ করুন" else "Tap to talk to EMO"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.text)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(tapArea)

        row.addView(buildMiniIconButton(AssistantIcons.Kind.MINIMIZE, p.subText, p.surfaceAlt, "Minimize to side edge") {
            collapseToEdgeTab()
        })
        card.addView(row)
    }

    private fun bindListening(card: LinearLayout, p: HudPalette, state: OverlayState.Listening) {
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val listenFace = if (state.isPtt || state.awakeSec != null || state.partialText.isNotBlank()) {
            FaceId.CURIOUS
        } else {
            FaceId.NORMAL
        }
        topRow.addView(makeVisor(face = listenFace, accent = p.primary, wDp = 46, hDp = 32, marginEndDp = 8))

        val statusLabel = when {
            state.isPtt -> "EMO শুনছে — বলুন"
            state.awakeSec != null && state.awakeSec > 0 -> "EMO সক্রিয় (${state.awakeSec}s) — বলুন"
            else -> "\"Hey EMO\" বলে কমান্ড দিন"
        }
        topRow.addView(TextView(ctx).apply {
            text = statusLabel
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.primary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        if (!state.isPtt && state.awakeSec == null && state.partialText.isBlank()) {
            topRow.addView(
                buildMiniIconButton(AssistantIcons.Kind.MIC, p.primary, p.primarySoft, "Speak command now") {
                    onPttTriggerClick()
                }.apply {
                    (layoutParams as? LinearLayout.LayoutParams)?.marginEnd = dp(4)
                }
            )
        }

        topRow.addView(
            buildMiniIconButton(AssistantIcons.Kind.MINIMIZE, p.subText, p.surfaceAlt, "Minimize to side edge") {
                collapseToEdgeTab()
            }
        )
        card.addView(topRow)

        if (state.partialText.isNotBlank()) {
            card.addView(TextView(ctx).apply {
                text = "“${state.partialText}”"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.text)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.START
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = pillBg(p.surfaceAlt, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            })
        }
    }

    private fun buildMiniIconButton(
        kind: AssistantIcons.Kind,
        tint: Int,
        bg: Int,
        cd: String,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(ctx).apply {
        gravity = Gravity.CENTER
        minimumWidth = dp(38)
        minimumHeight = dp(38)
        setPadding(dp(6), dp(6), dp(6), dp(6))
        contentDescription = cd
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rippleView(this, bg, 12)
        setOnClickListener { onClick() }
        addView(ImageView(ctx).apply {
            setImageDrawable(AssistantIcons.drawable(ctx, kind, tint, 15))
        })
    }

    private fun bindConfirm(card: LinearLayout, p: HudPalette, state: OverlayState.AwaitingConfirm) {
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(makeVisor(face = FaceId.STARE, accent = p.warn, wDp = 44, hDp = 30, marginEndDp = 8))
        header.addView(TextView(ctx).apply {
            text = "EMO নিশ্চিত করছে"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.warn)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(header)

        if (state.heardText.isNotBlank()) {
            card.addView(TextView(ctx).apply {
                text = "শোনা গেছে: “${state.heardText}”"
                textSize = 12f
                setTextColor(p.subText)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
        }

        card.addView(TextView(ctx).apply {
            text = state.actionSummary
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.text)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = pillBg(p.warnSoft, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        })

        card.addView(TextView(ctx).apply {
            text = state.question
            textSize = 12.5f
            setTextColor(p.text)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        })

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val cancelBtn = buildActionButton(
            kind = AssistantIcons.Kind.CLOSE,
            label = "বাতিল",
            fg = p.danger,
            bg = p.dangerSoft
        ) { onCancelClick() }
        btnRow.addView(cancelBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        val confirmBtn = buildActionButton(
            kind = AssistantIcons.Kind.CHECK_CIRCLE,
            label = "হ্যাঁ, চালান",
            fg = 0xFF041A11.toInt(),
            bg = p.ok
        ) { onConfirmClick() }
        btnRow.addView(confirmBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })

        card.addView(btnRow)
    }

    private fun bindRunning(card: LinearLayout, p: HudPalette, state: OverlayState.RunningFlow) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(makeVisor(face = FaceId.SQUEEZE, accent = p.primary, wDp = 44, hDp = 30, marginEndDp = 8))
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            addView(TextView(ctx).apply {
                text = state.appLabel
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.primary)
            })
            addView(TextView(ctx).apply {
                text = state.stepStatus
                textSize = 12.5f
                setTextColor(p.text)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        row.addView(textCol)

        val stopBtn = buildActionButton(
            kind = AssistantIcons.Kind.STOP,
            label = "Stop",
            fg = p.danger,
            bg = p.dangerSoft
        ) { onStopFlowClick() }
        row.addView(stopBtn)
        card.addView(row)
    }

    private fun bindFeedback(card: LinearLayout, p: HudPalette, state: OverlayState.ResultFeedback) {
        val accent = if (state.isSuccess) p.ok else p.danger
        val soft = if (state.isSuccess) p.okSoft else p.dangerSoft
        val face = if (state.isSuccess) FaceId.JOY else FaceId.MEH

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(makeVisor(face = face, accent = accent, wDp = 44, hDp = 30, marginEndDp = 10))

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        if (!state.heardText.isNullOrBlank()) {
            col.addView(TextView(ctx).apply {
                text = "“${state.heardText}”"
                textSize = 12f
                setTextColor(p.subText)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        if (!state.commandSummary.isNullOrBlank()) {
            col.addView(TextView(ctx).apply {
                text = state.commandSummary
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.text)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        col.addView(TextView(ctx).apply {
            text = cleanFeedbackMessage(state.message)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accent)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(col)

        if (state.canRetry) {
            val retryBtn = buildActionButton(
                kind = AssistantIcons.Kind.REFRESH,
                label = "আবার",
                fg = p.primary,
                bg = soft
            ) { onPttTriggerClick() }
            row.addView(retryBtn)
        }
        card.addView(row)
    }

    private fun buildActionButton(
        kind: AssistantIcons.Kind,
        label: String,
        fg: Int,
        bg: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            rippleView(this, bg, 14)
            setOnClickListener { onClick() }
            addView(ImageView(ctx).apply {
                setImageDrawable(AssistantIcons.drawable(ctx, kind, fg, 16))
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(6) }
            })
            addView(TextView(ctx).apply {
                text = label
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(fg)
            })
        }
    }

    private fun cleanFeedbackMessage(raw: String): String =
        raw.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF⚠️✅❌⏱⏳⌛▶️⬆️⬇️⬅️➡️❤️👍⏯🔊🔉📞📱👆⌨️↵◀️🏠🗂🔔⚙️🔒📸😴🤖🛑📁📄📷🗺️🔍🎵📝✉️💬]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
