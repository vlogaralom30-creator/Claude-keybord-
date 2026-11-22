package com.banglakeys.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

class BanglaIME : InputMethodService(), KeyboardView.Listener, VoiceInput.Listener {

    private var kv: KeyboardView? = null
    private lateinit var prefs: SharedPreferences

    private val buf = StringBuilder()      // phonetic roman buffer

    private var userBangla = true          // user-এর পছন্দ করা ভাষা
    private var bangla = true              // এই field-এ কার্যকর ভাষা
    private var symbols = 0                // 0 = অক্ষর, 1 = ?123, 2 = =\<
    private var inputClass = 0

    private var haptic = true
    private var sound = false
    private var numRow = false
    private var bnDigitsPref = true
    private var heightPct = 100
    private var inPanel = false

    private lateinit var suggestions: Suggestions
    private lateinit var notes: Notes
    private lateinit var clipboardMemory: ClipboardMemory
    private lateinit var voice: VoiceInput

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("bk", Context.MODE_PRIVATE)
        userBangla = prefs.getBoolean("bn", true)
        bangla = userBangla
        suggestions = Suggestions(this)
        notes = Notes(this)
        clipboardMemory = ClipboardMemory(this)
        clipboardMemory.start()
        voice = VoiceInput(this)
        voice.listener = this
    }

    override fun onDestroy() {
        clipboardMemory.stop()
        voice.destroy()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        kv = v
        loadPrefs()
        refreshLayout()
        return v
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        loadPrefs()
        buf.setLength(0)
        inPanel = false
        kv?.setComposing("")

        val type = info?.inputType ?: 0
        inputClass = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION

        val codeLike = inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI
            )

        bangla = userBangla && !codeLike
        symbols = when (inputClass) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> 1
            else -> 0
        }
        refreshLayout()
        kv?.setSuggestions(suggestions.suggestionsFor("", bangla))
        updateAutoShift()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (voice.listening) voice.stop()
        commitComposing()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        // user cursor অন্য জায়গায় নিলে composing বাদ
        if (buf.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            buf.setLength(0)
            kv?.setComposing("")
            kv?.setSuggestions(suggestions.suggestionsFor("", bangla))
            currentInputConnection?.finishComposingText()
        }
    }

    // ---------------- prefs / layout ----------------

    private fun loadPrefs() {
        haptic = prefs.getBoolean("haptic", true)
        sound = prefs.getBoolean("sound", false)
        numRow = prefs.getBoolean("numrow", false)
        bnDigitsPref = prefs.getBoolean("bndigits", true)
        heightPct = prefs.getInt("height", 100)
    }

    private fun spaceLabelFor(): String = if (bangla) "বাংলা" else "English"

    private fun refreshLayout() {
        val bnDigits = bangla && bnDigitsPref &&
            inputClass != InputType.TYPE_CLASS_NUMBER &&
            inputClass != InputType.TYPE_CLASS_PHONE
        val rows = when (symbols) {
            1 -> Layouts.symbols(bnDigits, bangla)
            2 -> Layouts.symbols2()
            else -> if (bangla) Layouts.phonetic(numRow, bnDigits) else Layouts.english(numRow)
        }
        kv?.setLayout(rows, spaceLabelFor(), heightPct / 100f)
        kv?.setEnterLabel(enterLabelFor(currentInputEditorInfo))
    }

    private fun enterLabelFor(info: EditorInfo?): String? {
        if (info == null) return null
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return null
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "SEARCH"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> null
        }
    }

    private fun updateAutoShift() {
        if (bangla || symbols != 0) return
        val ei = currentInputEditorInfo ?: return
        if (ei.inputType == InputType.TYPE_NULL) return
        val caps = currentInputConnection?.getCursorCapsMode(ei.inputType) ?: 0
        kv?.setAutoShift(caps != 0)
    }

    // ---------------- composing ----------------

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        if (buf.isEmpty()) {
            ic.finishComposingText()
            kv?.setComposing("")
            kv?.setSuggestions(suggestions.suggestionsFor("", bangla))
            return
        }
        val bn = Phonetic.convert(buf.toString())
        ic.setComposingText(bn, 1)
        kv?.setComposing(bn)
        kv?.setSuggestions(suggestions.suggestionsFor(buf.toString(), bangla))
    }

    private fun commitComposing() {
        if (buf.isEmpty()) return
        val bn = Phonetic.convert(buf.toString())
        suggestions.learn(buf.toString(), bn)
        currentInputConnection?.finishComposingText()
        buf.setLength(0)
        kv?.setComposing("")
        kv?.setSuggestions(suggestions.suggestionsFor("", bangla))
    }

    // ---------------- KeyboardView.Listener ----------------

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        val ch = text.singleOrNull()
        val phonetic = bangla && symbols == 0
        if (phonetic && ch != null &&
            (ch in 'a'..'z' || ch in 'A'..'Z' || ((ch == '^' || ch == '`') && buf.isNotEmpty()))
        ) {
            buf.append(ch)
            updateComposing()
            return
        }
        commitComposing()
        ic.commitText(text, 1)
    }

    override fun onDelete() {
        if (buf.isNotEmpty()) {
            buf.setLength(buf.length - 1)
            updateComposing()
            return
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun onSpace() {
        commitComposing()
        currentInputConnection?.commitText(" ", 1)
        updateAutoShift()
    }

    override fun onSpaceLongPress() {
        commitComposing()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    override fun onEnter() {
        commitComposing()
        suggestions.resetSentenceContext()
        kv?.setSuggestions(emptyList())
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        if (ei != null && (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
            if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        updateAutoShift()
    }

    override fun onSwitchLang() {
        commitComposing()
        userBangla = !userBangla
        prefs.edit().putBoolean("bn", userBangla).apply()
        bangla = userBangla
        inPanel = false
        symbols = 0
        refreshLayout()
        kv?.setSuggestions(suggestions.suggestionsFor("", bangla))
        updateAutoShift()
    }

    override fun onSymbols(mode: Int) {
        commitComposing()
        inPanel = false
        symbols = mode
        refreshLayout()
    }

    override fun onComposingTap() {
        commitComposing()
    }

    override fun onSuggestionTap(word: String) {
        val ic = currentInputConnection ?: return
        if (buf.isNotEmpty()) suggestions.learn(buf.toString(), word)
        buf.setLength(0)
        kv?.setComposing("")
        ic.commitText("$word ", 1)
        kv?.setSuggestions(suggestions.suggestionsFor("", bangla))
    }

    override fun onSnippet(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onNotepad() {
        commitComposing()
        inPanel = !inPanel
        if (inPanel) {
            notes = Notes(this)
            val items = notes.all() + clipboardMemory.clips()
            kv?.setLayout(Layouts.panel(items), spaceLabelFor(), heightPct / 100f)
        } else {
            refreshLayout()
        }
    }

    override fun onAssistant() {
        if (!AssistantSetup.isReady(this)) {
            val i = Intent(this, AssistantActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            return
        }
        val isPtt = AssistantPrefs.listeningMode(this) == ListeningMode.PUSH_TO_TALK
        if (AssistantListenerService.running) {
            if (isPtt) {
                AssistantListenerService.triggerPtt(this)
            } else {
                AssistantListenerService.stop(this)
                android.widget.Toast.makeText(this, "Assistant বন্ধ", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            if (isPtt) {
                AssistantListenerService.triggerPtt(this)
            } else {
                AssistantListenerService.start(this)
                android.widget.Toast.makeText(
                    this, "Assistant চালু — \"Hey Bot\" বলুন", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onVoice() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val i = Intent(this, VoicePermissionActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            return
        }
        if (voice.listening) {
            voice.stop()
        } else {
            commitComposing()
            AssistantListenerService.setPaused(true)
            kv?.setSuggestions(emptyList())
            kv?.setComposing("শুনছি…")
            voice.start(bangla)
        }
    }

    override fun onVoicePartial(text: String) {
        kv?.setSuggestions(emptyList())
        kv?.setComposing("শুনছি: $text")
    }

    override fun onVoiceResult(text: String) {
        currentInputConnection?.commitText("$text ", 1)
    }

    override fun onVoiceError(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onVoiceEnd() {
        AssistantListenerService.setPaused(false)
        kv?.setComposing("")
    }

    override fun onTranslate() {
        val ic = currentInputConnection ?: return
        val text = if (buf.isNotEmpty()) Phonetic.convert(buf.toString())
        else ic.getSelectedText(0)?.toString()
        if (text.isNullOrBlank()) return
        val from = if (bangla) "bn" else "en"
        val to = if (bangla) "en" else "bn"
        Translator.translate(text, from, to) { result ->
            if (result != null) {
                commitComposing()
                ic.commitText(result, 1)
            }
        }
    }

    override fun onFeedback() {
        if (haptic) {
            kv?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        if (sound) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }
}
