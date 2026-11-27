package com.banglakeys.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.banglakeys.app.robot.EmoBodyActivity
import com.banglakeys.app.robot.EmoBodySensorController
import com.banglakeys.app.robot.EmoBrainHub
import com.banglakeys.app.robot.EmoLearningActivity
import com.banglakeys.app.robot.EmoLearningStore
import com.banglakeys.app.robot.EmoLiveReactor
import com.banglakeys.app.robot.FaceId
import com.banglakeys.app.robot.FaceRenderer
import com.banglakeys.app.robot.RobotEvent
import com.banglakeys.app.robot.RobotFaceView

/**
 * ইউনিফাইড EMO কন্ট্রোল সেন্টার (Voice Command + 12-Face Brain + Floating Overlay + Ad Skipper).
 *
 * আগের আলাদা Voice Assistant কনসোল এবং Robot Face টেস্টারকে একত্রিত করে এক জায়গায়
 * সব EMO ক্ষমতা ও সেটিংস পরিচালনা করে।
 */
class AssistantActivity : Activity() {

    companion object {
        private const val REQ_PERMS = 201
        private const val REQ_STORAGE_TREE = 202
        const val EXTRA_SCROLL_TO_FACES = "scroll_to_faces"
    }

    private data class Palette(
        val bg: Int,
        val card: Int,
        val cardAlt: Int,
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
        val dangerSoft: Int,
    )

    private val pal = Palette(
        bg = 0xFF080C12.toInt(),
        card = 0xFF101722.toInt(),
        cardAlt = 0xFF151F2E.toInt(),
        border = 0xFF203246.toInt(),
        primary = 0xFF36D1FF.toInt(),
        primarySoft = 0xFF0D2838.toInt(),
        text = 0xFFF1F7FF.toInt(),
        subText = 0xFF8CA8C2.toInt(),
        ok = 0xFF22E58A.toInt(),
        okSoft = 0xFF0D2E20.toInt(),
        warn = 0xFFFFC857.toInt(),
        warnSoft = 0xFF35280D.toInt(),
        danger = 0xFFFF5A6E.toInt(),
        dangerSoft = 0xFF351218.toInt(),
    )

    lateinit var faceView: RobotFaceView
        private set

    // Dynamic status / interactive widgets
    private lateinit var scrollView: ScrollView
    private lateinit var faceLabCard: View
    private lateinit var eventsGridContainer: LinearLayout
    private lateinit var btnToggleEvents: TextView
    private val faceTileBoxes = HashMap<FaceId, LinearLayout>()

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
    private lateinit var chipContentDir: LinearLayout
    private lateinit var chipContentDirText: TextView
    private lateinit var chipFingerDir: LinearLayout
    private lateinit var chipFingerDirText: TextView
    private lateinit var scrollDirHintText: TextView

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

    private lateinit var switchBodySensors: Switch
    private lateinit var switchBodyAudio: Switch
    private lateinit var switchBodyHaptics: Switch
    private lateinit var learningPercentBadge: TextView
    private lateinit var learningSummarySub: TextView
    private lateinit var learningSummaryBar: ProgressBar
    private var centerSensors: EmoBodySensorController? = null
    private var centerReactor: EmoLiveReactor? = null

    private val hubListener: () -> Unit = {
        if (!isFinishing && !isDestroyed) {
            updateFaceTileHighlights()
            if (::faceView.isInitialized) faceView.invalidate()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun roundedBg(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun rippleCard(view: View, fill: Int, radiusDp: Int, stroke: Int? = null) {
        val base = roundedBg(fill, radiusDp, stroke)
        view.background = RippleDrawable(ColorStateList.valueOf(0x3336D1FF), base, null)
        view.isClickable = true
        view.isFocusable = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }

        // Edge-to-edge safe inset handling
        root.setOnApplyWindowInsetsListener { v, insets ->
            val top = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            v.setPadding(dp(16), dp(14) + top, dp(16), dp(28) + bottom)
            insets
        }

        // ---- Header
        root.addView(buildHeader())

        // ---- 0. Interactive EMO Face + Voice Quick Trigger
        root.addView(buildEmoHeroCard())

        // ---- 0a. EMO Learning Mode Progress Card
        root.addView(buildLearningModeCard())

        // ---- 0b. 32 Expressions & Brain Lab
        faceLabCard = buildFaceAndBrainLabCard()
        root.addView(faceLabCard)

        // ---- 1. Voice Control & Mode Card
        root.addView(buildServiceCard())

        // ---- 1b. Command Guide & Tester Card
        root.addView(buildCommandGuideCard())

        // ---- 2. Side-Edge EMO Overlay & Auto Ad Skipper
        root.addView(buildOverlayAndAdSkipCard())

        // ---- 3. 5/5 Setup Checklist & SAF Folder
        root.addView(buildSetupChecklistCard())

        // ---- 4. Safety & Confirmation Rules
        root.addView(buildSafetyCard())

        // ---- 5. Gemini Fallback + Gesture Test
        root.addView(buildGeminiAndTestCard())

        scrollView = ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            addView(root)
        }
        setContentView(scrollView)

        centerSensors = EmoBodySensorController(this)
        centerReactor = EmoLiveReactor(this)

        if (intent?.getBooleanExtra(EXTRA_SCROLL_TO_FACES, false) == true) {
            scrollView.post {
                scrollView.smoothScrollTo(0, faceLabCard.top)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        EmoBrainHub.addListener(hubListener)
        syncCenterBodySensors()
    }

    override fun onStop() {
        centerReactor?.stop()
        centerSensors?.stop()
        EmoBrainHub.removeListener(hubListener)
        super.onStop()
    }

    override fun onDestroy() {
        centerReactor?.release()
        super.onDestroy()
    }

    private fun syncCenterBodySensors() {
        if (AssistantPrefs.emoBodySensorsInCenter(this)) {
            centerSensors?.start()
            centerReactor?.start()
        } else {
            centerReactor?.stop()
            centerSensors?.stop()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
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
            tag = "emo_control_back_btn"
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
                text = "EMO Control Center"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.text)
            })
            addView(TextView(this@AssistantActivity).apply {
                text = "ভয়েস কমান্ড, ১২-ফেস ব্রেন, ওভারলে ও অ্যাড স্কিপার"
                textSize = 12f
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

    private fun addCardHeader(
        card: LinearLayout,
        icon: AssistantIcons.Kind,
        title: String,
        subtitle: String? = null,
    ) {
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
        testTag: String? = null,
        onClick: () -> Unit,
    ): Triple<LinearLayout, ImageView, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            if (testTag != null) tag = testTag
            rippleCard(this, bg, 14, stroke)
            setOnClickListener { onClick() }
        }
        val iv = ImageView(this).apply {
            setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, icon, fg, 18))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        }
        val tv = TextView(this).apply {
            text = label
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(fg)
        }
        box.addView(iv)
        box.addView(tv)
        return Triple(box, iv, tv)
    }

    private fun buildEmoHeroCard(): View {
        val card = makeCard()
        addCardHeader(
            card,
            AssistantIcons.Kind.BOT,
            "লাইভ EMO রোবট ও ব্রেন",
            "ফেসে ট্যাপ করলে কথা বলবে · কমান্ড দিলে রিয়েল-টাইম রিঅ্যাকশন দেবে"
        )

        faceView = RobotFaceView(this).apply {
            tag = "robot_face_view"
            background = roundedBg(0xFF060A10.toInt(), 16, 0x4436D1FF)
        }
        card.addView(
            faceView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(250)
            ).apply { bottomMargin = dp(12) }
        )

        val quickRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val talkBtn = buildIconButton(
            icon = AssistantIcons.Kind.MIC,
            label = "EMO-কে বলুন",
            fg = 0xFF031721.toInt(),
            bg = pal.primary,
            testTag = "btn_emo_hero_ptt",
        ) {
            if (!AssistantSetup.isReady(this)) {
                toast("আগে নিচের Accessibility ও Microphone চালু করুন")
            } else {
                AssistantListenerService.triggerPtt(this)
                EmoBrainHub.onListeningStarted(isPtt = true)
                faceView.invalidate()
                Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 350)
            }
        }.first
        quickRow.addView(talkBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply {
            marginEnd = dp(6)
        })

        val resetBtn = buildIconButton(
            icon = AssistantIcons.Kind.REFRESH,
            label = "Memory রিসেট",
            fg = pal.text,
            bg = pal.cardAlt,
            stroke = pal.border,
            testTag = "btn_reset_memory",
        ) {
            EmoBrainHub.resetMemory()
            faceView.invalidate()
            updateFaceTileHighlights()
            toast("EMO ব্রেন মেমোরি রিসেট হয়েছে")
        }.first
        quickRow.addView(resetBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })

        card.addView(quickRow)

        val bodyBtn = buildIconButton(
            icon = AssistantIcons.Kind.BOT,
            label = "🤖 EMO Body Mode (ফোনের শরীর)",
            fg = pal.primary,
            bg = pal.primarySoft,
            stroke = pal.primary,
            testTag = "btn_open_emo_body",
        ) {
            startActivity(Intent(this, EmoBodyActivity::class.java))
        }.first
        card.addView(
            bodyBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(8)
            }
        )

        card.addView(View(this).apply {
            setBackgroundColor(pal.border)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(6)
            }
        })

        switchBodySensors = addToggleRow(
            card,
            "লাইভ বডি সেন্সর (Live Body Sensors)",
            "এই স্ক্রিনে ফোন নাড়ালে, ফেলে দিলে বা কানে নিলে EMO সাথে সাথে রিঅ্যাক্ট করবে",
            AssistantPrefs.emoBodySensorsInCenter(this)
        ) { checked ->
            AssistantPrefs.setEmoBodySensorsInCenter(this, checked)
            syncCenterBodySensors()
        }.apply { tag = "switch_live_body_sensors" }

        switchBodyAudio = addToggleRow(
            card,
            "EMO বাংলা ভয়েস ও সাউন্ড",
            "স্পিকারে বাংলা অনুভূতি ও রোবট কিচিরমিচির সাউন্ড শোনাবে",
            AssistantPrefs.emoVoiceSfxEnabled(this)
        ) { checked ->
            AssistantPrefs.setEmoVoiceSfxEnabled(this, checked)
        }.apply { tag = "switch_emo_voice_sfx" }

        switchBodyHaptics = addToggleRow(
            card,
            "EMO হ্যাপটিক হার্টবিট ও ভাইব্রেশন",
            "আদর, ফিঙ্গারপ্রিন্ট বা পড়ে যাওয়ায় হার্টবিট ও মৃদু কম্পন দেবে",
            AssistantPrefs.emoHapticsEnabled(this)
        ) { checked ->
            AssistantPrefs.setEmoHapticsEnabled(this, checked)
        }.apply { tag = "switch_emo_haptics" }

        return card
    }

    private fun buildLearningModeCard(): View {
        val card = makeCard()
        val headRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }
        }
        headRow.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = roundedBg(pal.primarySoft, 10)
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) }
            addView(ImageView(this@AssistantActivity).apply {
                setImageDrawable(AssistantIcons.drawable(this@AssistantActivity, AssistantIcons.Kind.SPARKLE, pal.primary, 18))
            })
        })
        headRow.addView(TextView(this).apply {
            text = "EMO Learning Mode"
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.text)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        learningPercentBadge = TextView(this).apply {
            text = "0%"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.primary)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedBg(pal.primarySoft, 99)
            tag = "learning_summary_percent"
        }
        headRow.addView(learningPercentBadge)
        card.addView(headRow)

        learningSummarySub = TextView(this).apply {
            textSize = 12f
            setTextColor(pal.subText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }
        }
        card.addView(learningSummarySub)

        learningSummaryBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(pal.primary)
            progressBackgroundTintList = ColorStateList.valueOf(pal.cardAlt)
            tag = "learning_summary_progress"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8),
            ).apply { bottomMargin = dp(10) }
        }
        card.addView(learningSummaryBar)

        val openBtn = buildIconButton(
            icon = AssistantIcons.Kind.SPARKLE,
            label = "🧠 EMO Learning Mode খুলুন →",
            fg = pal.primary,
            bg = pal.primarySoft,
            stroke = pal.primary,
            testTag = "btn_open_emo_learning",
        ) {
            startActivity(Intent(this, EmoLearningActivity::class.java))
        }.first
        card.addView(openBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ))

        return card
    }

    private fun buildFaceAndBrainLabCard(): View {
        val card = makeCard()
        addCardHeader(
            card,
            AssistantIcons.Kind.SPARKLE,
            "৩২টি EMO এক্সপ্রেশন ও ব্রেন টেস্ট",
            "যেকোনো ফেসে ট্যাপ করে দেখুন — কিবোর্ড, Body Mode ও ভাসমান ওভারলেতে এগুলোই চলে"
        )

        faceTileBoxes.clear()
        fun addFaceGridSection(sectionTitle: String, items: List<FaceId>) {
            card.addView(TextView(this).apply {
                text = sectionTitle
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(pal.primary)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(6)
                }
            })
            for (rowIdx in items.indices step 4) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6) }
                }
                for (colIdx in 0 until 4) {
                    val idx = rowIdx + colIdx
                    if (idx < items.size) {
                        val face = items[idx]
                        val tile = buildFaceTile(face)
                        faceTileBoxes[face] = tile
                        row.addView(
                            tile,
                            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                if (colIdx > 0) marginStart = dp(3)
                                if (colIdx < 3) marginEnd = dp(3)
                            }
                        )
                    } else {
                        row.addView(View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                        })
                    }
                }
                card.addView(row)
            }
        }

        addFaceGridSection("ক্লাসিক ১২টি (Classic EMO)", FaceId.CLASSIC_12)
        addFaceGridSection("মাল্টি-রিঅ্যাকশন ২০টি (Expressive EMO)", FaceId.EXPRESSIVE_20)
        updateFaceTileHighlights()

        // Expand/collapse toggle for 30 Brain & Body Event test buttons
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            rippleCard(this, pal.cardAlt, 12, pal.border)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        toggleRow.addView(TextView(this).apply {
            text = "🧪 ব্রেন ও বডি ইভেন্ট টেস্টার (৩০টি)"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.text)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        btnToggleEvents = TextView(this).apply {
            text = "দেখুন ▾"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.primary)
        }
        toggleRow.addView(btnToggleEvents)
        card.addView(toggleRow)

        eventsGridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }

        val events: List<Triple<String, String, RobotEvent>> = listOf(
            Triple("event_tap", "👋 Tap", RobotEvent.Tap),
            Triple("event_long_press", "🤗 Long press (আদর)", RobotEvent.LongPress),
            Triple("event_shake", "📳 Shake (ঝাঁকি)", RobotEvent.Shake),
            Triple("event_free_fall", "😱 Free fall (পড়ছে)", RobotEvent.FreeFall),
            Triple("event_dropped", "😭 Dropped (কান্না)", RobotEvent.Dropped),
            Triple("event_comforted", "🥰 আদর / শান্ত", RobotEvent.Comforted),
            Triple("event_fingerprint", "🐾 Fingerprint Match", RobotEvent.FingerprintMatched),
            Triple("event_compass_spin", "😵‍💫 Compass Spin", RobotEvent.CompassSpin),
            Triple("event_ear_near", "🤫 কানের কাছে (Ear)", RobotEvent.EarNear),
            Triple("event_lift", "✋ Lift (তোলা)", RobotEvent.Lift),
            Triple("event_face_down", "⬇️ Face down (ঘুম)", RobotEvent.FaceDown),
            Triple("event_face_up", "⬆️ Face up (জাগা)", RobotEvent.FaceUp),
            Triple("event_clap", "😳 Clap (চমক)", RobotEvent.Clap),
            Triple("event_loud_sound", "🔊 Loud sound", RobotEvent.LoudSound),
            Triple("event_voice_heard", "🎙️ Voice heard", RobotEvent.VoiceHeard),
            Triple("event_battery_low", "🔋 Battery low", RobotEvent.BatteryLow),
            Triple("event_charging", "⚡ Charging", RobotEvent.Charging),
            Triple("event_dark", "🥺 Dark", RobotEvent.Dark),
            Triple("event_bright", "😉 Bright", RobotEvent.Bright),
            Triple("event_typing_fast", "⌨️ Typing fast", RobotEvent.TypingFast),
            Triple("event_typing_slow", "😑 Typing slow", RobotEvent.TypingSlow),
            Triple("event_backspace_burst", "😬 Backspace burst", RobotEvent.BackspaceBurst),
            Triple("event_keyword_love", "❤️ Keyword: love", RobotEvent.KeywordLove),
            Triple("event_keyword_laugh", "😂 Keyword: haha", RobotEvent.KeywordLaugh),
            Triple("event_keyword_kiss", "😘 Keyword: kiss", RobotEvent.KeywordKiss),
            Triple("event_keyword_shock", "😳 Keyword: omg", RobotEvent.KeywordShock),
            Triple("event_keyword_cry", "🥺 Keyword: cry", RobotEvent.KeywordCry),
            Triple("event_keyword_cute", "🤭 Keyword: uwu", RobotEvent.KeywordCute),
            Triple("event_keyword_anger", "💢 Keyword: anger", RobotEvent.KeywordAnger),
            Triple("event_message_sent", "📤 Message sent", RobotEvent.MessageSent),
        )

        var evRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        events.forEachIndexed { i, (btnTag, label, event) ->
            if (i % 2 == 0 && i != 0) {
                eventsGridContainer.addView(evRow)
                evRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            }
            evRow.addView(Button(this).apply {
                text = label
                textSize = 11.5f
                tag = btnTag
                isAllCaps = false
                setTextColor(pal.text)
                rippleCard(this, pal.cardAlt, 10, pal.border)
                minimumHeight = dp(48)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                setOnClickListener {
                    EmoBrainHub.handleEvent(event)
                    faceView.invalidate()
                    updateFaceTileHighlights()
                }
            })
        }
        if (events.size % 2 == 1) {
            evRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        }
        eventsGridContainer.addView(evRow)
        card.addView(eventsGridContainer)

        toggleRow.setOnClickListener {
            val opening = eventsGridContainer.visibility != View.VISIBLE
            eventsGridContainer.visibility = if (opening) View.VISIBLE else View.GONE
            btnToggleEvents.text = if (opening) "লুকান ▴" else "দেখুন ▾"
        }

        return card
    }

    private fun buildFaceTile(face: FaceId): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = dp(76)
            setPadding(dp(6), dp(8), dp(6), dp(8))
            tag = "face_tile_${face.name.lowercase()}"
            contentDescription = "${face.name} (${face.bn})"
            rippleCard(this, pal.cardAlt, 12, pal.border)
            setOnClickListener {
                EmoBrainHub.express(face, "${face.bn} (${face.name})", forceSpeech = true)
                faceView.invalidate()
                updateFaceTileHighlights()
            }
        }
        box.addView(
            MiniFacePreviewView(this, face).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(36)).apply {
                    bottomMargin = dp(4)
                }
            }
        )
        box.addView(TextView(this).apply {
            text = face.bn
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.text)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        return box
    }

    private fun updateFaceTileHighlights() {
        val current = EmoBrainHub.brain.mood
        for ((face, box) in faceTileBoxes) {
            val active = face == current
            rippleCard(
                box,
                if (active) pal.primarySoft else pal.cardAlt,
                12,
                if (active) pal.primary else pal.border,
            )
        }
    }

    private class MiniFacePreviewView(
        context: Context,
        private val face: FaceId,
    ) : View(context) {
        private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF060A10.toInt() }
        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x5536D1FF
            style = Paint.Style.STROKE
            strokeWidth = (1.2f * context.resources.displayMetrics.density).coerceAtLeast(1f)
        }
        private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF36D1FF.toInt() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            val w = width.toFloat()
            val h = height.toFloat()
            FaceRenderer.draw(
                canvas = canvas,
                face = face,
                cx = w / 2f,
                cy = h / 2f,
                plateW = w * 0.94f,
                plateH = h * 0.92f,
                eyeGap = w * 0.40f,
                eyeSize = h * 0.34f,
                blink = 0f,
                eyePaint = eyePaint,
                platePaint = platePaint,
                plateOutline = outlinePaint,
                glow = true,
            )
        }
    }

    private fun buildServiceCard(): View {
        val card = makeCard()
        addCardHeader(
            card,
            AssistantIcons.Kind.MIC,
            "EMO ভয়েস কন্ট্রোল ও ভাষা",
            "\"Hey EMO\" বা সাইড EMO ট্যাব থেকে কমান্ড দিন"
        )

        val svcTriple = buildIconButton(
            AssistantIcons.Kind.PLAY,
            "EMO চালু করুন",
            0xFF031721.toInt(),
            pal.primary,
            testTag = "btn_emo_service_toggle",
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
            pal.primarySoft,
            testTag = "btn_emo_ptt",
        ) {
            if (!AssistantSetup.isReady(this)) {
                toast("আগে Accessibility ও Microphone চালু করুন")
            } else {
                AssistantListenerService.triggerPtt(this)
                EmoBrainHub.onListeningStarted(isPtt = true)
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
            pal.cardAlt,
            pal.border,
            testTag = "btn_emo_lang",
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

        val pChip = buildIconButton(AssistantIcons.Kind.SHIELD, "Quiet PTT", pal.text, pal.cardAlt, pal.border) {
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

        // Scroll Direction Toggle
        card.addView(TextView(this).apply {
            text = "Scroll Direction ('upore' / 'niche')"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(pal.subText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(6)
            }
        })

        val dirRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val contentChip = buildIconButton(AssistantIcons.Kind.SWIPE_UP, "Content (ডিফল্ট)", pal.primary, pal.primarySoft) {
            AssistantPrefs.setScrollByContentDirection(this, true)
            refresh()
        }
        chipContentDir = contentChip.first
        chipContentDirText = contentChip.third
        dirRow.addView(chipContentDir, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(6)
        })

        val fingerChip = buildIconButton(AssistantIcons.Kind.SWIPE_UP, "Finger", pal.text, pal.cardAlt, pal.border) {
            AssistantPrefs.setScrollByContentDirection(this, false)
            refresh()
        }
        chipFingerDir = fingerChip.first
        chipFingerDirText = fingerChip.third
        dirRow.addView(chipFingerDir, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(6)
        })
        card.addView(dirRow)

        scrollDirHintText = TextView(this).apply {
            textSize = 11.5f
            setTextColor(pal.subText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }
        card.addView(scrollDirHintText)
        return card
    }

    private fun buildCommandGuideCard(): View {
        val card = makeCard()
        addCardHeader(
            card,
            AssistantIcons.Kind.SPARKLE,
            "EMO কমান্ড গাইড ও 99% সঠিক লিস্ট",
            "Chrome, YouTube, Facebook, TikTok ও Call কমান্ড দেখুন এবং Try করুন"
        )
        val guideBtn = buildIconButton(
            AssistantIcons.Kind.PLAY,
            "সব কমান্ড দেখুন ও Try করুন",
            0xFF031721.toInt(),
            pal.primary,
            testTag = "btn_open_command_guide",
        ) {
            startActivity(Intent(this, CommandGuideActivity::class.java))
        }.first
        card.addView(guideBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        return card
    }

    private fun buildOverlayAndAdSkipCard(): View {
        val card = makeCard()
        addCardHeader(
            card,
            AssistantIcons.Kind.OVERLAY,
            "ভাসমান EMO ওভারলে ও Auto Ad Skipper",
            "পাশে ভাসমান লাইভ EMO ফেস + নিরাপদ বিজ্ঞাপন স্কিপার"
        )

        switchOverlay = addToggleRow(
            card,
            "ভাসমান EMO সাইড ট্যাব দেখান",
            "বাম বা ডান কিনারায় টেনে নেওয়া যায় (ট্যাপ করলে EMO শোনে)",
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
            "ভাসমান EMO অবস্থান রিসেট করুন",
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
                rippleCard(this, pal.cardAlt, 12, pal.border)
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

        addChecklistItem(AssistantIcons.Kind.ACCESSIBILITY, "১. EMO Robot Accessibility", "চালু করুন") {
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
            pal.cardAlt,
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
            "ভুল কল বা মেসেজ ঠেকাতে আগে EMO কনফার্ম চাইবে"
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
            "জটিল বাংলা/Banglish কমান্ড স্থানীয় parser না বুঝলে EMO Gemini-র সাহায্য নেবে"
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
            background = roundedBg(pal.cardAlt, 12, pal.border)
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
        val saveKeyBtn = buildIconButton(AssistantIcons.Kind.CHECK_CIRCLE, "Key সেভ", 0xFF031721.toInt(), pal.primary) {
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
        onToggle: (Boolean) -> Unit,
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
            btnServiceText.text = "EMO বন্ধ করুন"
        } else {
            rippleCard(btnService, pal.primary, 14)
            btnServiceIcon.setImageDrawable(AssistantIcons.drawable(this, AssistantIcons.Kind.PLAY, 0xFF031721.toInt(), 18))
            btnServiceText.setTextColor(0xFF031721.toInt())
            btnServiceText.text = "EMO চালু করুন"
        }

        val isBangla = AssistantSetup.isBangla(this)
        btnLangText.text = "শোনার ভাষা: " + if (isBangla) "বাংলা (bn-BD)" else "English (en-US)"

        // Mode chips
        val mode = AssistantPrefs.listeningMode(this)
        if (mode == ListeningMode.CONTINUOUS) {
            rippleCard(chipContinuous, pal.primarySoft, 12, pal.primary)
            chipContinuousText.setTextColor(pal.primary)
            rippleCard(chipPtt, pal.cardAlt, 12, pal.border)
            chipPttText.setTextColor(pal.subText)
        } else {
            rippleCard(chipContinuous, pal.cardAlt, 12, pal.border)
            chipContinuousText.setTextColor(pal.subText)
            rippleCard(chipPtt, pal.primarySoft, 12, pal.primary)
            chipPttText.setTextColor(pal.primary)
        }

        // Scroll direction chips
        val byContent = AssistantPrefs.scrollByContentDirection(this)
        if (byContent) {
            rippleCard(chipContentDir, pal.primarySoft, 12, pal.primary)
            chipContentDirText.setTextColor(pal.primary)
            rippleCard(chipFingerDir, pal.cardAlt, 12, pal.border)
            chipFingerDirText.setTextColor(pal.subText)
            scrollDirHintText.text = "Content মোড চালু: 'niche' বললে নিচের লেখা দেখাবে, 'upore' বললে উপরে ফিরবে। ('porer video' সবসময় পরের শর্টস চালায়)"
        } else {
            rippleCard(chipContentDir, pal.cardAlt, 12, pal.border)
            chipContentDirText.setTextColor(pal.subText)
            rippleCard(chipFingerDir, pal.primarySoft, 12, pal.primary)
            chipFingerDirText.setTextColor(pal.primary)
            scrollDirHintText.text = "Finger মোড চালু: 'upore' বললে আঙুল উপরে টানবে, 'niche' বললে আঙুল নিচে টানবে।"
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

        switchBodySensors.isChecked = AssistantPrefs.emoBodySensorsInCenter(this)
        switchBodyAudio.isChecked = AssistantPrefs.emoVoiceSfxEnabled(this)
        switchBodyHaptics.isChecked = AssistantPrefs.emoHapticsEnabled(this)

        val learnSnap = EmoLearningStore.snapshot(this)
        learningPercentBadge.text = "${learnSnap.overallPercent}% · ${learnSnap.stage.titleBn}"
        learningSummaryBar.progress = learnSnap.overallPercent
        learningSummarySub.text =
            "টাইপিং ${learnSnap.typingPatternPercent}% (${learnSnap.totalLearnedWords} শব্দ) · ইমোজি ${learnSnap.emojiPreferencePercent}% (${learnSnap.totalEmojiUses} বার)"

        updateFaceTileHighlights()
        if (::faceView.isInitialized) faceView.invalidate()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
