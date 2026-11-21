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
import android.widget.TextView
import android.widget.Toast
import com.example.MainActivity

/**
 * Setup + control screen (XML layout ছাড়া, কোড থেকেই আঁকা)।
 * এখান থেকে: Accessibility ON, permission, Assistant start/stop, ভাষা বদল, gesture test।
 */
class AssistantActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var btnService: Button
    private lateinit var btnLang: Button

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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

    private fun mark(ok: Boolean) = if (ok) "✅" else "❌"

    private fun refresh() {
        val lines = listOf(
            "${mark(AssistantSetup.isAccessibilityOn(this))} Accessibility (touch/scroll এর জন্য)",
            "${mark(AssistantSetup.hasMic(this))} Microphone",
            "${mark(AssistantSetup.has(this, android.Manifest.permission.READ_CONTACTS))} Contacts (call এর জন্য)",
            "${mark(AssistantSetup.has(this, android.Manifest.permission.CALL_PHONE))} Call permission",
            "${mark(AssistantSetup.canOverlay(this))} Display over other apps (background এ app খুলতে)",
            "${mark(AssistantListenerService.running)} Assistant এখন শুনছে"
        )
        status.text = lines.joinToString("\n")
        btnService.text = if (AssistantListenerService.running) "⏹ Assistant বন্ধ করুন" else "▶️ Assistant চালু করুন"
        btnLang.text = "🌐 শোনার ভাষা: " + if (AssistantSetup.isBangla(this)) "বাংলা (bn-BD)" else "English (en-US)"
    }
}
