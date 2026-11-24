package com.banglakeys.app

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/**
 * কিবোর্ডের ঠিক উপরে থাকা নিবেদিত Decrypted Secret Message বার।
 * ব্যবহারকারী কোনো গোপন কোড কপি করলে এখানে আসল মেসেজ, প্রেরকের নাম,
 * এক-ট্যাপ Reply বাটন, Copy ও Close বাটন এবং ১৫ সেকেন্ডের auto-hide টাইমার দেখায়।
 */
class SecretDecodeBannerView(context: Context) : FrameLayout(context) {

    sealed class BannerState {
        object Hidden : BannerState()
        data class Decoded(
            val senderName: String,
            val plainText: String,
            val cipherText: String,
            val durationMs: Long = DEFAULT_AUTO_HIDE_MS,
        ) : BannerState()

        data class Unrecognized(
            val cipherText: String,
            val durationMs: Long = UNKNOWN_AUTO_HIDE_MS,
        ) : BannerState()
    }

    interface Listener {
        fun onReplyClicked(senderName: String)
        fun onCopyPlainClicked(plainText: String)
        fun onOpenVaultClicked()
        fun onDismissed() {}
    }

    var listener: Listener? = null

    private val d = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * d).toInt()
    private fun dpF(v: Float): Float = v * d

    private var theme: KeyboardTheme = ThemeStore.resolve(context)
    private var currentState: BannerState = BannerState.Hidden
    private var expanded = false

    private val cardContainer: LinearLayout
    private val topRow: LinearLayout
    private val senderBadge: TextView
    private val messageText: TextView
    private val replyBtn: TextView
    private val copyBtn: TextView
    private val vaultBtn: TextView
    private val closeBtn: TextView
    private val progressTrack: FrameLayout
    private val progressFill: View

    private val handler = Handler(Looper.getMainLooper())
    private var progressAnimator: ValueAnimator? = null
    private val autoHideRunnable = Runnable {
        hide()
        listener?.onDismissed()
    }

    init {
        visibility = View.GONE
        setPadding(dp(8f), dp(4f), dp(8f), dp(4f))

        cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            elevation = dpF(2f)
        }

        topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(7f), dp(6f), dp(6f))
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6f)
            }
            setOnClickListener {
                if (currentState is BannerState.Decoded) {
                    expanded = !expanded
                    messageText.maxLines = if (expanded) 8 else 2
                }
            }
        }

        senderBadge = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            tag = "secret_banner_sender"
        }

        messageText = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2f), 0, 0)
            tag = "secret_banner_text"
        }

        textCol.addView(senderBadge)
        textCol.addView(messageText)

        replyBtn = TextView(context).apply {
            text = "↩ Reply"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(38f)
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            contentDescription = "Reply in Secret Mode"
            tag = "secret_banner_reply_btn"
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(4f) }
            setOnClickListener {
                val st = currentState as? BannerState.Decoded ?: return@setOnClickListener
                val sender = st.senderName
                hide()
                listener?.onReplyClicked(sender)
            }
        }

        copyBtn = TextView(context).apply {
            text = "📋"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            minWidth = dp(40f)
            minHeight = dp(38f)
            contentDescription = "Copy decoded message"
            tag = "secret_banner_copy_btn"
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(4f) }
            setOnClickListener {
                val st = currentState as? BannerState.Decoded ?: return@setOnClickListener
                listener?.onCopyPlainClicked(st.plainText)
            }
        }

        vaultBtn = TextView(context).apply {
            text = "Vault"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(38f)
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            contentDescription = "Open Secret Vault"
            tag = "secret_banner_vault_btn"
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(4f) }
            setOnClickListener {
                hide()
                listener?.onOpenVaultClicked()
            }
        }

        closeBtn = TextView(context).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = dp(40f)
            minHeight = dp(38f)
            contentDescription = "Dismiss secret message banner"
            tag = "secret_banner_close_btn"
            setOnClickListener {
                hide()
                listener?.onDismissed()
            }
        }

        topRow.addView(textCol)
        topRow.addView(replyBtn)
        topRow.addView(copyBtn)
        topRow.addView(vaultBtn)
        topRow.addView(closeBtn)

        progressTrack = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(3f).coerceAtLeast(2)
            )
        }
        progressFill = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
        progressTrack.addView(progressFill)

        cardContainer.addView(topRow)
        cardContainer.addView(progressTrack)
        addView(cardContainer)

        applyThemeColors()
    }

    fun reloadTheme() {
        theme = ThemeStore.resolve(context)
        applyThemeColors()
    }

    fun currentState(): BannerState = currentState

    fun showDecoded(
        senderName: String,
        plainText: String,
        cipherText: String,
        durationMs: Long = DEFAULT_AUTO_HIDE_MS,
    ) {
        val state = BannerState.Decoded(senderName, plainText, cipherText, durationMs)
        currentState = state
        expanded = false

        senderBadge.text = "🔓 $senderName · গোপন বার্তা"
        messageText.maxLines = 2
        messageText.text = plainText

        replyBtn.visibility = View.VISIBLE
        copyBtn.visibility = View.VISIBLE
        vaultBtn.visibility = View.GONE
        visibility = View.VISIBLE

        startTimer(durationMs)
    }

    fun showUnrecognized(
        cipherText: String,
        durationMs: Long = UNKNOWN_AUTO_HIDE_MS,
    ) {
        currentState = BannerState.Unrecognized(cipherText, durationMs)
        expanded = false

        senderBadge.text = "🔒 অজানা Secret Code"
        messageText.maxLines = 2
        messageText.text = "এই কোডের passphrase সেভ করা নেই"

        replyBtn.visibility = View.GONE
        copyBtn.visibility = View.GONE
        vaultBtn.visibility = View.VISIBLE
        visibility = View.VISIBLE

        startTimer(durationMs)
    }

    fun hide() {
        stopTimer()
        if (currentState != BannerState.Hidden) {
            currentState = BannerState.Hidden
            messageText.text = ""
            senderBadge.text = ""
        }
        visibility = View.GONE
    }

    override fun onDetachedFromWindow() {
        stopTimer()
        super.onDetachedFromWindow()
    }

    private fun startTimer(durationMs: Long) {
        stopTimer()
        val safeDuration = durationMs.coerceAtLeast(1000L)
        handler.postDelayed(autoHideRunnable, safeDuration)

        progressFill.scaleX = 1f
        progressFill.pivotX = 0f
        progressAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = safeDuration
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                progressFill.scaleX = anim.animatedValue as Float
            }
            start()
        }
    }

    private fun stopTimer() {
        handler.removeCallbacks(autoHideRunnable)
        progressAnimator?.cancel()
        progressAnimator = null
    }

    private val needsDarkSurface: Boolean
        get() = theme.usesImage || (theme.bgAsset != null && theme.dark)

    private fun outerBgColor(): Int =
        if (needsDarkSurface) 0xEB15171E.toInt() else theme.bg

    private fun cardBgColor(): Int =
        if (needsDarkSurface) 0xF0222634.toInt() else theme.popup

    private fun strokeColor(): Int =
        if (theme.border != 0) theme.border else theme.badgeStroke

    private fun pillRipple(fill: Int, stroke: Int, cornerDp: Float): RippleDrawable {
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(cornerDp)
            setColor(fill)
            if (stroke != 0) {
                setStroke(dp(1f).coerceAtLeast(1), stroke)
            }
        }
        return RippleDrawable(ColorStateList.valueOf(theme.press), gd, null)
    }

    private fun applyThemeColors() {
        setBackgroundColor(outerBgColor())
        val corner = min(14f, theme.radiusDp + 4f)
        cardContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(corner)
            setColor(cardBgColor())
            setStroke(dp(1.2f).coerceAtLeast(1), strokeColor())
        }

        senderBadge.setTextColor(theme.accent)
        messageText.setTextColor(theme.text)

        replyBtn.setTextColor(theme.onAccent)
        replyBtn.background = pillRipple(theme.accent, theme.accent, 10f)

        copyBtn.setTextColor(theme.text)
        copyBtn.background = pillRipple(theme.actionBg, theme.pillStroke, 10f)

        vaultBtn.setTextColor(theme.accent)
        vaultBtn.background = pillRipple(theme.badgeBg, theme.badgeStroke, 10f)

        closeBtn.setTextColor(theme.text)
        closeBtn.background = pillRipple(theme.actionBg, theme.pillStroke, 10f)

        progressTrack.setBackgroundColor(withAlpha(theme.shadow, 50))
        progressFill.setBackgroundColor(theme.accent)
    }

    companion object {
        const val DEFAULT_AUTO_HIDE_MS = 15_000L
        const val UNKNOWN_AUTO_HIDE_MS = 6_000L
    }
}
