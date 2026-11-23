package com.banglakeys.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/**
 * KeyboardView ও NotepadClipboardPanelView-কে একই Container-এ ধরে রাখে এবং
 * প্যানেল খোলা/বন্ধ হলেও ঠিক KeyboardView-এর সমান height লক করে রাখে (size jump করে না)।
 */
class KeyboardHostLayout(
    context: Context,
    val keyboardView: KeyboardView = KeyboardView(context),
    val panelView: NotepadClipboardPanelView = NotepadClipboardPanelView(context),
) : FrameLayout(context) {

    init {
        keyboardView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
        panelView.visibility = View.GONE
        panelView.layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        addView(keyboardView)
        addView(panelView)
    }

    fun showPanel(show: Boolean) {
        keyboardView.visibility = if (show) View.INVISIBLE else View.VISIBLE
        panelView.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) panelView.setManageMode(false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChild(keyboardView, widthMeasureSpec, heightMeasureSpec)
        val lockedW = keyboardView.measuredWidth
        val lockedH = keyboardView.measuredHeight
        if (panelView.visibility != View.GONE) {
            panelView.measure(
                MeasureSpec.makeMeasureSpec(lockedW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(lockedH, MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(lockedW, lockedH)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        keyboardView.layout(0, 0, w, h)
        if (panelView.visibility != View.GONE) {
            panelView.layout(0, 0, w, h)
        }
    }
}

/**
 * Gboard-style 2-column scrollable Clipboard & Notepad panel:
 *  - Keyboard-এর exact একই height-এ ফিট হয় (size up/down jump করে না)
 *  - ৪ লাইনের readable কার্ড (পুরো লেখা স্পষ্ট দেখা যায়)
 *  - Pinned (Saved Notes) ও Recent (Clipboard History) আলাদা সেকশন
 *  - প্রতিটি কার্ডে Pin / Unpin, Edit ও Remove সাপোর্ট (Manage মোড বা Long-Press দিয়ে)
 *  - "+ Note" বাটন দিয়ে সরাসরি নতুন নোট যোগ করার সুবিধা
 */
class NotepadClipboardPanelView(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onBackToKeyboard()
        fun onPasteItem(text: String)
        fun onAddNewNote()
        fun onPinRecent(text: String)
        fun onUnpinNote(index: Int, text: String)
        fun onEditPinned(index: Int, text: String)
        fun onEditRecent(text: String)
        fun onRemovePinned(index: Int, text: String)
        fun onRemoveRecent(text: String)
        fun onClearRecent()
    }

    var listener: Listener? = null

    private val d = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * d).toInt()
    private fun dpF(v: Float): Float = v * d

    private var theme: KeyboardTheme = ThemeStore.resolve(context)
    private var pinnedItems: List<String> = emptyList()
    private var recentItems: List<String> = emptyList()
    private var manageMode = false

    private val rootCol: LinearLayout
    private val headerRow: LinearLayout
    private val backBtn: TextView
    private val titleView: TextView
    private val addNoteBtn: TextView
    private val manageBtn: TextView
    private val dividerView: View
    private val scrollView: ScrollView
    private val listContainer: LinearLayout

    init {
        rootCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6f), dp(4f), dp(8f), dp(4f))
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48f))
        }

        backBtn = TextView(context).apply {
            text = "‹"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minWidth = dp(44f)
            minHeight = dp(40f)
            contentDescription = "Back to keyboard"
            tag = "panel_back_btn"
            setOnClickListener { listener?.onBackToKeyboard() }
        }

        titleView = TextView(context).apply {
            text = "📋 Clipboard & Notes"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(4f), 0, dp(8f), 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        addNoteBtn = TextView(context).apply {
            text = "+ Note"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(34f)
            setPadding(dp(11f), dp(5f), dp(11f), dp(5f))
            tag = "panel_add_note_btn"
            setOnClickListener { listener?.onAddNewNote() }
        }

        manageBtn = TextView(context).apply {
            text = "Manage"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(34f)
            setPadding(dp(11f), dp(5f), dp(11f), dp(5f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6f) }
            tag = "panel_manage_btn"
            setOnClickListener {
                manageMode = !manageMode
                render()
            }
        }

        headerRow.addView(backBtn)
        headerRow.addView(titleView)
        headerRow.addView(addNoteBtn)
        headerRow.addView(manageBtn)

        dividerView = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1f).coerceAtLeast(1))
        }

        listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8f), dp(6f), dp(8f), dp(10f))
        }

        scrollView = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            addView(listContainer)
        }

        rootCol.addView(headerRow)
        rootCol.addView(dividerView)
        rootCol.addView(scrollView)
        addView(rootCol)

        applyThemeColors()
    }

    fun reloadTheme() {
        theme = ThemeStore.resolve(context)
        applyThemeColors()
        render()
    }

    fun setData(pinned: List<String>, recent: List<String>) {
        pinnedItems = pinned
        recentItems = recent
        render()
    }

    fun setManageMode(enabled: Boolean) {
        if (manageMode != enabled) {
            manageMode = enabled
            render()
        }
    }

    fun isManageMode(): Boolean = manageMode

    private fun panelBgColor(): Int {
        return if (theme.usesImage) 0xEB15171E.toInt() else theme.bg
    }

    private fun cardBgColor(): Int {
        return if (theme.usesImage) 0xD9242733.toInt() else theme.key
    }

    private fun cardStrokeColor(highlight: Boolean): Int {
        if (highlight) return theme.accent
        return if (theme.border != 0) theme.border else theme.pillStroke
    }

    private fun pillRipple(fill: Int, stroke: Int, cornerDp: Float): RippleDrawable {
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(cornerDp)
            setColor(fill)
            setStroke(dp(1f).coerceAtLeast(1), stroke)
        }
        return RippleDrawable(ColorStateList.valueOf(theme.press), gd, null)
    }

    private fun applyThemeColors() {
        setBackgroundColor(panelBgColor())
        backBtn.setTextColor(theme.text)
        backBtn.background = pillRipple(theme.actionBg, cardStrokeColor(false), 10f)

        titleView.setTextColor(theme.text)
        dividerView.setBackgroundColor(withAlpha(theme.shadow, 90))

        addNoteBtn.setTextColor(theme.accent)
        addNoteBtn.background = pillRipple(theme.badgeBg, theme.badgeStroke, 10f)

        updateManageButtonStyle()
    }

    private fun updateManageButtonStyle() {
        if (manageMode) {
            manageBtn.text = "Done"
            manageBtn.setTextColor(theme.onAccent)
            manageBtn.background = pillRipple(theme.accent, theme.accent, 10f)
        } else {
            manageBtn.text = "Manage"
            manageBtn.setTextColor(theme.text)
            manageBtn.background = pillRipple(theme.actionBg, cardStrokeColor(false), 10f)
        }
    }

    private fun render() {
        updateManageButtonStyle()
        listContainer.removeAllViews()

        if (pinnedItems.isEmpty() && recentItems.isEmpty()) {
            val emptyBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(20f), dp(28f), dp(20f), dp(20f))
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                )
            }
            emptyBox.addView(TextView(context).apply {
                text = "কোনো Pinned Note বা Clipboard আইটেম নেই"
                setTextColor(theme.text)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            emptyBox.addView(TextView(context).apply {
                text = "যেকোনো লেখা Copy করলে বা '+ Note' চাপলে এখানে বড় কার্ডে দেখাবে।"
                setTextColor(theme.hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                gravity = Gravity.CENTER
                setPadding(0, dp(6f), 0, dp(14f))
            })
            emptyBox.addView(TextView(context).apply {
                text = "+ নতুন Note লিখুন"
                setTextColor(theme.onAccent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
                background = pillRipple(theme.accent, theme.accent, 12f)
                setOnClickListener { listener?.onAddNewNote() }
            })
            listContainer.addView(emptyBox)
            return
        }

        if (pinnedItems.isNotEmpty()) {
            listContainer.addView(sectionHeader("📌 Pinned (${pinnedItems.size})", showClear = false))
            addTwoColumnGrid(pinnedItems, isPinned = true)
        }

        if (recentItems.isNotEmpty()) {
            listContainer.addView(sectionHeader("🕒 Recent (${recentItems.size})", showClear = true))
            addTwoColumnGrid(recentItems, isPinned = false)
        }
    }

    private fun sectionHeader(label: String, showClear: Boolean): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(6f), dp(4f), dp(6f))
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (showClear) {
            row.addView(TextView(context).apply {
                text = "Clear Recent"
                setTextColor(theme.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
                background = pillRipple(theme.actionBg, cardStrokeColor(false), 8f)
                tag = "panel_clear_recent_btn"
                setOnClickListener { listener?.onClearRecent() }
            })
        }
        return row
    }

    private fun addTwoColumnGrid(items: List<String>, isPinned: Boolean) {
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8f) }
            }

            val leftCard = buildCard(i, items[i], isPinned)
            leftCard.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = dp(4f)
            }
            row.addView(leftCard)

            if (i + 1 < items.size) {
                val rightCard = buildCard(i + 1, items[i + 1], isPinned)
                rightCard.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = dp(4f)
                }
                row.addView(rightCard)
            } else {
                val spacer = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f).apply {
                        marginStart = dp(4f)
                    }
                }
                row.addView(spacer)
            }

            listContainer.addView(row)
            i += 2
        }
    }

    private fun buildCard(index: Int, rawText: String, isPinned: Boolean): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11f), dp(10f), dp(11f), dp(9f))
            background = pillRipple(
                fill = cardBgColor(),
                stroke = cardStrokeColor(highlight = manageMode && isPinned),
                cornerDp = min(14f, theme.radiusDp + 4f)
            )
            setOnClickListener {
                if (manageMode) {
                    // Manage মোডে ক্লিক করলে এডিট খুলে বা স্পষ্ট অপশন দেখায়
                    if (isPinned) listener?.onEditPinned(index, rawText)
                    else listener?.onEditRecent(rawText)
                } else {
                    listener?.onPasteItem(rawText)
                }
            }
            setOnLongClickListener {
                manageMode = !manageMode
                render()
                true
            }
        }

        val isGlyph = GlyphAlphabet.looksLikeGlyphText(rawText)
        if (isPinned || isGlyph) {
            val tagView = TextView(context).apply {
                text = when {
                    isPinned && isGlyph -> "📌 🔐 Secret Cipher"
                    isPinned -> "📌 Pinned"
                    else -> "🔐 Secret Cipher"
                }
                setTextColor(theme.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(3f))
            }
            card.addView(tagView)
        }

        val body = TextView(context).apply {
            text = rawText
            setTextColor(theme.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dpF(2f), 1.0f)
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }
        card.addView(body)

        if (manageMode) {
            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8f), 0, 0)
            }

            actions.addView(miniActionBtn(
                label = if (isPinned) "Unpin" else "Pin",
                accent = !isPinned,
                onClick = {
                    if (isPinned) listener?.onUnpinNote(index, rawText)
                    else listener?.onPinRecent(rawText)
                }
            ).apply {
                tag = if (isPinned) "panel_unpin_$index" else "panel_pin_$index"
            })
            actions.addView(miniActionBtn(
                label = "Edit",
                accent = false,
                onClick = {
                    if (isPinned) listener?.onEditPinned(index, rawText)
                    else listener?.onEditRecent(rawText)
                }
            ).apply {
                tag = if (isPinned) "panel_edit_pinned_$index" else "panel_edit_recent_$index"
                (layoutParams as LinearLayout.LayoutParams).marginStart = dp(4f)
            })
            actions.addView(miniActionBtn(
                label = "✕",
                accent = false,
                danger = true,
                onClick = {
                    if (isPinned) listener?.onRemovePinned(index, rawText)
                    else listener?.onRemoveRecent(rawText)
                }
            ).apply {
                tag = if (isPinned) "panel_remove_pinned_$index" else "panel_remove_recent_$index"
                (layoutParams as LinearLayout.LayoutParams).marginStart = dp(4f)
            })

            card.addView(actions)
        }

        return card
    }

    private fun miniActionBtn(
        label: String,
        accent: Boolean,
        danger: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val bg = when {
            accent -> theme.badgeBg
            danger -> 0x33EF4444
            else -> theme.actionBg
        }
        val stroke = when {
            accent -> theme.badgeStroke
            danger -> 0x99EF4444.toInt()
            else -> cardStrokeColor(false)
        }
        val fg = when {
            accent -> theme.badgeText
            danger -> 0xFFEF4444.toInt()
            else -> theme.text
        }
        return TextView(context).apply {
            text = label
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(30f)
            setPadding(dp(7f), dp(4f), dp(7f), dp(4f))
            background = pillRipple(bg, stroke, 8f)
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }
    }
}
