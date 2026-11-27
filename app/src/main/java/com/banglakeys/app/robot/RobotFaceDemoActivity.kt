package com.banglakeys.app.robot

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Phase 1 standalone test — উপরে RobotBrain-চালিত face (`RobotFaceView`),
 * নিচে প্রতিটি `RobotEvent`-এর জন্য বাটন এবং মেমোরি রিসেট বাটন।
 */
class RobotFaceDemoActivity : Activity() {

    lateinit var faceView: RobotFaceView
        private set

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1017"))
        }

        // Edge-to-edge safe padding for status & navigation bars on API 35/36
        root.setOnApplyWindowInsetsListener { v, insets ->
            val top = if (android.os.Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }
            val bottom = if (android.os.Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            v.setPadding(0, top, 0, bottom)
            insets
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(12), dp(4))
        }
        val backBtn = Button(this).apply {
            text = "← ফিরে যান"
            textSize = 12f
            tag = "robot_demo_back_btn"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1D2330"))
            minimumHeight = dp(48)
            setOnClickListener { finish() }
        }
        val titleView = TextView(this).apply {
            text = "🤖 EMO Face + Brain"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(12), 0, 0, 0)
        }
        topBar.addView(backBtn)
        topBar.addView(titleView)
        root.addView(
            topBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        faceView = RobotFaceView(this).apply {
            tag = "robot_face_view"
        }
        root.addView(
            faceView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.05f)
        )

        val buttonArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        buttonArea.addView(TextView(this).apply {
            text = "🧪 Fake Event (ট্যাপ করে Brain পরীক্ষা করুন)"
            setTextColor(Color.parseColor("#CFD8E3"))
            textSize = 13.5f
            setPadding(dp(4), 0, 0, dp(6))
        })

        val events: List<Triple<String, String, RobotEvent>> = listOf(
            Triple("event_tap", "👋 Tap", RobotEvent.Tap),
            Triple("event_long_press", "🤗 Long press (আদর)", RobotEvent.LongPress),
            Triple("event_shake", "📳 Shake (ঝাঁকি)", RobotEvent.Shake),
            Triple("event_lift", "✋ Lift (তোলা)", RobotEvent.Lift),
            Triple("event_face_down", "⬇️ Face down (জোর ঘুম)", RobotEvent.FaceDown),
            Triple("event_face_up", "⬆️ Face up (জাগা)", RobotEvent.FaceUp),
            Triple("event_clap", "👏 Clap", RobotEvent.Clap),
            Triple("event_loud_sound", "🔊 Loud sound", RobotEvent.LoudSound),
            Triple("event_voice_heard", "🎙️ Voice heard", RobotEvent.VoiceHeard),
            Triple("event_battery_low", "🔋 Battery low", RobotEvent.BatteryLow),
            Triple("event_charging", "⚡ Charging", RobotEvent.Charging),
            Triple("event_dark", "🌑 Dark", RobotEvent.Dark),
            Triple("event_bright", "☀️ Bright", RobotEvent.Bright),
            Triple("event_typing_fast", "⌨️ Typing fast", RobotEvent.TypingFast),
            Triple("event_typing_slow", "🐢 Typing slow", RobotEvent.TypingSlow),
            Triple("event_backspace_burst", "⌫ Backspace burst", RobotEvent.BackspaceBurst),
            Triple("event_keyword_love", "❤️ Keyword: love", RobotEvent.KeywordLove),
            Triple("event_keyword_anger", "😡 Keyword: anger", RobotEvent.KeywordAnger),
            Triple("event_message_sent", "📤 Message sent", RobotEvent.MessageSent),
        )

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        events.forEachIndexed { i, (btnTag, label, event) ->
            if (i % 2 == 0 && i != 0) {
                grid.addView(row)
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            }
            row.addView(Button(this).apply {
                text = label
                textSize = 11.5f
                tag = btnTag
                minimumHeight = dp(48)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    EmoBrainHub.handleEvent(event)
                    faceView.invalidate()
                }
            })
        }
        if (events.size % 2 == 1) {
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
        }
        grid.addView(row)
        buttonArea.addView(grid)

        buttonArea.addView(Button(this).apply {
            text = "🗑️ সব Memory মুছে ফেলো (reset)"
            tag = "btn_reset_memory"
            minimumHeight = dp(48)
            setOnClickListener {
                EmoBrainHub.resetMemory()
                faceView.invalidate()
            }
        })

        val scroll = ScrollView(this).apply { addView(buttonArea) }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
    }
}
