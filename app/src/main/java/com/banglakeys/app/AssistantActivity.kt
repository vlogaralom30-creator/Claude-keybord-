package com.banglakeys.app

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * BanglaKeys Assistant কনসোল (pure Material-styled View UI + custom Canvas vector icons).
 *
 * এখান থেকে: Listening মোড, ৫/৫ সেটআপ ও SAF ফোল্ডার, Side-Edge Overlay ও অবস্থান রিসেট,
 * Auto Ad Skipper (YouTube + Verified Game/App Ads), কনফার্মেশন সেফটি, Gemini Fallback।
 */
class AssistantActivity : Activity() {

    companion object {
        private const val REQ_PERMS = 201
        private const val REQ_STORAGE_TREE = 202
    }

    private data class Palette(
        val bg: Int,
        val card: Int,
        val border: Int,
        val primary: Int,
        val primarySoft: Int,
        val text: Int,
        val subText: Int,
        val ok: Int,
        val okSoft: Int,
        val warn: Int,
        val warnSoft: Int,
        val danger: Int,
        val dangerSoft: Int
    )

    private lateinit var pal: Palette

    // Dynamic status / interactive widgets
    private lateinit var statusPillText: TextView
    private lateinit var statusPillBg: LinearLayout
    private lateinit var statusPillIcon: ImageView
    private lateinit var btnService: LinearLayout
    private lateinit var btnServiceText: TextView
    private lateinit var btnServiceIcon: ImageView
    private lateinit var btnPttSpeak: LinearLayout
    private lateinit var btnLang: LinearLayout
    private lateinit var btnLangText: TextView

    private lateinit var chipContinuous: LinearLayout
    private lateinit var chipContinuousText: TextView
    private lateinit var chipPtt: LinearLayout
    private lateinit var chipPttText: TextView

    private lateinit var checklistProgress: TextView
    private val checkRows = ArrayList<Triple<ImageView, TextView, TextView>>()
    private lateinit var safStatusText: TextView

    private lateinit var switchOverlay: Switch
    private lateinit var overlayEdgeText: TextView
    private lateinit var switchAdSkip: Switch
    private lateinit var switchAdSkipGames: Switch
    private lateinit var adSkipStatsText: TextView

    private lateinit var switchConfirmCall: Switch
    private lateinit var switchConfirmSend: Switch
    private lateinit var switchConfirmAiSend: Switch
    private lateinit var timeoutLabel: TextView
    private lateinit var timeoutSeek: SeekBar

    private lateinit var geminiStatusText: TextView
    private lateinit var geminiKeyInput: EditText

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun buildPalette(): Palette {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) {
            Palette(
                bg = 0xFF0E1413.toInt(),
                card = 0xFF171F1E.toInt(),
                border = 0xFF283533.toInt(),
                primary = 0xFF4DB6AC.toInt(),
                primarySoft = 0xFF004D45.toInt(),
                text = 0xFFE3E9E8.toInt(),
                subText = 0xFF9EB0AD.toInt(),
                ok = 0xFF4ADE80.toInt(),
                okSoft = 0xFF133622.toInt(),
                warn = 0xFFFBBF24.toInt(),
                warnSoft = 0xFF3A2D0B.toInt(),
                danger = 0xFFF87171.toInt(),
                dangerSoft = 0xFF3E1818.toInt()
            )
        } else {
            Palette(
                bg = 0xFFF2F6F5.toInt(),
                card = 0xFFFFFFFF.toInt(),
                border = 0xFFD9E4E1.toInt(),
                primary = 0xFF00796B.toInt(),
                primarySoft = 0xFFD8F3EF.toInt(),
                text = 0xFF161D1C.toInt(),
                subText = 0xFF566865.toInt(),
                ok = 0xFF15803D.toInt(),
                okSoft = 0xFFDCFCE7.toInt(),
                warn = 0xFFB45309.toInt(),
                warnSoft = 0xFFFEF3C7.toInt(),
                danger = 0xFFB91C1C.toInt(),
                dangerSoft = 0xFFFEE2E2.toInt()
            )
        }
    }

    private fun roundedBg(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun rippleCard(view: View, fill: Int, radiusDp: Int, stroke: Int? = null) {
        val base = roundedBg(fill, radiusDp, stroke)
        view.background = RippleDrawable(ColorStateList.valueOf(0x2200796B), base, null)
        view.isClickable = true
        view.isFocusable = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pal = buildPalette()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }

        // ---- Header
        root.addView(buildHeader())

        // ---- 1. Status & Service Card
        root.addView(buildServiceCard())

        // ---- 2. Side-Edge Overlay & Auto Ad Skipper
        root.addView(buildOverlayAndAdSkipCard())

        // ---- 3. 5/5 Setup Checklist & SAF Folder
        root.addView(buildSetupChecklistCard())

        // ---- 4. Safety & Confirmation Rules
        root.addView(buildSafetyCard())

        // ---- 5. Gemini Fallback + Gesture Test
        root.addView(buildGeminiAndTestCard())

        setContentView(ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_STORAGE_TREE && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                AssistantPrefs.setStorageTreeUri(this, uri.toString())
                toast("Storage ফোল্ডার সেভ হয়েছে")
                refresh()
            }
        }
    }

    // ------------------------------------------------------------ Section Builders

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }

        val backBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            rippleCard(this, pal.card, 14, pal.border)
            contentDescription = "Back"
            setOnClickListener { finish() }
            addView(ImageView(this@AssistantActivity).apply {
                setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, AssistantIcons.Kind.BACK, pal.text, 20))
            })
        }
        row.addView(backBtn)

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            }
            addView(TextView(this@AssistantActivity).apply {
                text = "BanglaKeys Assistant"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.text)
            })
            addView(TextView(this@AssistantActivity).apply {
                text = "ভয়েস কমান্ড, সাইড-এজ ওভারলে ও অ্যাড স্কিপার"
                textSize = 12.5f
                setTextColor(pal.subText)
            })
        }
        row.addView(titleCol)

        statusPillBg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(12), dp(6))
            background = roundedBg(pal.primarySoft, 99)
        }
        statusPillIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginEnd = dp(6) }
        }
        statusPillText = TextView(this).apply {
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
        }
        statusPillBg.addView(statusPillIcon)
        statusPillBg.addView(statusPillText)
        row.addView(statusPillBg)

        return row
    }

    private fun makeCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(pal.card, 18, pal.border)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
    }

    private fun addCardHeader(card: LinearLayout, icon: AssistantIcons.Kind, title: String, subtitle: String? = null) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        header.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundedBg(pal.primarySoft, 10)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) }
            addView(ImageView(this@AssistantActivity).apply {
                setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, icon, pal.primary, 18))
            })
        })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@AssistantActivity).apply {
                text = title
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.text)
            })
            if (!subtitle.isNullOrBlank()) {
                addView(TextView(this@AssistantActivity).apply {
                    text = subtitle
                    textSize = 12f
                    setTextColor(pal.subText)
                })
            }
        }
        header.addView(col)
        card.addView(header)
    }

    private fun buildIconButton(
        icon: AssistantIcons.Kind,
        label: String,
        fg: Int,
        bg: Int,
        stroke: Int? = null,
        onClick: () -> Unit
    ): Triple<LinearLayout, ImageView, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            rippleCard(this, bg, 14, stroke)
            setOnClickListener { onClick() }
        }
        val iv = ImageView(this).apply {
            setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, icon, fg, 18))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        }
        val tv = TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(fg)
        }
        box.addView(iv)
        box.addView(tv)
        return Triple(box, iv, tv)
    }

    private fun buildServiceCard(): View {
        val card = makeCard()
        addCardHeader(
            card,
            AssistantIcons.Kind.BOT,
            "Assistant কন্ট্রোল ও ভাষা",
            "\"Hey Bot\" বা সাইড ট্যাব থেকে কমান্ড দিন"
        )

        val svcTriple = buildIconButton(
            AssistantIcons.Kind.PLAY,
            "Assistant চালু করুন",
            Color.WHITE,
            pal.primary
        ) {
            if (AssistantListenerService.running) {
                AssistantListenerService.stop(this)
            } else if (!AssistantSetup.isReady(this)) {
                toast("আগে নিচের Accessibility আর Microphone অনুমতি দিন")
            } else {
                AssistantListenerService.start(this)
            }
            Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 350)
        }
        btnService = svcTriple.first
        btnServiceIcon = svcTriple.second
        btnServiceText = svcTriple.third
        card.addView(btnService, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        val pttTriple = buildIconButton(
            AssistantIcons.Kind.MIC,
            "এখনই কথা বলুন (Push-to-Talk)",
            pal.primary,
            pal.primarySoft
        ) {
            if (!AssistantSetup.isReady(this)) {
                toast("আগে Accessibility ও Microphone চালু করুন")
            } else {
                AssistantListenerService.triggerPtt(this)
                Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 350)
            }
        }
        btnPttSpeak = pttTriple.first
        card.addView(btnPttSpeak, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        val langTriple = buildIconButton(
            AssistantIcons.Kind.GLOBE,
            "শোনার ভাষা",
            pal.text,
            pal.bg,
            pal.border
        ) {
            AssistantSetup.setBangla(this, !AssistantSetup.isBangla(this))
            AssistantListenerService.notifyConfigChanged(this)
            refresh()
            toast("শোনার ভাষা আপডেট হয়েছে")
        }
        btnLang = langTriple.first
        btnLangText = langTriple.third
        card.addView(btnLang, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // Listening Mode Toggle Chips
        card.addView(TextView(this).apply {
            text = "Listening Mode"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.subText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        })

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val cChip = buildIconButton(AssistantIcons.Kind.MIC, "Continuous", pal.primary, pal.primarySoft) {
            AssistantPrefs.setListeningMode(this, ListeningMode.CONTINUOUS)
            AssistantListenerService.notifyConfigChanged(this)
            refresh()
        }
        chipContinuous = cChip.first
        chipContinuousText = cChip.third
        modeRow.addView(chipContinuous, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        val pChip = buildIconButton(AssistantIcons.Kind.SHIELD, "Quiet PTT", pal.text, pal.bg, pal.border) {
            AssistantPrefs.setListeningMode(this, ListeningMode.PUSH_TO_TALK)
            AssistantListenerService.notifyConfigChanged(this)
            refresh()
        }
        chipPtt = pChip.first
        chipPttText = pChip.third
        modeRow.addView(chipPtt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })

        card.addView(modeRow)
        return card
    }

    private fun buildOverlayAndAdSkipCard(): View {
        val card = makeCard()
        addCardHeader(
            card,
            AssistantIcons.Kind.OVERLAY,
            "সাইড-এজ ওভারলে ও Auto Ad Skipper",
            "উপরের স্ক্রিন ফাঁকা রাখতে পাশে ভাসমান ট্যাব + নিরাপদ বিজ্ঞাপন স্কিপার"
        )

        switchOverlay = addToggleRow(
            card,
            "ভাসমান সাইড-এজ ট্যাব দেখান",
            "বাম বা ডান কিনারায় টেনে নেওয়া যায় (ট্যাপ করলে কমান্ড নেয়)",
            AssistantPrefs.overlayHudEnabled(this)
        ) { checked ->
            AssistantPrefs.setOverlayHudEnabled(this, checked)
            AssistantListenerService.notifyConfigChanged(this)
        }

        overlayEdgeText = TextView(this).apply {
            textSize = 12f
            setTextColor(pal.subText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(8)
            }
        }
        card.addView(overlayEdgeText)

        val resetBtn = buildIconButton(
            AssistantIcons.Kind.REFRESH,
            "সাইড ট্যাবের অবস্থান রিসেট করুন",
            pal.primary,
            pal.primarySoft
        ) {
            AssistantPrefs.resetOverlayPosition(this)
            AssistantListenerService.notifyConfigChanged(this)
            refresh()
            toast("ডান পাশে ডিফল্ট অবস্থানে রিসেট হয়েছে")
        }.first
        card.addView(resetBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // Divider
        card.addView(View(this).apply {
            setBackgroundColor(pal.border)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply { bottomMargin = dp(10) }
        })

        val adHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
            addView(ImageView(this@AssistantActivity).apply {
                setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, AssistantIcons.Kind.SKIP_FORWARD, pal.primary, 18))
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
            })
            addView(TextView(this@AssistantActivity).apply {
                text = "Auto Ad Skipper (Strict Guard)"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.text)
            })
        }
        card.addView(adHead)

        switchAdSkip = addToggleRow(
            card,
            "YouTube ও ভেরিফাইড বিজ্ঞাপন অটো-স্কিপ",
            "সাইন-আপ, রেজিস্ট্রেশন বা ফর্মের 'Skip' বাটন সম্পূর্ণ ব্লক করা থাকে",
            AssistantPrefs.adSkipEnabled(this)
        ) { checked ->
            AssistantPrefs.setAdSkipEnabled(this, checked)
        }

        switchAdSkipGames = addToggleRow(
            card,
            "গেম ও অ্যাপের (×) ক্লোজ অ্যাড স্কিপ",
            "কেবল যাচাইকৃত Ad-SDK ওভারলে এবং কাউন্টডাউন শেষ হলেই ক্লিক করবে",
            AssistantPrefs.adSkipGamesEnabled(this)
        ) { checked ->
            AssistantPrefs.setAdSkipGamesEnabled(this, checked)
        }

        adSkipStatsText = TextView(this).apply {
            textSize = 12f
            setTextColor(pal.primary)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        card.addView(adSkipStatsText)

        return card
    }

    private fun buildSetupChecklistCard(): View {
        val card = makeCard()
        val headRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        headRow.addView(TextView(this).apply {
            text = "প্রয়োজনীয় অনুমতি ও সেটআপ"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.text)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        checklistProgress = TextView(this).apply {
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedBg(pal.primarySoft, 99)
            setTextColor(pal.primary)
        }
        headRow.addView(checklistProgress)
        card.addView(headRow)

        fun addChecklistItem(icon: AssistantIcons.Kind, title: String, actionText: String, onClick: () -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                rippleCard(this, pal.bg, 12, pal.border)
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            }
            val iv = ImageView(this).apply {
                setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, icon, pal.subText, 18))
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) }
            }
            val tv = TextView(this).apply {
                text = title
                textSize = 13.5f
                setTextColor(pal.text)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val badge = TextView(this).apply {
                text = actionText
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.primary)
            }
            row.addView(iv)
            row.addView(tv)
            row.addView(badge)
            card.addView(row)
            checkRows.add(Triple(iv, tv, badge))
        }

        addChecklistItem(AssistantIcons.Kind.ACCESSIBILITY, "১. Accessibility Service", "চালু করুন") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addChecklistItem(AssistantIcons.Kind.MIC, "২. Microphone Permission", "অনুমতি দিন") {
            requestPermissions(AssistantSetup.runtimePermissions(), REQ_PERMS)
        }
        addChecklistItem(AssistantIcons.Kind.MESSAGE, "৩. Contacts & Phone Call", "অনুমতি দিন") {
            requestPermissions(AssistantSetup.runtimePermissions(), REQ_PERMS)
        }
        addChecklistItem(AssistantIcons.Kind.OVERLAY, "৪. Display Over Other Apps", "সেটিংস") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        addChecklistItem(AssistantIcons.Kind.APP_INFO, "৫. Android 13+ Restricted Setting", "App Info") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }

        // SAF Storage Folder picker for Pack F
        safStatusText = TextView(this).apply {
            textSize = 12f
            setTextColor(pal.subText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(6)
            }
        }
        card.addView(safStatusText)

        val safBtn = buildIconButton(
            AssistantIcons.Kind.FOLDER,
            "Storage Folder বেছে নিন (ফাইল/ফোল্ডার তৈরি)",
            pal.text,
            pal.bg,
            pal.border
        ) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_STORAGE_TREE)
            } catch (_: Exception) {
                toast("এই ডিভাইসে Folder Picker পাওয়া যায়নি")
            }
        }.first
        card.addView(safBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return card
    }

    private fun buildSafetyCard(): View {
        val card = makeCard()
        addCardHeader(
            card,
            AssistantIcons.Kind.SHIELD,
            "নিরাপত্তা ও কনফার্মেশন",
            "ভুল কল বা মেসেজ ঠেকাতে আগে কনফার্ম চাইবে"
        )

        switchConfirmCall = addToggleRow(
            card,
            "ফোন / অ্যাপ কলের আগে Confirm চাইবে",
            "Call, imo বা WhatsApp কলের আগে হ্যাঁ/বাতিল দেখাবে",
            AssistantPrefs.confirmBeforeAppCall(this)
        ) { AssistantPrefs.setConfirmBeforeAppCall(this, it) }

        switchConfirmSend = addToggleRow(
            card,
            "মেসেঞ্জার Send করার আগে Confirm চাইবে",
            "WhatsApp / imo / Messenger মেসেজ পাঠানোর আগে নিশ্চিত করবে",
            AssistantPrefs.confirmBeforeHumanSend(this)
        ) { AssistantPrefs.setConfirmBeforeHumanSend(this, it) }

        switchConfirmAiSend = addToggleRow(
            card,
            "AI Chat (Claude/ChatGPT/Gemini) Send Confirm",
            "চালু করলে AI চ্যাটে প্রম্পট সেন্ড করার আগেও Confirm চাইবে",
            AssistantPrefs.confirmBeforeAiChatSend(this)
        ) { AssistantPrefs.setConfirmBeforeAiChatSend(this, it) }

        timeoutLabel = TextView(this).apply {
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.text)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(4)
            }
        }
        card.addView(timeoutLabel)

        timeoutSeek = SeekBar(this).apply {
            max = AssistantPrefs.MAX_FLOW_TIMEOUT_SEC - AssistantPrefs.MIN_FLOW_TIMEOUT_SEC
            progress = AssistantPrefs.appFlowTimeoutSec(this@AssistantActivity) - AssistantPrefs.MIN_FLOW_TIMEOUT_SEC
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val sec = AssistantPrefs.MIN_FLOW_TIMEOUT_SEC + p
                    if (fromUser) AssistantPrefs.setAppFlowTimeoutSec(this@AssistantActivity, sec)
                    timeoutLabel.text = "App Flow Timeout: $sec সেকেন্ড"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        card.addView(timeoutSeek)
        return card
    }

    private fun buildGeminiAndTestCard(): View {
        val card = makeCard()
        addCardHeader(
            card,
            AssistantIcons.Kind.SPARKLE,
            "Gemini স্মার্ট Fallback ও টেস্ট",
            "জটিল বাংলা/Banglish কমান্ড স্থানীয় parser না বুঝলে Gemini সাহায্য করবে"
        )

        geminiStatusText = TextView(this).apply {
            textSize = 12.5f
            setTextColor(pal.subText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        card.addView(geminiStatusText)

        geminiKeyInput = EditText(this).apply {
            hint = "Gemini API Key (AIza...)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(GeminiConfig.getUserKey(this@AssistantActivity))
            setTextColor(pal.text)
            setHintTextColor(pal.subText)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBg(pal.bg, 12, pal.border)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        card.addView(geminiKeyInput)

        val keyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        val saveKeyBtn = buildIconButton(AssistantIcons.Kind.CHECK_CIRCLE, "Key সেভ", Color.WHITE, pal.primary) {
            val k = geminiKeyInput.text.toString().trim()
            GeminiConfig.setKey(this, k)
            refresh()
            toast(if (k.isEmpty()) "User key মুছে ফেলা হয়েছে" else "Gemini Key সেভ হয়েছে")
        }.first
        keyRow.addView(saveKeyBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        val clearKeyBtn = buildIconButton(AssistantIcons.Kind.CLOSE, "মুছুন", pal.danger, pal.dangerSoft) {
            geminiKeyInput.setText("")
            GeminiConfig.setKey(this, "")
            refresh()
            toast("User key মুছে ফেলা হয়েছে")
        }.first
        keyRow.addView(clearKeyBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })
        card.addView(keyRow)

        val testBtn = buildIconButton(
            AssistantIcons.Kind.SWIPE_UP,
            "টেস্ট: ৩ সেকেন্ড পর 'Up' Swipe (অন্য অ্যাপ খুলুন)",
            pal.primary,
            pal.primarySoft
        ) {
            toast("৩ সেকেন্ডের মধ্যে TikTok বা Chrome-এ যান…")
            Handler(Looper.getMainLooper()).postDelayed({
                val msg = CommandExecutor(applicationContext).execute(Command.Swipe(Dir.UP))
                toast(msg)
            }, 3000)
        }.first
        card.addView(testBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return card
    }

    private fun addToggleRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        initial: Boolean,
        onToggle: (Boolean) -> Unit
    ): Switch {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(0, dp(4), 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(10)
            }
            addView(TextView(this@AssistantActivity).apply {
                text = title
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.text)
            })
            addView(TextView(this@AssistantActivity).apply {
                text = subtitle
                textSize = 12f
                setTextColor(pal.subText)
            })
        }
        val sw = Switch(this).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        row.setOnClickListener { sw.toggle() }
        row.addView(col)
        row.addView(sw)
        parent.addView(row)
        return sw
    }

    // ------------------------------------------------------------ Refresh State

    private fun refresh() {
        val isRun = AssistantListenerService.running
        val isReady = AssistantSetup.isReady(this)
        val a11y = AssistantSetup.isAccessibilityOn(this)
        val mic = AssistantSetup.hasMic(this)
        val contacts = AssistantSetup.has(this, android.Manifest.permission.READ_CONTACTS) &&
            AssistantSetup.has(this, android.Manifest.permission.CALL_PHONE)
        val overlay = AssistantSetup.canOverlay(this)

        // Status pill
        when {
            isRun -> {
                statusPillBg.background = roundedBg(pal.okSoft, 99)
                statusPillIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.CHECK_CIRCLE, pal.ok, 15))
                statusPillText.setTextColor(pal.ok)
                statusPillText.text = "চালু আছে"
            }
            isReady -> {
                statusPillBg.background = roundedBg(pal.primarySoft, 99)
                statusPillIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.BOT, pal.primary, 15))
                statusPillText.setTextColor(pal.primary)
                statusPillText.text = "প্রস্তুত"
            }
            else -> {
                statusPillBg.background = roundedBg(pal.warnSoft, 99)
                statusPillIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.ALERT_CIRCLE, pal.warn, 15))
                statusPillText.setTextColor(pal.warn)
                statusPillText.text = "সেটআপ বাকি"
            }
        }

        // Service button
        if (isRun) {
            rippleCard(btnService, pal.dangerSoft, 14)
            btnServiceIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.STOP, pal.danger, 18))
            btnServiceText.setTextColor(pal.danger)
            btnServiceText.text = "Assistant বন্ধ করুন"
        } else {
            rippleCard(btnService, pal.primary, 14)
            btnServiceIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.PLAY, Color.WHITE, 18))
            btnServiceText.setTextColor(Color.WHITE)
            btnServiceText.text = "Assistant চালু করুন"
        }

        val isBangla = AssistantSetup.isBangla(this)
        btnLangText.text = "শোনার ভাষা: " + if (isBangla) "বাংলা (bn-BD)" else "English (en-US)"

        // Mode chips
        val mode = AssistantPrefs.listeningMode(this)
        if (mode == ListeningMode.CONTINUOUS) {
            rippleCard(chipContinuous, pal.primarySoft, 12, pal.primary)
            chipContinuousText.setTextColor(pal.primary)
            rippleCard(chipPtt, pal.bg, 12, pal.border)
            chipPttText.setTextColor(pal.subText)
        } else {
            rippleCard(chipContinuous, pal.bg, 12, pal.border)
            chipContinuousText.setTextColor(pal.subText)
            rippleCard(chipPtt, pal.primarySoft, 12, pal.primary)
            chipPttText.setTextColor(pal.primary)
        }

        // Checklist progress (out of 5 including overlay & SAF)
        val safOk = AssistantSetup.hasStorageTree(this)
        val states = listOf(a11y, mic, contacts, overlay, a11y)
        val doneCount = listOf(a11y, mic, contacts, overlay, safOk).count { it }
        checklistProgress.text = "$doneCount/5 সম্পন্ন"
        checklistProgress.background = roundedBg(if (doneCount == 5) pal.okSoft else pal.primarySoft, 99)
        checklistProgress.setTextColor(if (doneCount == 5) pal.ok else pal.primary)

        for (idx in checkRows.indices) {
            val (iv, _, badge) = checkRows[idx]
            val ok = states.getOrNull(idx) == true
            if (idx < 4) {
                iv.setImageDrawable(
                    AssistantIcons.drawable(
                        this,
                        if (ok) AssistantIcons.Kind.CHECK_CIRCLE else AssistantIcons.Kind.ALERT_CIRCLE,
                        if (ok) pal.ok else pal.warn,
                        18
                    )
                )
                badge.text = if (ok) "চালু" else "ঠিক করুন"
                badge.setTextColor(if (ok) pal.ok else pal.warn)
            }
        }
        safStatusText.text = if (safOk) {
            "Storage Folder সেট করা আছে (ফাইল ও ফোল্ডার তৈরি চালু)"
        } else {
            "ফাইল/ফোল্ডার তৈরির কমান্ডের জন্য একটি Storage Folder বেছে নিন"
        }

        // Side-Edge & Ad Skipper
        switchOverlay.isChecked = AssistantPrefs.overlayHudEnabled(this)
        val edgeLabel = if (AssistantPrefs.overlayEdge(this) == OverlayEdge.LEFT) "বাম কিনারা (Left)" else "ডান কিনারা (Right)"
        val pct = (AssistantPrefs.overlayYRatio(this) * 100).toInt()
        overlayEdgeText.text = "বর্তমান অবস্থান: $edgeLabel · উচ্চতা $pct%"

        switchAdSkip.isChecked = AssistantPrefs.adSkipEnabled(this)
        switchAdSkipGames.isChecked = AssistantPrefs.adSkipGamesEnabled(this)
        val skipped = AssistantPrefs.adSkipCount(this)
        val last = AssistantPrefs.lastAdSkipInfo(this)
        adSkipStatsText.text = if (skipped > 0) {
            "মোট স্কিপ করা বিজ্ঞাপন: $skipped টি" + if (last != null) " (সর্বশেষ: $last)" else ""
        } else {
            "এখনো কোনো বিজ্ঞাপন স্কিপ হয়নি — সক্রিয় আছে"
        }

        // Safety & Timeout
        switchConfirmCall.isChecked = AssistantPrefs.confirmBeforeAppCall(this)
        switchConfirmSend.isChecked = AssistantPrefs.confirmBeforeHumanSend(this)
        switchConfirmAiSend.isChecked = AssistantPrefs.confirmBeforeAiChatSend(this)
        val sec = AssistantPrefs.appFlowTimeoutSec(this)
        timeoutLabel.text = "App Flow Timeout: $sec সেকেন্ড"
        timeoutSeek.progress = sec - AssistantPrefs.MIN_FLOW_TIMEOUT_SEC

        // Gemini status
        geminiStatusText.text = when (GeminiConfig.keySource(this)) {
            GeminiConfig.KeySource.USER_SETTINGS -> "স্ট্যাটাস: আপনার সেভ করা Gemini API Key সক্রিয়"
            GeminiConfig.KeySource.BUILD_CONFIG -> "স্ট্যাটাস: বিল্ট-ইন Secrets Key সক্রিয়"
            GeminiConfig.KeySource.NONE -> "স্ট্যাটাস: Key সেট নেই (চাইলে নিচে দিন, নইলে লোকাল কমান্ড চলবে)"
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
