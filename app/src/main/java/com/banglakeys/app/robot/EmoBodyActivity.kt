package com.banglakeys.app.robot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.banglakeys.app.AssistantIcons
import com.banglakeys.app.AssistantListenerService
import com.banglakeys.app.AssistantPrefs
import com.banglakeys.app.AssistantSetup
import kotlin.math.min
import kotlin.math.sin

/**
 * ডেডিকেটেড **EMO Body Mode**:
 * ফোনের সব শারীরিক সেন্সরকে (Accelerometer/Gyroscope ব্যালেন্স ও পতন, সামনের ক্যামেরা চোখ,
 * কানের প্রক্সিমিটি সেন্সর, কম্পাস, স্পিকার TTS + সাউন্ড, ফিঙ্গারপ্রিন্ট এবং হ্যাপটিক হার্টবিট)
 * একত্রিত করে পূর্ণাঙ্গ জীবন্ত EMO রোবট অনুভূতি দেয়।
 */
class EmoBodyActivity : ComponentActivity() {

    private object Pal {
        const val BG = 0xFF080C12.toInt()
        const val SURFACE = 0xFF0F1622.toInt()
        const val SURFACE_ALT = 0xFF152030.toInt()
        const val BORDER = 0xFF1F3448.toInt()
        const val CYAN = 0xFF36D1FF.toInt()
        const val CYAN_SOFT = 0xFF0D2A3C.toInt()
        const val TEXT = 0xFFF1F7FF.toInt()
        const val SUB = 0xFF8AA8C4.toInt()
        const val OK = 0xFF22E58A.toInt()
        const val OK_SOFT = 0xFF0D2E20.toInt()
        const val CORAL = 0xFFFF5A76.toInt()
        const val CORAL_SOFT = 0xFF38121E.toInt()
        const val WARN = 0xFFFFC857.toInt()
    }

    private lateinit var sensorController: EmoBodySensorController
    private lateinit var liveReactor: EmoLiveReactor
    private lateinit var cameraEyes: EmoCameraEyes

    private lateinit var visorView: EmoBodyVisorView
    private lateinit var statusPillText: TextView
    private lateinit var statusPillBg: LinearLayout

    private lateinit var hudEye: TextView
    private lateinit var hudEar: TextView
    private lateinit var hudCompass: TextView
    private lateinit var hudTilt: TextView
    private lateinit var hudBattery: TextView

    private lateinit var sootheBanner: LinearLayout
    private lateinit var pawFallbackOverlay: LinearLayout
    private lateinit var pawHoldBtn: LinearLayout
    private lateinit var pawHoldHint: TextView

    private lateinit var needsEnergyText: TextView
    private lateinit var needsBondText: TextView
    private lateinit var needsFunText: TextView

    private lateinit var dockEyeCam: DockBtn
    private lateinit var dockAudio: DockBtn
    private lateinit var dockHaptic: DockBtn

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pawHoldTriggered = false
    private val pawHoldRunnable = Runnable {
        pawHoldTriggered = true
        pawFallbackOverlay.visibility = View.GONE
        EmoBrainHub.handleEvent(RobotEvent.FingerprintMatched)
        toast("🐾 EMO তোমার স্পর্শ চিনে নিয়েছে!")
    }

    private val cameraPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                AssistantPrefs.setEmoCameraEyesEnabled(this, true)
                startCameraEyesIfAllowed()
                toast("👁️ EMO-র চোখ এখন তোমাকে দেখছে")
            } else {
                AssistantPrefs.setEmoCameraEyesEnabled(this, false)
                toast("ক্যামেরা অনুমতি ছাড়া Eye Cam চালু করা যাবে না")
            }
            refreshHud()
        }

    private val hubListener: () -> Unit = {
        if (!isFinishing && !isDestroyed) {
            refreshHud()
            if (::visorView.isInitialized) visorView.invalidate()
        }
    }

    private data class DockBtn(
        val container: LinearLayout,
        val icon: TextView,
        val label: TextView,
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun roundedBg(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun ripple(view: View, fill: Int, radiusDp: Int, stroke: Int? = null) {
        view.background = RippleDrawable(ColorStateList.valueOf(0x3336D1FF), roundedBg(fill, radiusDp, stroke), null)
        view.isClickable = true
        view.isFocusable = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        sensorController = EmoBodySensorController(this)
        liveReactor = EmoLiveReactor(this)
        cameraEyes = EmoCameraEyes(this)

        val outer = FrameLayout(this).apply {
            setBackgroundColor(Pal.BG)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Pal.BG)
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }

        root.setOnApplyWindowInsetsListener { v, insets ->
            val sys = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars())
            } else {
                null
            }
            val top = sys?.top ?: @Suppress("DEPRECATION") insets.systemWindowInsetTop
            val bottom = sys?.bottom ?: @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            v.setPadding(dp(14), dp(10) + top, dp(14), dp(12) + bottom)
            insets
        }

        // 1. Top bar
        root.addView(buildTopBar())

        // 2. Telemetry Organ HUD Strip
        root.addView(buildTelemetryHud())

        // 3. Giant Central Physical Visor
        visorView = EmoBodyVisorView(this).apply {
            tag = "emo_body_visor"
        }
        root.addView(
            visorView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        )

        // 4. Crying Recovery Banner (Hidden unless isCrying)
        sootheBanner = buildSootheBanner()
        root.addView(sootheBanner)

        // 5. Compact Needs Strip
        root.addView(buildNeedsStrip())

        // 6. Bottom Hardware Organ Dock
        root.addView(buildBottomDock())

        outer.addView(
            root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Fallback Fingerprint Hold Scanner overlay (if no hardware biometric sensor)
        pawFallbackOverlay = buildPawFallbackOverlay()
        outer.addView(
            pawFallbackOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(outer)
        refreshHud()
    }

    override fun onStart() {
        super.onStart()
        EmoBrainHub.addListener(hubListener)
        sensorController.start()
        liveReactor.start()
        startCameraEyesIfAllowed()
        refreshHud()
    }

    override fun onStop() {
        cameraEyes.stop()
        liveReactor.stop()
        sensorController.stop()
        EmoBrainHub.removeListener(hubListener)
        mainHandler.removeCallbacks(pawHoldRunnable)
        super.onStop()
    }

    override fun onDestroy() {
        cameraEyes.shutdown()
        liveReactor.release()
        super.onDestroy()
    }

    private fun startCameraEyesIfAllowed() {
        if (!AssistantPrefs.emoCameraEyesEnabled(this)) return
        val hasCam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasCam) {
            cameraEyes.start(this) { err ->
                toast("Eye Cam ত্রুটি: $err")
            }
        }
    }

    // --------------------------------------------------------- UI Builders

    private fun buildTopBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val back = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            tag = "emo_body_back"
            contentDescription = "ফিরে যান"
            ripple(this, Pal.SURFACE, 14, Pal.BORDER)
            setOnClickListener { finish() }
            addView(ImageView(this@EmoBodyActivity).apply {
                setImageDrawable(AssistantIcons.drawable(this@EmoBodyActivity, AssistantIcons.Kind.BACK, Pal.TEXT, 20))
            })
        }
        row.addView(back)

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
                marginEnd = dp(8)
            }
            addView(TextView(this@EmoBodyActivity).apply {
                text = "EMO Body"
                textSize = 19f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Pal.TEXT)
            })
            addView(TextView(this@EmoBodyActivity).apply {
                text = "ফোনের শরীর · সেন্সর ও অনুভূতি"
                textSize = 11.5f
                setTextColor(Pal.SUB)
            })
        }
        row.addView(titleCol)

        statusPillBg = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(6), dp(11), dp(6))
            background = roundedBg(Pal.CYAN_SOFT, 99, Pal.CYAN)
            tag = "emo_body_status_pill"
        }
        statusPillText = TextView(this).apply {
            text = "লাইভ বডি"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Pal.CYAN)
        }
        statusPillBg.addView(statusPillText)
        row.addView(statusPillBg)

        return row
    }

    private fun buildTelemetryHud(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun makeChip(tagStr: String, initial: String): TextView = TextView(this).apply {
            text = initial
            tag = tagStr
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Pal.TEXT)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = roundedBg(Pal.SURFACE, 10, Pal.BORDER)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
        }

        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hudEye = makeChip("hud_eye", "👁️ Eye: OFF")
        hudEar = makeChip("hud_ear", "👂 কান: খোলা")
        hudCompass = makeChip("hud_compass", "🧭 0° N")
        r1.addView(hudEye)
        r1.addView(hudEar)
        r1.addView(hudCompass)

        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hudTilt = makeChip("hud_tilt", "⚖️ 0° / 0°")
        hudBattery = makeChip("hud_battery", "🔋 80%")
        r2.addView(hudTilt)
        r2.addView(hudBattery)

        wrap.addView(r1)
        wrap.addView(r2)
        return wrap
    }

    private fun buildSootheBanner(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(52)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            tag = "btn_soothe_emo"
            contentDescription = "EMO কাঁদছে, ট্যাপ করে আদর করো"
            ripple(this, Pal.CORAL_SOFT, 16, Pal.CORAL)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            setOnClickListener {
                EmoBrainHub.handleEvent(RobotEvent.Comforted)
                refreshHud()
            }
            addView(TextView(this@EmoBodyActivity).apply {
                text = "😢 পড়ে ব্যথা পেয়েছে! ট্যাপ করে আদর করো"
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Pal.TEXT)
            })
        }
    }

    private fun buildNeedsStrip(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = roundedBg(Pal.SURFACE, 12, Pal.BORDER)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        fun cell(labelColor: Int): TextView = TextView(this).apply {
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(labelColor)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        needsEnergyText = cell(Pal.CYAN)
        needsBondText = cell(Pal.CORAL)
        needsFunText = cell(Pal.OK)
        row.addView(needsEnergyText)
        row.addView(needsBondText)
        row.addView(needsFunText)
        return row
    }

    private fun buildBottomDock(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun makeDockButton(
            tagStr: String,
            emoji: String,
            label: String,
            onClick: () -> Unit,
        ): DockBtn {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(58)
                minimumWidth = dp(48)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                tag = tagStr
                contentDescription = label
                ripple(this, Pal.SURFACE_ALT, 14, Pal.BORDER)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener { onClick() }
            }
            val ic = TextView(this).apply {
                text = emoji
                textSize = 18f
                gravity = Gravity.CENTER
            }
            val tv = TextView(this).apply {
                text = label
                textSize = 10.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Pal.TEXT)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            }
            box.addView(ic)
            box.addView(tv)
            return DockBtn(box, ic, tv)
        }

        dockEyeCam = makeDockButton("btn_body_eye_cam", "👁️", "Eye Cam") {
            val next = !AssistantPrefs.emoCameraEyesEnabled(this)
            if (next) {
                val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
                if (granted) {
                    AssistantPrefs.setEmoCameraEyesEnabled(this, true)
                    startCameraEyesIfAllowed()
                } else {
                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                }
            } else {
                AssistantPrefs.setEmoCameraEyesEnabled(this, false)
                cameraEyes.stop()
            }
            refreshHud()
        }

        val dockFinger = makeDockButton("btn_body_fingerprint", "🐾", "Paw Scan") {
            EmoFingerprintBridge.launchFingerprintScan(
                activity = this,
                onMatched = { refreshHud() },
                onRejected = { msg -> toast(msg) },
                onNeedHoldFallback = {
                    pawHoldTriggered = false
                    pawHoldHint.text = "EMO-কে চেনাতে ১ সেকেন্ড চেপে ধরো"
                    pawFallbackOverlay.visibility = View.VISIBLE
                }
            )
        }

        val dockVoice = makeDockButton("btn_body_voice_ptt", "🎙️", "Hey EMO") {
            if (!AssistantSetup.isReady(this)) {
                toast("আগে EMO Control Center থেকে Accessibility ও Mic চালু করুন")
            } else {
                AssistantListenerService.triggerPtt(this)
                EmoBrainHub.onListeningStarted(isPtt = true)
                refreshHud()
            }
        }

        dockAudio = makeDockButton("btn_body_audio_toggle", "🔊", "Voice") {
            val next = !AssistantPrefs.emoVoiceSfxEnabled(this)
            AssistantPrefs.setEmoVoiceSfxEnabled(this, next)
            refreshHud()
            toast(if (next) "EMO স্পিকার ও সাউন্ড চালু" else "EMO স্পিকার মিউট করা হয়েছে")
        }

        dockHaptic = makeDockButton("btn_body_haptic_toggle", "💓", "Haptic") {
            val next = !AssistantPrefs.emoHapticsEnabled(this)
            AssistantPrefs.setEmoHapticsEnabled(this, next)
            if (next) EmoHaptics.heartbeat(this)
            refreshHud()
        }

        row.addView(dockEyeCam.container)
        row.addView(dockFinger.container)
        row.addView(dockVoice.container)
        row.addView(dockAudio.container)
        row.addView(dockHaptic.container)
        return row
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPawFallbackOverlay(): LinearLayout {
        val backdrop = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xCC05080D.toInt())
            visibility = View.GONE
            tag = "paw_fallback_overlay"
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setOnClickListener {
                mainHandler.removeCallbacks(pawHoldRunnable)
                visibility = View.GONE
            }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(24), dp(22), dp(20))
            background = roundedBg(Pal.SURFACE, 22, Pal.CYAN)
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        card.addView(TextView(this).apply {
            text = "🐾 EMO Paw Scan"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Pal.TEXT)
            gravity = Gravity.CENTER
        })

        pawHoldHint = TextView(this).apply {
            text = "EMO-কে চেনাতে ১ সেকেন্ড চেপে ধরো"
            textSize = 13f
            setTextColor(Pal.SUB)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(18)
            }
        }
        card.addView(pawHoldHint)

        pawHoldBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            minimumWidth = dp(96)
            minimumHeight = dp(96)
            tag = "btn_paw_hold_pad"
            contentDescription = "চেপে ধরে EMO-কে চেনান"
            ripple(this, Pal.CYAN_SOFT, 48, Pal.CYAN)
            addView(TextView(this@EmoBodyActivity).apply {
                text = "🫆"
                textSize = 36f
            })
            setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        pawHoldTriggered = false
                        pawHoldHint.text = "স্ক্যান হচ্ছে… ধরে রাখো ❤️"
                        EmoHaptics.tapPulse(this@EmoBodyActivity)
                        mainHandler.removeCallbacks(pawHoldRunnable)
                        mainHandler.postDelayed(pawHoldRunnable, 950L)
                        v.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        mainHandler.removeCallbacks(pawHoldRunnable)
                        if (!pawHoldTriggered) {
                            pawHoldHint.text = "আরেকটু সময় ধরে রাখো (১ সেকেন্ড)"
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        card.addView(pawHoldBtn, LinearLayout.LayoutParams(dp(96), dp(96)))

        val closeBtn = TextView(this).apply {
            text = "বন্ধ করুন"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Pal.SUB)
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener {
                mainHandler.removeCallbacks(pawHoldRunnable)
                backdrop.visibility = View.GONE
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        card.addView(closeBtn)

        backdrop.addView(card)
        return backdrop
    }

    // --------------------------------------------------------- Refresh HUD

    private fun refreshHud() {
        val b = EmoBrainHub.brain
        val s = EmoBrainHub.bodyState

        // Status pill
        when {
            b.isCrying -> {
                statusPillBg.background = roundedBg(Pal.CORAL_SOFT, 99, Pal.CORAL)
                statusPillText.setTextColor(Pal.CORAL)
                statusPillText.text = "কাঁদছে 😢"
            }
            s.isNearEar -> {
                statusPillBg.background = roundedBg(Pal.CYAN_SOFT, 99, Pal.CYAN)
                statusPillText.setTextColor(Pal.CYAN)
                statusPillText.text = "ফিসফিস 🤫"
            }
            s.cameraEyesActive && s.faceDetected -> {
                statusPillBg.background = roundedBg(Pal.OK_SOFT, 99, Pal.OK)
                statusPillText.setTextColor(Pal.OK)
                statusPillText.text = "চোখে দেখছে 👁️"
            }
            else -> {
                statusPillBg.background = roundedBg(Pal.CYAN_SOFT, 99, Pal.CYAN)
                statusPillText.setTextColor(Pal.CYAN)
                statusPillText.text = "লাইভ বডি"
            }
        }

        // Telemetry chips
        val camWanted = AssistantPrefs.emoCameraEyesEnabled(this)
        hudEye.text = when {
            s.cameraEyesActive && s.faceDetected -> "👁️ Eye: ট্র্যাকিং"
            s.cameraEyesActive || camWanted -> "👁️ Eye: খুঁজছে"
            else -> "👁️ Eye: OFF"
        }
        hudEar.text = if (s.isNearEar) "👂 কান: ফিসফিস 🤫" else "👂 কান: খোলা"
        hudCompass.text = "🧭 ${s.azimuthDeg.toInt()}° ${s.compassLabel}"
        hudTilt.text = "⚖️ ${s.rollDeg.toInt()}° / ${s.pitchDeg.toInt()}°"
        hudBattery.text = if (s.isCharging) "⚡ ${s.batteryPct}% চার্জ" else "🔋 ${s.batteryPct}%"

        // Soothe banner
        sootheBanner.visibility = if (b.isCrying) View.VISIBLE else View.GONE

        // Needs
        needsEnergyText.text = "⚡ Energy ${b.needs.energy}%"
        needsBondText.text = "❤️ Bond ${b.needs.bond}%"
        needsFunText.text = "✨ Fun ${b.needs.funLevel}%"

        // Dock toggle highlights
        val camActive = AssistantPrefs.emoCameraEyesEnabled(this)
        ripple(
            dockEyeCam.container,
            if (camActive) Pal.CYAN_SOFT else Pal.SURFACE_ALT,
            14,
            if (camActive) Pal.CYAN else Pal.BORDER
        )
        dockEyeCam.label.setTextColor(if (camActive) Pal.CYAN else Pal.TEXT)

        val audioOn = AssistantPrefs.emoVoiceSfxEnabled(this)
        dockAudio.icon.text = if (audioOn) "🔊" else "🔇"
        ripple(
            dockAudio.container,
            if (audioOn) Pal.CYAN_SOFT else Pal.SURFACE_ALT,
            14,
            if (audioOn) Pal.CYAN else Pal.BORDER
        )

        val hapticOn = AssistantPrefs.emoHapticsEnabled(this)
        ripple(
            dockHaptic.container,
            if (hapticOn) Pal.CYAN_SOFT else Pal.SURFACE_ALT,
            14,
            if (hapticOn) Pal.CYAN else Pal.BORDER
        )
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // --------------------------------------------------------- Physical Interactive Visor View

    private class EmoBodyVisorView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val blink = BlinkAnimator()
        private var touchGazeX = 0f
        private var touchGazeY = 0f
        private var touching = false
        private var petDist = 0f
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var petTriggered = false

        private val bgCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A0F18.toInt()
        }
        private val bgOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x4D36D1FF
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * density
        }
        private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF36D1FF.toInt()
        }
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF102738.toInt()
        }
        private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8036D1FF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * density
        }
        private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFEAF8FF.toInt()
            textAlign = Paint.Align.CENTER
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14.5f, resources.displayMetrics)
        }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF8AA8C4.toInt()
            textAlign = Paint.Align.CENTER
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12.5f, resources.displayMetrics)
        }

        init {
            isClickable = true
            isFocusable = true
            isLongClickable = true
            cameraDistance = 14000f * density
            contentDescription = "EMO রোবটের জীবন্ত মুখ — ট্যাপ বা আঙুল বুলিয়ে আদর করুন"
            setOnClickListener {
                if (!petTriggered) {
                    EmoBrainHub.handleEvent(RobotEvent.Tap)
                }
            }
            setOnLongClickListener {
                petTriggered = true
                EmoBrainHub.handleEvent(RobotEvent.LongPress)
                true
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touching = true
                    petTriggered = false
                    petDist = 0f
                    lastTouchX = event.x
                    lastTouchY = event.y
                    updateTouchGaze(event.x, event.y)
                }
                MotionEvent.ACTION_MOVE -> {
                    updateTouchGaze(event.x, event.y)
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    petDist += kotlin.math.hypot(dx, dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    if (!petTriggered && petDist > 95f * density) {
                        petTriggered = true
                        EmoBrainHub.handleEvent(RobotEvent.LongPress)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touching = false
                    touchGazeX = 0f
                    touchGazeY = 0f
                }
            }
            return super.onTouchEvent(event)
        }

        private fun updateTouchGaze(x: Float, y: Float) {
            if (width <= 0 || height <= 0) return
            touchGazeX = ((x / width) * 2f - 1f).coerceIn(-1f, 1f)
            touchGazeY = ((y / height) * 2f - 1f).coerceIn(-1f, 1f)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return

            EmoBrainHub.tick()
            val brain = EmoBrainHub.brain
            val body = EmoBrainHub.bodyState
            val nowMs = System.currentTimeMillis()

            // ফিজিক্যাল 3D টিল্ট ও কান্নার সময় মৃদু কাঁপুনি
            val tremble = if (brain.isCrying) {
                sin((nowMs % 320L) / 320f * 2f * Math.PI).toFloat() * 1.6f
            } else 0f
            rotationX = (-body.pitchDeg * 0.65f).coerceIn(-16f, 16f)
            rotationY = (body.rollDeg * 0.65f + tremble).coerceIn(-16f, 16f)

            val cx = width / 2f
            val bubbleBand = 52f * density
            val bottomHintBand = 30f * density
            val availH = (height - bubbleBand - bottomHintBand).coerceAtLeast(100f * density)
            val plateW = min(width * 0.90f, availH * 1.55f)
            val plateH = min(availH * 0.86f, plateW * 0.62f)
            val cy = bubbleBand + availH / 2f
            val eyeSize = plateH * 0.25f
            val eyeGap = plateW * 0.39f

            val gx = if (touching) touchGazeX else body.combinedGazeX()
            val gy = if (touching) touchGazeY else body.combinedGazeY()

            eyePaint.color = if (brain.isCrying) 0xFF5CE1FF.toInt() else 0xFF36D1FF.toInt()
            bgOutlinePaint.color = if (brain.isCrying) 0x88FF5A76.toInt() else 0x5536D1FF

            FaceRenderer.draw(
                canvas = canvas,
                face = brain.mood,
                cx = cx,
                cy = cy,
                plateW = plateW,
                plateH = plateH,
                eyeGap = eyeGap,
                eyeSize = eyeSize,
                blink = if (brain.sleeping) 0.82f else blink.tick(),
                eyePaint = eyePaint,
                platePaint = bgCardPaint,
                plateOutline = bgOutlinePaint,
                gazeX = gx * (plateW * 0.095f),
                gazeY = gy * (plateH * 0.095f),
                glow = true,
            )

            EmoParticles.draw(
                canvas = canvas,
                cx = cx,
                cy = cy,
                plateW = plateW,
                plateH = plateH,
                eyeGap = eyeGap,
                isCrying = brain.isCrying,
                mood = brain.mood,
                effect = body.lastEffect,
                effectAgeMs = nowMs - body.lastEffectAtMs,
                nowMs = nowMs,
            )

            brain.speechText?.let { speech ->
                val maxW = (width - 24f * density).coerceAtLeast(100f)
                val tw = bubbleText.measureText(speech)
                val bw = (tw + 30f * density).coerceAtMost(maxW)
                val bh = 36f * density
                val top = (cy - plateH / 2f - bh - 10f * density).coerceAtLeast(4f * density)
                val rect = RectF(cx - bw / 2f, top, cx + bw / 2f, top + bh)
                canvas.drawRoundRect(rect, 16f * density, 16f * density, bubblePaint)
                canvas.drawRoundRect(rect, 16f * density, 16f * density, bubbleStroke)
                val fm = bubbleText.fontMetrics
                canvas.drawText(speech, cx, rect.centerY() - (fm.ascent + fm.descent) / 2f, bubbleText)
            }

            val hint = when {
                brain.isCrying -> "😢 কপালে হাত বুলিয়ে বা নিচে আদর করো"
                brain.sleeping -> "💤 ফোনে মৃদু টোকা দিলে বা সোজা করলে জাগবে"
                else -> "${brain.mood.name} (${brain.mood.bn}) · কপালে হাত বুলিয়ে আদর করুন"
            }
            canvas.drawText(hint, cx, height - 10f * density, hintPaint)

            if (isAttachedToWindow) {
                postInvalidateOnAnimation()
            }
        }
    }
}
