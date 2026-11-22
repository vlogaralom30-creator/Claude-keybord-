package com.banglakeys.app

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity

/**
 * Setup + control screen (XML layout ছাড়া, pure Views + code-built vector icons)।
 * এখান থেকে: Accessibility, Runtime Permissions, Overlay, SAF storage folder, Controls ও Quick Test।
 */
class AssistantActivity : Activity() {

    companion object {
        private const val REQ_STORAGE_TREE = 202
    }

    private data class Palette(
        val bg: Int,
        val card: Int,
        val cardAlt: Int,
        val border: Int,
        val text: Int,
        val subText: Int,
        val primary: Int,
        val primarySoft: Int,
        val onPrimary: Int,
        val ok: Int,
        val okSoft: Int,
        val warn: Int,
        val warnSoft: Int,
        val danger: Int,
        val dangerSoft: Int
    )

    private data class StatusRowViews(
        val iconView: ImageView,
        val subtitleView: TextView,
        val actionBtn: TextView?
    )

    private lateinit var pal: Palette

    // Hero & quick controls
    private lateinit var heroStatusIcon: ImageView
    private lateinit var heroStatusText: TextView
    private lateinit var btnServiceContainer: LinearLayout
    private lateinit var btnServiceIcon: ImageView
    private lateinit var btnServiceLabel: TextView
    private lateinit var btnLangLabel: TextView
    private lateinit var checklistSummaryBadge: TextView

    // Checklist rows
    private lateinit var rowA11y: StatusRowViews
    private lateinit var rowPerms: StatusRowViews
    private lateinit var rowOverlay: StatusRowViews
    private lateinit var rowAlarm: StatusRowViews
    private lateinit var rowStorage: StatusRowViews

    // Storage folder control + listening mode + switches + seekbar
    private lateinit var btnModeContinuous: LinearLayout
    private lateinit var btnModeContinuousIcon: ImageView
    private lateinit var btnModeContinuousLabel: TextView
    private lateinit var btnModePtt: LinearLayout
    private lateinit var btnModePttIcon: ImageView
    private lateinit var btnModePttLabel: TextView
    private lateinit var modeHintText: TextView

    private lateinit var btnStorageLabel: TextView
    private lateinit var btnStorageIcon: ImageView
    private lateinit var swOverlayHud: Switch
    private lateinit var swOverlaySub: TextView
    private lateinit var swConfirmAppCall: Switch
    private lateinit var swConfirmHumanSend: Switch
    private lateinit var swConfirmAiChatSend: Switch
    private lateinit var timeoutBadge: TextView
    private lateinit var timeoutSeek: SeekBar

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun palette(): Palette {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) {
            Palette(
                bg = 0xFF101414.toInt(),
                card = 0xFF1A2020.toInt(),
                cardAlt = 0xFF232C2B.toInt(),
                border = 0xFF2E3B39.toInt(),
                text = 0xFFE0E5E4.toInt(),
                subText = 0xFF9FB2AF.toInt(),
                primary = 0xFF4DB6AC.toInt(),
                primarySoft = 0xFF004D45.toInt(),
                onPrimary = 0xFF002B26.toInt(),
                ok = 0xFF4ADE80.toInt(),
                okSoft = 0xFF133622.toInt(),
                warn = 0xFFFBBF24.toInt(),
                warnSoft = 0xFF3A2D0B.toInt(),
                danger = 0xFFF87171.toInt(),
                dangerSoft = 0xFF3E1818.toInt()
            )
        } else {
            Palette(
                bg = 0xFFF6FAF9.toInt(),
                card = 0xFFFFFFFF.toInt(),
                cardAlt = 0xFFE9F2F0.toInt(),
                border = 0xFFD4E3E0.toInt(),
                text = 0xFF161D1C.toInt(),
                subText = 0xFF536663.toInt(),
                primary = 0xFF00796B.toInt(),
                primarySoft = 0xFFD6F2EE.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                ok = 0xFF15803D.toInt(),
                okSoft = 0xFFDCFCE7.toInt(),
                warn = 0xFFB45309.toInt(),
                warnSoft = 0xFFFEF3C7.toInt(),
                danger = 0xFFDC2626.toInt(),
                dangerSoft = 0xFFFEE2E2.toInt()
            )
        }
    }

    private fun roundBg(fill: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeDp > 0 && strokeColor != 0) setStroke(dp(strokeDp), strokeColor)
        }

    private fun rippleSelectable(view: View) {
        val out = TypedValue()
        if (theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)) {
            if (Build.VERSION.SDK_INT >= 23) {
                view.foreground = getDrawable(out.resourceId)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        pal = palette()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(24))
            setBackgroundColor(pal.bg)
        }

        // ------------------------------------------------------------ 1. Hero Header Card
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF015E57.toInt(), 0xFF00796B.toInt(), 0xFF00897B.toInt())
            ).apply {
                cornerRadius = dp(22).toFloat()
            }
        }

        val heroTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heroAvatar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundBg(0x2EFFFFFF, 14)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            addView(ImageView(this@AssistantActivity).apply {
                setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, AssistantIcons.Kind.BOT, Color.WHITE, 26))
                contentDescription = "BanglaKeys Assistant icon"
            })
        }
        heroTopRow.addView(heroAvatar)

        val heroTitles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            }
            addView(TextView(this@AssistantActivity).apply {
                text = "BanglaKeys Assistant"
                textSize = 19f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@AssistantActivity).apply {
                text = "ভয়েস কমান্ড ও অ্যাসিস্ট্যান্ট সেটআপ"
                textSize = 12.5f
                setTextColor(0xDEFFFFFF.toInt())
            })
        }
        heroTopRow.addView(heroTitles)

        val backBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundBg(0x26FFFFFF, 22)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            contentDescription = "Keyboard settings-এ ফিরুন"
            rippleSelectable(this)
            setOnClickListener { returnToMain() }
            addView(ImageView(this@AssistantActivity).apply {
                setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, AssistantIcons.Kind.BACK, Color.WHITE, 20))
            })
        }
        heroTopRow.addView(backBtn)
        heroCard.addView(heroTopRow)

        val heroStatusPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(14), dp(6))
            background = roundBg(0x28FFFFFF, 99)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        heroStatusIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(6) }
        }
        heroStatusText = TextView(this).apply {
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        heroStatusPill.addView(heroStatusIcon)
        heroStatusPill.addView(heroStatusText)
        heroCard.addView(heroStatusPill)
        root.addView(heroCard)

        // ------------------------------------------------------------ 2. Quick Control Card
        val quickCard = createCard().apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(14)
        }

        btnServiceContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(50)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            rippleSelectable(this)
            setOnClickListener {
                if (AssistantListenerService.running) {
                    AssistantListenerService.stop(this@AssistantActivity)
                } else if (!AssistantSetup.isReady(this@AssistantActivity)) {
                    Toast.makeText(
                        this@AssistantActivity,
                        "আগে নিচে থেকে Accessibility ও Microphone চালু করুন",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    AssistantListenerService.start(this@AssistantActivity)
                }
                Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 400)
            }
        }
        btnServiceIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) }
        }
        btnServiceLabel = TextView(this).apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        }
        btnServiceContainer.addView(btnServiceIcon)
        btnServiceContainer.addView(btnServiceLabel)
        quickCard.addView(btnServiceContainer)

        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val btnLangContainer = buildCompactPillButton(
            kind = AssistantIcons.Kind.GLOBE,
            labelInit = "",
            onClick = {
                AssistantSetup.setBangla(this, !AssistantSetup.isBangla(this))
                refresh()
                Toast.makeText(this, "ভাষা বদলাতে Assistant বন্ধ করে আবার চালু করুন", Toast.LENGTH_SHORT).show()
            }
        ) { tv -> btnLangLabel = tv }
        quickRow.addView(btnLangContainer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        val btnTestContainer = buildCompactPillButton(
            kind = AssistantIcons.Kind.SWIPE_UP,
            labelInit = "৩ সেকেন্ড Swipe টেস্ট",
            onClick = {
                Toast.makeText(this, "৩ সেকেন্ডে অন্য অ্যাপে যান…", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    val msg = CommandExecutor(applicationContext).execute(Command.Swipe(Dir.UP))
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }, 3000)
            }
        ) {}
        quickRow.addView(btnTestContainer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })
        quickCard.addView(quickRow)

        // Listening Mode segmented toggle (Continuous vs Quiet Push-to-Talk)
        quickCard.addView(dividerView().apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
        })
        quickCard.addView(TextView(this).apply {
            text = "শোনার ধরন (Listening Mode)"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.text)
        })
        modeHintText = TextView(this).apply {
            textSize = 12f
            setTextColor(pal.subText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2); bottomMargin = dp(8) }
        }
        quickCard.addView(modeHintText)

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btnModeContinuous = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            rippleSelectable(this)
            setOnClickListener {
                AssistantPrefs.setListeningMode(this@AssistantActivity, ListeningMode.CONTINUOUS)
                AssistantListenerService.notifyConfigChanged(this@AssistantActivity)
                refresh()
            }
        }
        btnModeContinuousIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(6) }
        }
        btnModeContinuousLabel = TextView(this).apply {
            text = "Continuous"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
        }
        btnModeContinuous.addView(btnModeContinuousIcon)
        btnModeContinuous.addView(btnModeContinuousLabel)
        modeRow.addView(btnModeContinuous, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        btnModePtt = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            rippleSelectable(this)
            setOnClickListener {
                AssistantPrefs.setListeningMode(this@AssistantActivity, ListeningMode.PUSH_TO_TALK)
                AssistantListenerService.notifyConfigChanged(this@AssistantActivity)
                refresh()
            }
        }
        btnModePttIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(6) }
        }
        btnModePttLabel = TextView(this).apply {
            text = "Quiet PTT"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
        }
        btnModePtt.addView(btnModePttIcon)
        btnModePtt.addView(btnModePttLabel)
        modeRow.addView(btnModePtt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })
        quickCard.addView(modeRow)
        root.addView(quickCard)

        // ------------------------------------------------------------ 3. Permissions & Status Checklist Card
        val statusCard = createCard().apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(14)
        }
        val statusHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        statusHeader.addView(ImageView(this).apply {
            setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, AssistantIcons.Kind.SHIELD, pal.primary, 20))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) }
        })
        statusHeader.addView(TextView(this).apply {
            text = "পারমিশন ও সিস্টেম স্ট্যাটাস"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.text)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        checklistSummaryBadge = TextView(this).apply {
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        statusHeader.addView(checklistSummaryBadge)
        statusCard.addView(statusHeader)

        rowA11y = addChecklistRow(
            parent = statusCard,
            title = "Accessibility সার্ভিস",
            actionText = "সেটিংস",
            onAction = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        )

        // Android 13+ Restricted setting helper link
        val restrictedHelper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundBg(pal.cardAlt, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(6) }
            rippleSelectable(this)
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
            addView(ImageView(this@AssistantActivity).apply {
                setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, AssistantIcons.Kind.APP_INFO, pal.primary, 16))
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) }
            })
            addView(TextView(this@AssistantActivity).apply {
                text = "Android 13+: 'Restricted setting' দেখালে App info খুলে Allow করুন"
                textSize = 12f
                setTextColor(pal.primary)
            })
        }
        statusCard.addView(restrictedHelper)

        statusCard.addView(dividerView())
        rowPerms = addChecklistRow(
            parent = statusCard,
            title = "Mic, Contacts ও Call অনুমোদন",
            actionText = "অনুমতি দিন",
            onAction = { requestPermissions(AssistantSetup.runtimePermissions(), 201) }
        )

        statusCard.addView(dividerView())
        rowOverlay = addChecklistRow(
            parent = statusCard,
            title = "Display over other apps",
            actionText = "সেটিংস",
            onAction = {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
        )

        statusCard.addView(dividerView())
        rowAlarm = addChecklistRow(
            parent = statusCard,
            title = "Alarm ও Timer (SET_ALARM)",
            actionText = null,
            onAction = null
        )

        statusCard.addView(dividerView())
        rowStorage = addChecklistRow(
            parent = statusCard,
            title = "Assistant Storage Folder (SAF)",
            actionText = "বাছুন",
            onAction = { launchStorageTreePicker() }
        )

        root.addView(statusCard)

        // ------------------------------------------------------------ 4. Controls & Safety Card
        val controlsCard = createCard().apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(14)
        }
        val controlsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        controlsHeader.addView(ImageView(this).apply {
            setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, AssistantIcons.Kind.LOCK, pal.primary, 20))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) }
        })
        controlsHeader.addView(TextView(this).apply {
            text = "কনফার্মেশন ও স্টোরেজ নিয়ন্ত্রণ"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.text)
        })
        controlsCard.addView(controlsHeader)

        // Storage folder selector box
        val folderBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundBg(pal.cardAlt, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        folderBox.addView(TextView(this).apply {
            text = "ফাইল ও ফোল্ডার তৈরির কমান্ড চালানোর জন্য আপনাকে নিজে ট্যাপ করে একটি ফোল্ডার বেছে দিতে হবে:"
            textSize = 12.5f
            setTextColor(pal.subText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        })
        val folderBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(46)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundBg(pal.primarySoft, 12, pal.primary, 1)
            rippleSelectable(this)
            setOnClickListener { launchStorageTreePicker() }
        }
        btnStorageIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        }
        btnStorageLabel = TextView(this).apply {
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.primary)
        }
        folderBtnRow.addView(btnStorageIcon)
        folderBtnRow.addView(btnStorageLabel)
        folderBox.addView(folderBtnRow)
        controlsCard.addView(folderBox)

        // Switches
        swOverlayHud = addSwitchRow(
            parent = controlsCard,
            iconKind = AssistantIcons.Kind.BOT,
            title = "Live Command Overlay Pill",
            subtitle = "স্ক্রিনের উপরে লাইভ শোনা কথা, কমান্ড ও হ্যাঁ/না বাটন দেখাবে",
            initial = AssistantPrefs.overlayHudEnabled(this),
            bindSubtitle = { swOverlaySub = it }
        ) { checked ->
            AssistantPrefs.setOverlayHudEnabled(this, checked)
            AssistantListenerService.notifyConfigChanged(this)
            refresh()
        }

        controlsCard.addView(dividerView())
        swConfirmAppCall = addSwitchRow(
            parent = controlsCard,
            iconKind = AssistantIcons.Kind.LOCK,
            title = "App call-এর আগে confirm",
            subtitle = "imo, WhatsApp বা Messenger কলের আগে হ্যাঁ/না চাইবে",
            initial = AssistantPrefs.confirmBeforeAppCall(this)
        ) { AssistantPrefs.setConfirmBeforeAppCall(this, it) }

        controlsCard.addView(dividerView())
        swConfirmHumanSend = addSwitchRow(
            parent = controlsCard,
            iconKind = AssistantIcons.Kind.MESSAGE,
            title = "মেসেজ পাঠানোর আগে confirm",
            subtitle = "WhatsApp, imo বা Messenger-এ মেসেজ সেন্ড করার আগে",
            initial = AssistantPrefs.confirmBeforeHumanSend(this)
        ) { AssistantPrefs.setConfirmBeforeHumanSend(this, it) }

        controlsCard.addView(dividerView())
        swConfirmAiChatSend = addSwitchRow(
            parent = controlsCard,
            iconKind = AssistantIcons.Kind.SPARKLE,
            title = "AI chat send-এর আগে confirm",
            subtitle = "Claude, ChatGPT বা Gemini-তে পাঠানোর আগে জিজ্ঞাসা করবে",
            initial = AssistantPrefs.confirmBeforeAiChatSend(this)
        ) { AssistantPrefs.setConfirmBeforeAiChatSend(this, it) }

        controlsCard.addView(dividerView())

        // Timeout SeekBar section
        val timeoutHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10); bottomMargin = dp(6) }
        }
        timeoutHeader.addView(ImageView(this).apply {
            setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, AssistantIcons.Kind.TIMER, pal.primary, 18))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        })
        timeoutHeader.addView(TextView(this).apply {
            text = "App-flow অপেক্ষার সময়"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.text)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        timeoutBadge = TextView(this).apply {
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.primary)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundBg(pal.primarySoft, 8)
        }
        timeoutHeader.addView(timeoutBadge)
        controlsCard.addView(timeoutHeader)

        timeoutSeek = SeekBar(this).apply {
            max = AssistantPrefs.MAX_FLOW_TIMEOUT_SEC - AssistantPrefs.MIN_FLOW_TIMEOUT_SEC
            progress = AssistantPrefs.appFlowTimeoutSec(this@AssistantActivity) - AssistantPrefs.MIN_FLOW_TIMEOUT_SEC
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val sec = AssistantPrefs.MIN_FLOW_TIMEOUT_SEC + progress
                    timeoutBadge.text = "$sec সেকেন্ড"
                    if (fromUser) AssistantPrefs.setAppFlowTimeoutSec(this@AssistantActivity, sec)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        controlsCard.addView(timeoutSeek)
        root.addView(controlsCard)

        // ------------------------------------------------------------ 5. Back to Keyboard & Settings Button
        val returnBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(50)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = roundBg(pal.card, 16, pal.border, 1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
            rippleSelectable(this)
            setOnClickListener { returnToMain() }
            addView(ImageView(this@AssistantActivity).apply {
                setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, AssistantIcons.Kind.KEYBOARD, pal.primary, 20))
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(10) }
            })
            addView(TextView(this@AssistantActivity).apply {
                text = "Bangla Keys Keyboard ও Settings-এ ফিরুন"
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.primary)
            })
        }
        root.addView(returnBtn)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            addView(root)
            if (Build.VERSION.SDK_INT >= 30) {
                setOnApplyWindowInsetsListener { v, insets ->
                    val sys = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
                    insets
                }
            } else {
                fitsSystemWindows = true
            }
        }
        setContentView(scroll)
    }

    private fun createCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = roundBg(pal.card, 20, pal.border, 1)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dividerView(): View = View(this).apply {
        setBackgroundColor(pal.border)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            1
        ).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        }
    }

    private fun buildCompactPillButton(
        kind: AssistantIcons.Kind,
        labelInit: String,
        onClick: () -> Unit,
        bindText: (TextView) -> Unit
    ): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(46)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundBg(pal.cardAlt, 14)
            rippleSelectable(this)
            setOnClickListener { onClick() }
        }
        box.addView(ImageView(this).apply {
            setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, kind, pal.primary, 18))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(6) }
        })
        val tv = TextView(this).apply {
            text = labelInit
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.text)
            maxLines = 1
        }
        bindText(tv)
        box.addView(tv)
        return box
    }

    private fun addChecklistRow(
        parent: LinearLayout,
        title: String,
        actionText: String?,
        onAction: (() -> Unit)?
    ): StatusRowViews {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(12) }
        }
        row.addView(icon)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            addView(TextView(this@AssistantActivity).apply {
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.text)
            })
        }
        val sub = TextView(this).apply {
            textSize = 12f
            setTextColor(pal.subText)
        }
        textCol.addView(sub)
        row.addView(textCol)

        var btn: TextView? = null
        if (actionText != null && onAction != null) {
            btn = TextView(this).apply {
                text = actionText
                textSize = 12.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.primary)
                gravity = Gravity.CENTER
                minHeight = dp(38)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = roundBg(pal.primarySoft, 10)
                rippleSelectable(this)
                setOnClickListener { onAction() }
            }
            row.addView(btn)
        }

        parent.addView(row)
        return StatusRowViews(icon, sub, btn)
    }

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun addSwitchRow(
        parent: LinearLayout,
        iconKind: AssistantIcons.Kind,
        title: String,
        subtitle: String,
        initial: Boolean,
        bindSubtitle: ((TextView) -> Unit)? = null,
        onChange: (Boolean) -> Unit
    ): Switch {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, iconKind, pal.primary, 20))
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(12) }
        })
        val subTv = TextView(this@AssistantActivity).apply {
            text = subtitle
            textSize = 12f
            setTextColor(pal.subText)
        }
        bindSubtitle?.invoke(subTv)
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(12)
            }
            addView(TextView(this@AssistantActivity).apply {
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.text)
            })
            addView(subTv)
        })
        val sw = Switch(this).apply {
            isChecked = initial
            minWidth = dp(48)
            minHeight = dp(48)
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        row.addView(sw)
        parent.addView(row)
        return sw
    }

    @Suppress("DEPRECATION")
    private fun launchStorageTreePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        try {
            startActivityForResult(intent, REQ_STORAGE_TREE)
        } catch (e: Exception) {
            AssistantLog.w("OPEN_DOCUMENT_TREE failed", e)
            Toast.makeText(this, "Storage folder picker খোলা যায়নি", Toast.LENGTH_SHORT).show()
        }
    }

    private fun returnToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
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

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_STORAGE_TREE && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    AssistantLog.w("takePersistableUriPermission failed", e)
                }
                AssistantPrefs.setStorageTreeUri(this, uri.toString())
                Toast.makeText(this, "Assistant storage folder সেভ হয়েছে", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    private fun applyStatusIcon(view: ImageView, ok: Boolean) {
        val kind = if (ok) AssistantIcons.Kind.CHECK_CIRCLE else AssistantIcons.Kind.ALERT_CIRCLE
        val tint = if (ok) pal.ok else pal.danger
        view.setImageDrawable(AssistantIcons.drawable(this, kind, tint, 22))
    }

    private fun refresh() {
        val a11yOn = AssistantSetup.isAccessibilityOn(this)
        val a11yConnected = AssistantAccessibilityService.instance != null
        val micOk = AssistantSetup.hasMic(this)
        val contactsOk = AssistantSetup.has(this, android.Manifest.permission.READ_CONTACTS)
        val callOk = AssistantSetup.has(this, android.Manifest.permission.CALL_PHONE)
        val overlayOk = AssistantSetup.canOverlay(this)
        val alarmOk = AssistantSetup.hasSetAlarm(this)
        val storageOk = AssistantSetup.hasStorageTree(this)
        val listening = AssistantListenerService.running

        val mode = AssistantPrefs.listeningMode(this)
        val isPtt = mode == ListeningMode.PUSH_TO_TALK

        // Hero status pill
        when {
            listening && isPtt -> {
                heroStatusIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.BOT, 0xFF86EFAC.toInt(), 16))
                heroStatusText.text = "Quiet PTT প্রস্তুত (ট্যাপ করে কথা বলুন)"
            }
            listening -> {
                heroStatusIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.CHECK_CIRCLE, 0xFF86EFAC.toInt(), 16))
                heroStatusText.text = "Assistant এখন শুনছে (Continuous)"
            }
            AssistantSetup.isReady(this) -> {
                heroStatusIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.PLAY, Color.WHITE, 14))
                heroStatusText.text = "চালু করার জন্য প্রস্তুত"
            }
            else -> {
                heroStatusIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.ALERT_CIRCLE, 0xFFFDE68A.toInt(), 16))
                heroStatusText.text = "সেটআপ বাকি আছে (নিচে দেখুন)"
            }
        }

        // Primary start/stop button
        if (listening) {
            btnServiceContainer.background = roundBg(pal.dangerSoft, 14, pal.danger, 1)
            btnServiceIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.STOP, pal.danger, 18))
            btnServiceLabel.setTextColor(pal.danger)
            btnServiceLabel.text = "Assistant বন্ধ করুন"
        } else {
            btnServiceContainer.background = roundBg(pal.primary, 14)
            btnServiceIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.PLAY, pal.onPrimary, 18))
            btnServiceLabel.setTextColor(pal.onPrimary)
            btnServiceLabel.text = "Assistant চালু করুন"
        }

        btnLangLabel.text = if (AssistantSetup.isBangla(this)) "বাংলা (bn-BD)" else "English (en-US)"

        // Listening Mode pill states
        if (!isPtt) {
            btnModeContinuous.background = roundBg(pal.primarySoft, 14, pal.primary, 1)
            btnModeContinuousIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.MIC, pal.primary, 18))
            btnModeContinuousLabel.setTextColor(pal.primary)

            btnModePtt.background = roundBg(pal.cardAlt, 14)
            btnModePttIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.BOT, pal.subText, 18))
            btnModePttLabel.setTextColor(pal.subText)

            modeHintText.text = "সবসময় শুনবে — \"Hey Bot\" বলে বা সক্রিয় থাকা অবস্থায় সরাসরি কমান্ড দিন"
        } else {
            btnModeContinuous.background = roundBg(pal.cardAlt, 14)
            btnModeContinuousIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.MIC, pal.subText, 18))
            btnModeContinuousLabel.setTextColor(pal.subText)

            btnModePtt.background = roundBg(pal.primarySoft, 14, pal.primary, 1)
            btnModePttIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.BOT, pal.primary, 18))
            btnModePttLabel.setTextColor(pal.primary)

            modeHintText.text = "নিঃশব্দ মোড — কোনো বিরক্তিকর বিপ নেই; উপরের ভাসমান পিল বা BOT কী ট্যাপ করে বলুন"
        }

        // Checklist rows
        applyStatusIcon(rowA11y.iconView, a11yOn)
        rowA11y.subtitleView.text = when {
            a11yOn && a11yConnected -> "চালু ও সংযুক্ত (কাজের জন্য প্রস্তুত)"
            a11yOn -> "চালু আছে (সংযুক্ত নয় — পুনরায় টগল করতে পারেন)"
            else -> "ভয়েস দিয়ে scroll, click ও টাইপ করতে চালু করুন"
        }

        val permsOk = micOk && contactsOk && callOk
        applyStatusIcon(rowPerms.iconView, micOk)
        rowPerms.subtitleView.text =
            "Mic: ${if (micOk) "OK" else "নেই"} • Contacts: ${if (contactsOk) "OK" else "নেই"} • Call: ${if (callOk) "OK" else "নেই"}"
        rowPerms.actionBtn?.text = if (permsOk) "আপডেট" else "অনুমতি দিন"

        applyStatusIcon(rowOverlay.iconView, overlayOk)
        rowOverlay.subtitleView.text =
            if (overlayOk) "অনুমোদিত (ব্যাকগ্রাউন্ড থেকে অ্যাপ খুলতে পারবে)"
            else "ব্যাকগ্রাউন্ড থেকে অ্যাপ খোলার জন্য প্রয়োজন"

        applyStatusIcon(rowAlarm.iconView, alarmOk)
        rowAlarm.subtitleView.text =
            if (alarmOk) "সক্রিয় (Alarm ও Timer কমান্ড কাজ করবে)"
            else "Manifest-এ SET_ALARM অনুপস্থিত"

        applyStatusIcon(rowStorage.iconView, storageOk)
        rowStorage.subtitleView.text =
            if (storageOk) "নির্বাচিত (ফাইল ও ফোল্ডার তৈরি হবে)"
            else "এখনো বাছা হয়নি — ট্যাপ করে বাছুন"

        val readyCount = listOf(a11yOn, micOk, overlayOk, alarmOk, storageOk).count { it }
        checklistSummaryBadge.text = "$readyCount/5 প্রস্তুত"
        if (readyCount == 5) {
            checklistSummaryBadge.setTextColor(pal.ok)
            checklistSummaryBadge.background = roundBg(pal.okSoft, 8)
        } else {
            checklistSummaryBadge.setTextColor(pal.warn)
            checklistSummaryBadge.background = roundBg(pal.warnSoft, 8)
        }

        // Storage button inside Controls
        if (storageOk) {
            btnStorageIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.CHECK_CIRCLE, pal.ok, 18))
            btnStorageLabel.text = "Storage folder পরিবর্তন করুন (নির্বাচিত)"
        } else {
            btnStorageIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.FOLDER, pal.primary, 18))
            btnStorageLabel.text = "Assistant storage folder বেছে নিন"
        }

        swOverlayHud.isChecked = AssistantPrefs.overlayHudEnabled(this)
        swOverlaySub.text = if (overlayOk) {
            "স্ক্রিনের উপরে লাইভ শোনা কথা, কমান্ড ও হ্যাঁ/না বাটন দেখাবে"
        } else {
            "ভাসমান কমান্ড পিল দেখতে উপরের 'Display over other apps' অনুমতি দিন"
        }
        swConfirmAppCall.isChecked = AssistantPrefs.confirmBeforeAppCall(this)
        swConfirmHumanSend.isChecked = AssistantPrefs.confirmBeforeHumanSend(this)
        swConfirmAiChatSend.isChecked = AssistantPrefs.confirmBeforeAiChatSend(this)

        val sec = AssistantPrefs.appFlowTimeoutSec(this)
        timeoutBadge.text = "$sec সেকেন্ড"
        val expectedProg = sec - AssistantPrefs.MIN_FLOW_TIMEOUT_SEC
        if (timeoutSeek.progress != expectedProg) timeoutSeek.progress = expectedProg
    }
}
