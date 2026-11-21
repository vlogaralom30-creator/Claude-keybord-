package com.banglakeys.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity

/**
 * Setup + control screen (XML layout ছাড়া, কোড থেকেই আঁকা)।
 * এখান থেকে: Accessibility ON, permission, Assistant start/stop, ভাষা বদল, SAF storage folder, Controls, gesture test।
 */
class AssistantActivity : Activity() {

    companion object {
        private const val REQ_STORAGE_TREE = 202
    }

    private lateinit var status: TextView
    private lateinit var btnService: Button
    private lateinit var btnLang: Button
    private lateinit var btnStorageFolder: Button
    private lateinit var swConfirmAppCall: Switch
    private lateinit var swConfirmHumanSend: Switch
    private lateinit var swConfirmAiChatSend: Switch
    private lateinit var timeoutLabel: TextView
    private lateinit var timeoutSeek: SeekBar

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(16)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        fun addButton(label: String, onClick: () -> Unit): Button {
            val b = Button(this)
            b.text = label
            b.isAllCaps = false
            b.setOnClickListener { onClick() }
            root.addView(b)
            return b
        }

        val title = TextView(this)
        title.text = "🤖 BanglaKeys Assistant"
        title.textSize = 22f
        root.addView(title)

        status = TextView(this)
        status.textSize = 15f
        status.setPadding(0, dp(12), 0, dp(12))
        root.addView(status)

        addButton("১) Accessibility চালু করুন") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        addButton("   (Android 13+: 'Restricted setting' এলে — App info খুলুন)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
        addButton("২) Mic / Contacts / Call / Notification permission") {
            requestPermissions(AssistantSetup.runtimePermissions(), 201)
        }
        addButton("৩) Display over other apps permission") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        btnService = addButton("") {
            if (AssistantListenerService.running) {
                AssistantListenerService.stop(this)
            } else if (!AssistantSetup.isReady(this)) {
                Toast.makeText(this, "আগে Accessibility আর Mic permission দিন", Toast.LENGTH_LONG).show()
            } else {
                AssistantListenerService.start(this)
            }
            Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 400)
        }
        btnLang = addButton("") {
            AssistantSetup.setBangla(this, !AssistantSetup.isBangla(this))
            refresh()
            Toast.makeText(this, "ভাষা বদলাতে Assistant বন্ধ করে আবার চালু করুন", Toast.LENGTH_SHORT).show()
        }
        addButton("🧪 টেস্ট: ৩ সেকেন্ড পর 'up' swipe (TikTok/Chrome খুলে রাখুন)") {
            Toast.makeText(this, "৩ সেকেন্ডে অন্য app-এ যান…", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                val msg = CommandExecutor(applicationContext).execute(Command.Swipe(Dir.UP))
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            }, 3000)
        }

        // ---------------- Controls (R5) ----------------
        val controlsHeader = TextView(this).apply {
            text = "⚙️ Controls (নিয়ন্ত্রণ ও স্টোরেজ)"
            textSize = 18f
            setPadding(0, dp(18), 0, dp(6))
        }
        root.addView(controlsHeader)

        val storageNote = TextView(this).apply {
            text = "📁 ফাইল/ফোল্ডার তৈরির জন্য আপনি নিজে নিচের বাটনে ট্যাপ করে একটি ফোল্ডার বেছে নিতে হবে (অ্যাপ নিজে থেকে অনুমোদন নিতে পারে না):"
            textSize = 13f
            setPadding(0, dp(4), 0, dp(6))
        }
        root.addView(storageNote)

        btnStorageFolder = addButton("") {
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

        @Suppress("UseSwitchCompatOrMaterialCode")
        fun addSwitchRow(label: String, initial: Boolean, onChange: (Boolean) -> Unit): Switch {
            val sw = Switch(this).apply {
                text = label
                isChecked = initial
                setPadding(0, dp(8), 0, dp(8))
                setOnCheckedChangeListener { _, checked ->
                    onChange(checked)
                }
            }
            root.addView(sw)
            return sw
        }

        swConfirmAppCall = addSwitchRow(
            "App call (imo/WhatsApp/Messenger) এর আগে confirm",
            AssistantPrefs.confirmBeforeAppCall(this)
        ) { AssistantPrefs.setConfirmBeforeAppCall(this, it) }

        swConfirmHumanSend = addSwitchRow(
            "মেসেজ পাঠানোর আগে confirm (WhatsApp/imo/Messenger)",
            AssistantPrefs.confirmBeforeHumanSend(this)
        ) { AssistantPrefs.setConfirmBeforeHumanSend(this, it) }

        swConfirmAiChatSend = addSwitchRow(
            "AI chat (Claude/ChatGPT/Gemini) send-এর আগে confirm",
            AssistantPrefs.confirmBeforeAiChatSend(this)
        ) { AssistantPrefs.setConfirmBeforeAiChatSend(this, it) }

        timeoutLabel = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(timeoutLabel)

        timeoutSeek = SeekBar(this).apply {
            max = AssistantPrefs.MAX_FLOW_TIMEOUT_SEC - AssistantPrefs.MIN_FLOW_TIMEOUT_SEC
            progress = AssistantPrefs.appFlowTimeoutSec(this@AssistantActivity) - AssistantPrefs.MIN_FLOW_TIMEOUT_SEC
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val sec = AssistantPrefs.MIN_FLOW_TIMEOUT_SEC + progress
                    timeoutLabel.text = "⏱ App-flow timeout: $sec সেকেন্ড (2–15)"
                    if (fromUser) AssistantPrefs.setAppFlowTimeoutSec(this@AssistantActivity, sec)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(timeoutSeek)

        val returnSpacer = TextView(this).apply { setPadding(0, dp(8), 0, dp(0)) }
        root.addView(returnSpacer)

        addButton("⌨️ Bangla Keys Keyboard ও Settings-এ ফিরুন") {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

        val scroll = ScrollView(this).apply {
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
                Toast.makeText(this, "✅ Assistant storage folder সেভ হয়েছে", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }
    }

    private fun mark(ok: Boolean) = if (ok) "✅" else "❌"

    private fun refresh() {
        val a11yOn = AssistantSetup.isAccessibilityOn(this)
        val a11yConnected = AssistantAccessibilityService.instance != null
        val storageOk = AssistantSetup.hasStorageTree(this)
        val alarmOk = AssistantSetup.hasSetAlarm(this)

        val lines = listOf(
            "${mark(a11yOn)} Accessibility চালু (${if (a11yConnected) "connected" else "সংযুক্ত নয়"})",
            "${mark(AssistantSetup.hasMic(this))} Microphone",
            "${mark(AssistantSetup.has(this, android.Manifest.permission.READ_CONTACTS))} Contacts (call / email এর জন্য)",
            "${mark(AssistantSetup.has(this, android.Manifest.permission.CALL_PHONE))} Call permission",
            "${mark(AssistantSetup.canOverlay(this))} Display over other apps (background এ app খুলতে)",
            "${mark(alarmOk)} Alarm / Timer permission (SET_ALARM)",
            "${mark(storageOk)} Assistant storage folder ${if (storageOk) "(বেছে নেওয়া আছে)" else "(ট্যাপ করে বাছুন)"}",
            "${mark(AssistantListenerService.running)} Assistant এখন শুনছে"
        )
        status.text = lines.joinToString("\n")
        btnService.text = if (AssistantListenerService.running) "⏹ Assistant বন্ধ করুন" else "▶️ Assistant চালু করুন"
        btnLang.text = "🌐 শোনার ভাষা: " + if (AssistantSetup.isBangla(this)) "বাংলা (bn-BD)" else "English (en-US)"
        btnStorageFolder.text = (if (storageOk) "✅ " else "📁 ") + "Assistant storage folder বেছে নিন"

        swConfirmAppCall.isChecked = AssistantPrefs.confirmBeforeAppCall(this)
        swConfirmHumanSend.isChecked = AssistantPrefs.confirmBeforeHumanSend(this)
        swConfirmAiChatSend.isChecked = AssistantPrefs.confirmBeforeAiChatSend(this)

        val sec = AssistantPrefs.appFlowTimeoutSec(this)
        timeoutLabel.text = "⏱ App-flow timeout: $sec সেকেন্ড (2–15)"
        val expectedProg = sec - AssistantPrefs.MIN_FLOW_TIMEOUT_SEC
        if (timeoutSeek.progress != expectedProg) timeoutSeek.progress = expectedProg
    }
}
