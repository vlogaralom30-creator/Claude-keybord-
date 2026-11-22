package com.banglakeys.app

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * স্ক্রিনের উপরের দিকে ভাসমান Compact Floating Command Pill Overlay।
 *
 * - লাইভ যা শোনা যাচ্ছে (partial speech) সাথে সাথে দেখায় যাতে কোনো ভুল না হয়।
 * - Call / Message সেন্ডের আগে inline Confirm ও Cancel বাটন দেখায়।
 * - Push-to-Talk মোডে "Tap to Speak" পিল হিসেবে থাকে (ট্যাপ করলেই শোনা শুরু হয়)।
 * - Display over other apps না থাকলে বা বন্ধ থাকলে নিরাপদে skip করে।
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
    private var attached = false
    private var currentState: OverlayState? = null
    private var autoResetRunnable: Runnable? = null

    fun isOverlayPermittedAndEnabled(): Boolean =
        AssistantPrefs.overlayHudEnabled(ctx) && AssistantSetup.canOverlay(ctx)

    fun currentOverlayState(): OverlayState? = currentState

    fun render(state: OverlayState) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { render(state) }
            return
        }
        cancelAutoReset()
        currentState = state

        if (!isOverlayPermittedAndEnabled()) {
            detach()
            return
        }

        ensureAttached()
        val container = rootContainer ?: return
        bindState(container, state)
    }

    fun renderResultWithAutoReset(
        state: OverlayState.ResultFeedback,
        resetAfterMs: Long = 2600L,
        nextStateSupplier: () -> OverlayState?
    ) {
        render(state)
        if (resetAfterMs <= 0L) return
        val r = Runnable {
            val next = nextStateSupplier()
            if (next != null) render(next) else hide()
        }
        autoResetRunnable = r
        handler.postDelayed(r, resetAfterMs)
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { hide() }
            return
        }
        cancelAutoReset()
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

    private fun ensureAttached() {
        val manager = wm ?: return
        if (attached && rootContainer != null) return

        val container = rootContainer ?: LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootContainer = container

        val maxW = minOf(
            ctx.resources.displayMetrics.widthPixels - dp(24),
            dp(380)
        ).coerceAtLeast(dp(220))

        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            maxW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarTopOffset() + dp(8)
        }

        try {
            manager.addView(container, lp)
            attached = true
        } catch (e: Exception) {
            AssistantLog.w("CommandOverlayManager addView failed", e)
            attached = false
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
    }

    private fun statusBarTopOffset(): Int {
        val resId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) ctx.resources.getDimensionPixelSize(resId) else dp(24)
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun palette(): HudPalette {
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) {
            HudPalette(
                surface = 0xF21A2020.toInt(),
                surfaceAlt = 0xFF232C2B.toInt(),
                border = 0xFF334340.toInt(),
                text = 0xFFE0E5E4.toInt(),
                subText = 0xFF9FB2AF.toInt(),
                primary = 0xFF4DB6AC.toInt(),
                primarySoft = 0xFF004D45.toInt(),
                ok = 0xFF4ADE80.toInt(),
                okSoft = 0xFF133622.toInt(),
                warn = 0xFFFBBF24.toInt(),
                warnSoft = 0xFF3A2D0B.toInt(),
                danger = 0xFFF87171.toInt(),
                dangerSoft = 0xFF3E1818.toInt()
            )
        } else {
            HudPalette(
                surface = 0xF7FFFFFF.toInt(),
                surfaceAlt = 0xFFE9F2F0.toInt(),
                border = 0xFFD4E3E0.toInt(),
                text = 0xFF161D1C.toInt(),
                subText = 0xFF536663.toInt(),
                primary = 0xFF00796B.toInt(),
                primarySoft = 0xFFD6F2EE.toInt(),
                ok = 0xFF16A34A.toInt(),
                okSoft = 0xFFDCFCE7.toInt(),
                warn = 0xFFD97706.toInt(),
                warnSoft = 0xFFFEF3C7.toInt(),
                danger = 0xFFDC2626.toInt(),
                dangerSoft = 0xFFFEE2E2.toInt()
            )
        }
    }

    private fun pillBg(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun rippleView(view: View, fill: Int, radiusDp: Int, stroke: Int? = null) {
        val base = pillBg(fill, radiusDp, stroke)
        view.background = RippleDrawable(ColorStateList.valueOf(0x2800796B), base, null)
        view.isClickable = true
        view.isFocusable = false
    }

    private fun bindState(root: LinearLayout, state: OverlayState) {
        root.removeAllViews()
        val p = palette()

        val borderColor = when (state) {
            is OverlayState.AwaitingConfirm -> p.warn
            is OverlayState.RunningFlow -> p.primary
            is OverlayState.ResultFeedback -> if (state.isSuccess) p.ok else p.danger
            is OverlayState.Listening -> p.primary
            is OverlayState.Dormant -> p.border
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = pillBg(p.surface, 22, borderColor)
            elevation = dp(10).toFloat()
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        when (state) {
            is OverlayState.Dormant -> bindDormant(card, p, state)
            is OverlayState.Listening -> bindListening(card, p, state)
            is OverlayState.AwaitingConfirm -> bindConfirm(card, p, state)
            is OverlayState.RunningFlow -> bindRunning(card, p, state)
            is OverlayState.ResultFeedback -> bindFeedback(card, p, state)
        }

        root.addView(card)
    }

    private fun bindDormant(card: LinearLayout, p: HudPalette, state: OverlayState.Dormant) {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setOnClickListener { onPttTriggerClick() }
        }
        rippleView(card, p.surface, 22, p.border)

        val botChip = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            background = pillBg(p.primarySoft, 14)
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) }
            addView(ImageView(ctx).apply {
                setImageDrawable(AssistantIcons.drawable(ctx, AssistantIcons.Kind.BOT, p.primary, 18))
            })
        }
        row.addView(botChip)

        row.addView(TextView(ctx).apply {
            text = if (state.isBangla) "কথা বলতে ট্যাপ করুন (Push-to-Talk)" else "Tap to speak command"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.text)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val micPill = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = pillBg(p.primary, 16)
            addView(ImageView(ctx).apply {
                setImageDrawable(AssistantIcons.drawable(ctx, AssistantIcons.Kind.MIC, Color.WHITE, 16))
            })
        }
        row.addView(micPill)
        card.addView(row)
    }

    private fun bindListening(card: LinearLayout, p: HudPalette, state: OverlayState.Listening) {
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val micBadge = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            background = pillBg(p.primary, 14)
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) }
            addView(ImageView(ctx).apply {
                setImageDrawable(AssistantIcons.drawable(ctx, AssistantIcons.Kind.MIC, Color.WHITE, 16))
            })
        }
        topRow.addView(micBadge)

        val statusLabel = when {
            state.isPtt -> "শুনছি — আপনার কমান্ড বলুন"
            state.awakeSec != null && state.awakeSec > 0 -> "সক্রিয় (${state.awakeSec}s) — সরাসরি কমান্ড দিন"
            else -> "\"Hey Bot\" বলে কমান্ড দিন"
        }
        topRow.addView(TextView(ctx).apply {
            text = statusLabel
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.primary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val closeBtn = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER
            minimumWidth = dp(40)
            minimumHeight = dp(36)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            contentDescription = "Sleep or stop listening"
            rippleView(this, p.surfaceAlt, 12)
            setOnClickListener { onDismissOrSleepClick() }
            addView(ImageView(ctx).apply {
                setImageDrawable(AssistantIcons.drawable(ctx, AssistantIcons.Kind.CLOSE, p.subText, 14))
            })
        }
        topRow.addView(closeBtn)
        card.addView(topRow)

        if (state.partialText.isNotBlank()) {
            card.addView(TextView(ctx).apply {
                text = "“${state.partialText}”"
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.text)
                maxLines = 2
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

    private fun bindConfirm(card: LinearLayout, p: HudPalette, state: OverlayState.AwaitingConfirm) {
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(ctx).apply {
            setImageDrawable(AssistantIcons.drawable(ctx, AssistantIcons.Kind.SHIELD, p.warn, 18))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        })
        header.addView(TextView(ctx).apply {
            text = "নিশ্চিত করুন"
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
            fg = Color.WHITE,
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
        row.addView(ImageView(ctx).apply {
            setImageDrawable(AssistantIcons.drawable(ctx, AssistantIcons.Kind.SPARKLE, p.primary, 18))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        })
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
                textSize = 13f
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
        val iconKind = if (state.isSuccess) AssistantIcons.Kind.CHECK_CIRCLE else AssistantIcons.Kind.ALERT_CIRCLE

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(ImageView(ctx).apply {
            setImageDrawable(AssistantIcons.drawable(ctx, iconKind, accent, 20))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) }
        })

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
