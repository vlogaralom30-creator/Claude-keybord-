package com.banglakeys.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/**
 * Theme-synced, fixed-height Emoji Picker panel:
 *  - Top compact header showing active category title (+ Clear pill in Recent tab)
 *  - Responsive scrollable emoji grid (7+ columns in portrait, wider in landscape)
 *  - Bottom category bar: ABC (back) | 9 scrollable category tabs | Hold-to-repeat ⌫ Backspace
 *  - Long-press skin-tone variant overlay on supported People & Body emojis
 */
class EmojiPanelView(
    context: Context,
    val repo: EmojiRepository = EmojiRepository(context),
) : FrameLayout(context) {

    interface Listener {
        fun onBackToKeyboard()
        fun onEmojiSelected(emoji: String)
        fun onBackspace()
    }

    var listener: Listener? = null

    private val d = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * d).toInt()
    private fun dpF(v: Float): Float = v * d

    private var theme: KeyboardTheme = ThemeStore.resolve(context)
    private var activeCategory: EmojiCategory =
        if (repo.hasRecents()) EmojiCategory.RECENT else EmojiCategory.SMILEYS
    private var colCount = 7

    private val rootCol: LinearLayout
    private val headerRow: LinearLayout
    private val categoryTitleView: TextView
    private val hintOrClearBtn: TextView
    private val topDivider: View
    private val scrollView: ScrollView
    private val gridContainer: LinearLayout
    private val bottomDivider: View
    private val bottomBar: LinearLayout
    private val backBtn: TextView
    private val tabScroll: HorizontalScrollView
    private val tabRow: LinearLayout
    private val backspaceBtn: TextView

    private val toneScrim: FrameLayout
    private val toneCard: LinearLayout
    private val tabButtons = LinkedHashMap<EmojiCategory, TextView>()

    private val handler = Handler(Looper.getMainLooper())
    private var repeatActive = false
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!repeatActive) return
            listener?.onBackspace()
            handler.postDelayed(this, 55L)
        }
    }

    init {
        rootCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(4f), dp(10f), dp(4f))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(34f))
        }

        categoryTitleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        hintOrClearBtn = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(26f)
            setPadding(dp(9f), dp(2f), dp(9f), dp(2f))
            tag = "emoji_clear_recent_btn"
        }

        headerRow.addView(categoryTitleView)
        headerRow.addView(hintOrClearBtn)

        topDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1f).coerceAtLeast(1))
        }

        gridContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6f), dp(4f), dp(6f), dp(6f))
        }

        scrollView = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            addView(gridContainer)
        }

        bottomDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1f).coerceAtLeast(1))
        }

        bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6f), dp(4f), dp(6f), dp(4f))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48f))
        }

        backBtn = TextView(context).apply {
            text = "ABC"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = dp(48f)
            minHeight = dp(40f)
            setPadding(dp(8f), 0, dp(8f), 0)
            contentDescription = "Back to keyboard"
            tag = "emoji_back_btn"
            setOnClickListener {
                dismissTonePopup()
                listener?.onBackToKeyboard()
            }
        }

        tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        for (cat in EmojiCategory.values()) {
            val tabBtn = TextView(context).apply {
                text = cat.icon
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
                minWidth = dp(38f)
                minHeight = dp(38f)
                setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
                contentDescription = cat.title
                tag = "emoji_tab_${cat.id}"
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    dp(38f)
                ).apply {
                    marginStart = dp(2f)
                    marginEnd = dp(2f)
                }
                setOnClickListener { selectCategory(cat) }
            }
            tabButtons[cat] = tabBtn
            tabRow.addView(tabBtn)
        }

        tabScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4f)
                marginEnd = dp(4f)
            }
            addView(tabRow)
        }

        backspaceBtn = TextView(context).apply {
            text = "⌫"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = dp(48f)
            minHeight = dp(40f)
            setPadding(dp(8f), 0, dp(8f), 0)
            contentDescription = "Backspace"
            tag = "emoji_backspace_btn"
            setOnClickListener { listener?.onBackspace() }
            setOnLongClickListener {
                repeatActive = true
                listener?.onBackspace()
                handler.removeCallbacks(repeatRunnable)
                handler.postDelayed(repeatRunnable, 60L)
                true
            }
            setOnTouchListener { _, ev ->
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    if (repeatActive) {
                        stopBackspaceRepeat()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        }

        bottomBar.addView(backBtn)
        bottomBar.addView(tabScroll)
        bottomBar.addView(backspaceBtn)

        rootCol.addView(headerRow)
        rootCol.addView(topDivider)
        rootCol.addView(scrollView)
        rootCol.addView(bottomDivider)
        rootCol.addView(bottomBar)
        addView(rootCol)

        toneCard = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
            elevation = dpF(8f)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            isClickable = true
        }

        toneScrim = FrameLayout(context).apply {
            visibility = View.GONE
            isClickable = true
            isFocusable = true
            tag = "emoji_tone_overlay"
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setOnClickListener { dismissTonePopup() }
            addView(toneCard)
        }
        addView(toneScrim)

        applyThemeColors()
        renderAll()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            val newCols = ((w - dp(12f)) / dp(46f)).coerceIn(7, 12)
            if (newCols != colCount) {
                colCount = newCols
                renderGrid()
            }
        }
    }

    override fun onDetachedFromWindow() {
        stopBackspaceRepeat()
        super.onDetachedFromWindow()
    }

    private fun stopBackspaceRepeat() {
        repeatActive = false
        handler.removeCallbacks(repeatRunnable)
    }

    fun reloadTheme() {
        theme = ThemeStore.resolve(context)
        applyThemeColors()
        renderAll()
    }

    fun openPanel(preferredCategory: EmojiCategory? = null) {
        repo.reload()
        dismissTonePopup()
        activeCategory = preferredCategory
            ?: if (repo.hasRecents()) EmojiCategory.RECENT else EmojiCategory.SMILEYS
        scrollView.scrollTo(0, 0)
        renderAll()
    }

    fun currentCategory(): EmojiCategory = activeCategory

    fun selectCategory(category: EmojiCategory) {
        dismissTonePopup()
        if (activeCategory != category) {
            activeCategory = category
            scrollView.scrollTo(0, 0)
        }
        renderAll()
        tabButtons[category]?.let { btn ->
            tabScroll.post {
                val targetX = (btn.left - (tabScroll.width - btn.width) / 2).coerceAtLeast(0)
                tabScroll.smoothScrollTo(targetX, 0)
            }
        }
    }

    fun dismissTonePopup() {
        if (toneScrim.visibility != View.GONE) {
            toneScrim.visibility = View.GONE
            toneCard.removeAllViews()
        }
    }

    fun isTonePopupVisible(): Boolean = toneScrim.visibility == View.VISIBLE

    private fun panelBgColor(): Int =
        if (theme.usesImage) 0xEB15171E.toInt() else theme.bg

    private fun cellFillColor(): Int =
        if (theme.usesImage) 0x00000000 else 0x00000000

    private fun cardSurfaceColor(): Int =
        if (theme.usesImage) 0xF0242733.toInt() else theme.popup

    private fun strokeColor(): Int =
        if (theme.border != 0) theme.border else theme.pillStroke

    private fun pillRipple(fill: Int, stroke: Int, cornerDp: Float, strokeDp: Float = 1f): RippleDrawable {
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(cornerDp)
            setColor(fill)
            if (stroke != 0) {
                setStroke(dp(strokeDp).coerceAtLeast(1), stroke)
            }
        }
        return RippleDrawable(ColorStateList.valueOf(theme.press), gd, null)
    }

    private fun applyThemeColors() {
        setBackgroundColor(panelBgColor())
        bottomBar.setBackgroundColor(if (theme.usesImage) 0xD9101218.toInt() else mixColor(theme.bg, theme.fn, 0.45f))
        categoryTitleView.setTextColor(theme.text)
        topDivider.setBackgroundColor(withAlpha(theme.shadow, 85))
        bottomDivider.setBackgroundColor(withAlpha(theme.shadow, 100))

        backBtn.setTextColor(theme.text)
        backBtn.background = pillRipple(theme.actionBg, strokeColor(), 10f)

        backspaceBtn.setTextColor(theme.text)
        backspaceBtn.background = pillRipple(theme.actionBg, strokeColor(), 10f)

        toneScrim.setBackgroundColor(0x66000000)
        toneCard.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(min(16f, theme.radiusDp + 6f))
            setColor(cardSurfaceColor())
            setStroke(dp(1.5f).coerceAtLeast(1), theme.accent)
        }
    }

    private fun renderAll() {
        renderHeader()
        renderTabs()
        renderGrid()
    }

    private fun renderHeader() {
        categoryTitleView.text = activeCategory.title
        when {
            activeCategory == EmojiCategory.RECENT && repo.hasRecents() -> {
                hintOrClearBtn.visibility = View.VISIBLE
                hintOrClearBtn.text = "Clear"
                hintOrClearBtn.setTextColor(theme.accent)
                hintOrClearBtn.background = pillRipple(theme.actionBg, strokeColor(), 8f)
                hintOrClearBtn.setOnClickListener {
                    repo.clearRecents()
                    renderHeader()
                    renderGrid()
                }
            }
            activeCategory == EmojiCategory.PEOPLE -> {
                hintOrClearBtn.visibility = View.VISIBLE
                hintOrClearBtn.text = "Hold for skin tone"
                hintOrClearBtn.setTextColor(theme.hint)
                hintOrClearBtn.background = null
                hintOrClearBtn.setOnClickListener(null)
                hintOrClearBtn.isClickable = false
            }
            else -> {
                hintOrClearBtn.visibility = View.GONE
                hintOrClearBtn.setOnClickListener(null)
            }
        }
    }

    private fun renderTabs() {
        for ((cat, btn) in tabButtons) {
            val selected = cat == activeCategory
            btn.background = if (selected) {
                pillRipple(theme.badgeBg, theme.accent, 10f, 1.4f)
            } else {
                pillRipple(0x00000000, 0, 10f)
            }
            btn.alpha = if (selected) 1f else 0.72f
        }
    }

    private fun renderGrid() {
        gridContainer.removeAllViews()
        val items = repo.emojisFor(activeCategory)

        if (items.isEmpty()) {
            val emptyBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(20f), dp(26f), dp(20f), dp(20f))
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
            }
            emptyBox.addView(TextView(context).apply {
                text = "🕒 এখনো কোনো Recent Emoji নেই"
                setTextColor(theme.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            emptyBox.addView(TextView(context).apply {
                text = "যেকোনো ক্যাটাগরি থেকে ইমোজি ব্যবহার করলে এখানে সেভ হয়ে থাকবে।"
                setTextColor(theme.hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                gravity = Gravity.CENTER
                setPadding(0, dp(6f), 0, dp(14f))
            })
            emptyBox.addView(TextView(context).apply {
                text = "😀 Smileys দেখুন"
                setTextColor(theme.onAccent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
                background = pillRipple(theme.accent, theme.accent, 12f)
                tag = "emoji_browse_smileys_btn"
                setOnClickListener { selectCategory(EmojiCategory.SMILEYS) }
            })
            gridContainer.addView(emptyBox)
            return
        }

        val cols = colCount.coerceAtLeast(7)
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(2f) }
            }
            for (c in 0 until cols) {
                val idx = i + c
                if (idx < items.size) {
                    val emoji = items[idx]
                    val hasTones = repo.supportsSkinTone(emoji)
                    val cell = EmojiCellView(context, hasTones, theme.hint).apply {
                        text = emoji
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
                        setTextColor(theme.text)
                        gravity = Gravity.CENTER
                        minHeight = dp(42f)
                        contentDescription = emoji
                        tag = "emoji_cell_${activeCategory.id}_$idx"
                        background = pillRipple(cellFillColor(), 0, 10f)
                        layoutParams = LinearLayout.LayoutParams(0, dp(42f), 1f)
                        setOnClickListener {
                            repo.recordRecent(emoji)
                            listener?.onEmojiSelected(emoji)
                        }
                        if (hasTones) {
                            setOnLongClickListener {
                                showTonePopup(emoji)
                                true
                            }
                        }
                    }
                    row.addView(cell)
                } else {
                    row.addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dp(42f), 1f)
                    })
                }
            }
            gridContainer.addView(row)
            i += cols
        }
    }

    private fun showTonePopup(emoji: String) {
        val variants = repo.skinToneVariants(emoji)
        if (variants.isEmpty()) return
        toneCard.removeAllViews()

        val current = repo.preferredEmoji(emoji)
        for ((idx, variant) in variants.withIndex()) {
            val isCurrent = variant == current
            val btn = TextView(context).apply {
                text = variant
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTextColor(theme.text)
                gravity = Gravity.CENTER
                minWidth = dp(44f)
                minHeight = dp(44f)
                contentDescription = variant
                tag = "emoji_tone_$idx"
                background = if (isCurrent) {
                    pillRipple(theme.badgeBg, theme.accent, 10f, 1.4f)
                } else {
                    pillRipple(0x00000000, 0, 10f)
                }
                layoutParams = LinearLayout.LayoutParams(
                    dp(44f),
                    dp(44f)
                ).apply {
                    marginStart = dp(2f)
                    marginEnd = dp(2f)
                }
                setOnClickListener {
                    repo.setPreferredSkinTone(emoji, variant)
                    repo.recordRecent(variant)
                    listener?.onEmojiSelected(variant)
                    dismissTonePopup()
                    if (activeCategory == EmojiCategory.PEOPLE || activeCategory == EmojiCategory.RECENT) {
                        renderGrid()
                    }
                }
            }
            toneCard.addView(btn)
        }
        toneScrim.visibility = View.VISIBLE
    }

    /**
     * Emoji cell that draws a subtle bottom-right corner indicator when skin-tone variants exist.
     */
    private class EmojiCellView(
        context: Context,
        private val hasToneVariants: Boolean,
        indicatorColor: Int,
    ) : TextView(context) {

        private val d = resources.displayMetrics.density
        private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = indicatorColor
            alpha = 140
            style = Paint.Style.FILL
        }
        private val trianglePath = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!hasToneVariants || width <= 0 || height <= 0) return
            val s = 4.5f * d
            val right = width - 4f * d
            val bottom = height - 4f * d
            trianglePath.reset()
            trianglePath.moveTo(right, bottom)
            trianglePath.lineTo(right - s, bottom)
            trianglePath.lineTo(right, bottom - s)
            trianglePath.close()
            canvas.drawPath(trianglePath, indicatorPaint)
        }
    }
}
