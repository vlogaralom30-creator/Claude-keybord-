package com.banglakeys.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Secret message লেখা/পড়ার screen (v2 — manual copy/paste, PLAN.md দেখুন):
 *  - একাধিক contact-এর জন্য আলাদা passphrase সেভ/পরিচালনা
 *  - পাঠানোর style বাছা (Runic/Dots/Shapes)
 *  - Encrypt: বাছা contact-এর passphrase + style দিয়ে
 *  - Decrypt: সেভ করা সব passphrase + সব style নিজে থেকে try করে — কে পাঠিয়েছে
 *    আগে থেকে বলা লাগে না
 *  - Auto-detect overlay চালু/বন্ধ করার সুইচ (আসল scanning
 *    AssistantAccessibilityService.kt-এ হয়, Accessibility চালু থাকতে হবে)
 */
class SecretComposeActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * d).toInt()

    private lateinit var contactsList: LinearLayout
    private lateinit var contactNameInput: EditText
    private lateinit var contactPassInput: EditText
    private lateinit var styleRow: LinearLayout
    private var selectedStyle: GlyphStyle = GlyphStyle.RUNIC
    private lateinit var encryptForLabel: TextView
    private var encryptForContact: String? = null

    private lateinit var plainInput: EditText
    private lateinit var glyphOutput: TextView
    private lateinit var glyphInput: EditText
    private lateinit var decodedOutput: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedStyle = SecretConfig.getStyle(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }

        root.addView(title("🔐 Secret Message", 24f))
        root.addView(title("একই passphrase ছাড়া কেউ (AI/human) এটা পড়তে পারবে না।", 13f))

        // ---------------- auto-detect toggle ----------------
        val adRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        adRow.addView(title("Screen-এ auto-detect (overlay)", 14f).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val adSwitch = Switch(this).apply { isChecked = SecretConfig.autoDetectEnabled(this@SecretComposeActivity) }
        adSwitch.setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
            SecretConfig.setAutoDetect(this, checked)
            if (checked) Toast.makeText(this, "Accessibility চালু থাকতে হবে (BanglaKeys Assistant)", Toast.LENGTH_LONG).show()
        }
        adRow.addView(adSwitch)
        root.addView(adRow)

        root.addView(divider())

        // ---------------- contacts / passphrases ----------------
        root.addView(title("👤 Contact ও Passphrase", 16f))
        contactsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(contactsList)

        contactNameInput = EditText(this).apply { hint = "নাম (যেমন: বন্ধু, পরিবার)" }
        root.addView(contactNameInput)
        contactPassInput = EditText(this).apply {
            hint = "তার সাথে ঠিক করা passphrase"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(contactPassInput)
        root.addView(button("💾 Contact সেভ করুন") {
            SecretConfig.setContact(this, contactNameInput.text.toString(), contactPassInput.text.toString())
            contactNameInput.setText("")
            contactPassInput.setText("")
            refreshContacts()
            Toast.makeText(this, "সেভ হয়েছে — শুধু এই ফোনেই থাকবে", Toast.LENGTH_SHORT).show()
        })

        root.addView(divider())

        // ---------------- style picker ----------------
        root.addView(title("🎨 Style (পাঠানোর জন্য)", 16f))
        styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(styleRow)
        refreshStyleRow()

        root.addView(divider())

        // ---------------- encrypt ----------------
        root.addView(title("✏️ লিখুন ও Encrypt করুন", 16f))
        encryptForLabel = title("কার জন্য: (নিচে থেকে একটা contact বাছুন)", 13f)
        root.addView(encryptForLabel)
        plainInput = EditText(this).apply { hint = "আসল লেখা…" }
        root.addView(plainInput)
        root.addView(button("🔒 Encrypt করুন") { encode() })
        glyphOutput = selectableText()
        root.addView(glyphOutput)
        root.addView(button("📋 Copy করুন") { copy(glyphOutput.text.toString()) })

        root.addView(divider())

        // ---------------- decrypt ----------------
        root.addView(title("🔓 পেস্ট করুন ও Decrypt করুন", 16f))
        root.addView(title("সেভ করা সব passphrase/style নিজে থেকে try করবে।", 12f))
        glyphInput = EditText(this).apply { hint = "পেস্ট করুন…" }
        root.addView(glyphInput)
        root.addView(button("🔓 Decrypt করুন") { decode() })
        decodedOutput = selectableText()
        root.addView(decodedOutput)

        setContentView(ScrollView(this).apply { addView(root) })
        refreshContacts()
    }

    // --------------------------------------------------------- contacts UI

    private fun refreshContacts() {
        contactsList.removeAllViews()
        val contacts = SecretConfig.getContacts(this)
        if (contacts.isEmpty()) {
            contactsList.addView(title("এখনো কোনো contact সেভ করা হয়নি।", 13f))
            return
        }
        for ((name, _) in contacts) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val label = title(if (name == encryptForContact) "✅ $name" else name, 14f).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    encryptForContact = name
                    encryptForLabel.text = "কার জন্য: $name"
                    refreshContacts()
                }
            }
            row.addView(label)
            row.addView(Button(this).apply {
                text = "✕"
                setOnClickListener {
                    SecretConfig.removeContact(this@SecretComposeActivity, name)
                    if (encryptForContact == name) encryptForContact = null
                    refreshContacts()
                }
            })
            contactsList.addView(row)
        }
    }

    // ----------------------------------------------------------- style UI

    private fun refreshStyleRow() {
        styleRow.removeAllViews()
        for (style in GlyphStyle.values()) {
            styleRow.addView(Button(this).apply {
                text = (if (style == selectedStyle) "✅ " else "") + style.label
                setOnClickListener {
                    selectedStyle = style
                    SecretConfig.setStyle(this@SecretComposeActivity, style)
                    refreshStyleRow()
                }
            })
        }
    }

    // ------------------------------------------------------- encrypt/decrypt

    private fun encode() {
        val contact = encryptForContact
        val pass = contact?.let { SecretConfig.getContacts(this)[it] }
        if (pass.isNullOrBlank()) {
            Toast.makeText(this, "আগে উপরে থেকে একটা contact বাছুন (নাম-এ tap করুন)", Toast.LENGTH_LONG).show()
            return
        }
        val text = plainInput.text.toString()
        if (text.isBlank()) return
        val bytes = SecretCipher.encrypt(text, pass)
        glyphOutput.text = GlyphAlphabet.encode(bytes, selectedStyle)
    }

    private fun decode() {
        val passphrases = SecretConfig.allPassphrases(this)
        if (passphrases.isEmpty()) {
            Toast.makeText(this, "আগে অন্তত একটা contact/passphrase সেভ করুন", Toast.LENGTH_SHORT).show()
            return
        }
        val glyph = glyphInput.text.toString()
        val bytes = GlyphAlphabet.decodeAny(glyph)
        val plain = bytes?.let { SecretCipher.decryptWithAny(it, passphrases) }
        decodedOutput.text = plain ?: "❌ পড়া গেল না (কোনো সেভ করা passphrase মিলছে না, বা নষ্ট/অসম্পূর্ণ text)"
    }

    private fun copy(text: String) {
        if (text.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("secret", text))
        Toast.makeText(this, "Copy হয়েছে", Toast.LENGTH_SHORT).show()
    }

    // ----------------------------------------------------------------- UI

    private fun title(text: String, size: Float): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setPadding(0, dp(10), 0, dp(4))
    }

    private fun selectableText(): TextView = TextView(this).apply {
        setTextIsSelectable(true)
        textSize = 18f
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(0x33000000)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).also {
            it.topMargin = dp(16)
        }
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }
}
