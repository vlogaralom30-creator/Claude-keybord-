package com.banglakeys.app

import android.content.ClipData
import android.content.ClipboardManager
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
import android.widget.LinearLayout
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BanglaIME : InputMethodService(), KeyboardView.Listener, VoiceInput.Listener {

    private enum class NoteEditTarget { NEW_NOTE, PINNED_NOTE, RECENT_CLIP }

    private var host: KeyboardHostLayout? = null
    private var secretBannerView: SecretDecodeBannerView? = null
    private var kv: KeyboardView? = null
    private var panelView: NotepadClipboardPanelView? = null
    private var emojiPanelView: EmojiPanelView? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var secretDetector: SecretClipboardDetector
    private var secretClipManager: ClipboardManager? = null
    private var secretClipListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastAllowSecretBanner = true
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bannerCollectorJob: Job? = null
    private var renderedBannerEventId: Long = -1L

    private val buf = StringBuilder()      // active roman/word prefix buffer
    private var secretMode = false
    private val secretText = StringBuilder()

    private var noteEditMode = false
    private var noteEditTarget = NoteEditTarget.NEW_NOTE
    private var noteEditIndex = -1
    private var noteEditOriginalText = ""
    private val noteEditBuf = StringBuilder()
    private val noteEditRoman = StringBuilder()

    private var userBangla = true          // user-এর পছন্দ করা ভাষা
    private var bangla = true              // এই field-এ কার্যকর ভাষা
    private var symbols = 0                // 0 = অক্ষর, 1 = ?123, 2 = =\<
    private var inputClass = 0

    private var allowSuggest = true
    private var allowLearn = true

    private var haptic = true
    private var sound = false
    private var numRow = false
    private var bnDigitsPref = true
    private var heightPct = 100

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
        secretDetector = SecretBannerStateHolder.detector(this)
        if (SecretConfig.clipboardMonitorEnabled(this)) {
            SecretClipboardMonitorService.start(this)
        }
        startSecretClipboardListener()
        voice = VoiceInput(this)
        voice.listener = this
    }

    override fun onDestroy() {
        bannerCollectorJob?.cancel()
        serviceScope.cancel()
        stopSecretClipboardListener()
        clipboardMemory.stop()
        voice.destroy()
        // voice.destroy() মাঝপথে হলে assistant যেন "paused" হয়ে আটকে না থাকে
        AssistantListenerService.setPaused(false)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val banner = SecretDecodeBannerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val h = KeyboardHostLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val v = h.keyboardView
        val pv = h.panelView
        val ev = h.emojiPanelView
        v.listener = this

        banner.listener = object : SecretDecodeBannerView.Listener {
            override fun onReplyClicked(senderName: String) {
                renderedBannerEventId = -1L
                SecretBannerStateHolder.dismiss()
                SecretConfig.setActiveContact(this@BanglaIME, senderName)
                enterSecretModeFor(senderName)
                host?.setMode(KeyboardHostLayout.HostMode.KEYBOARD)
            }

            override fun onCopyPlainClicked(plainText: String) {
                ClipboardMemory.ignoreNext(plainText)
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("decoded", plainText))
                Toast.makeText(this@BanglaIME, "📋 ডিকোড করা লেখা কপি হয়েছে", Toast.LENGTH_SHORT).show()
            }

            override fun onOpenVaultClicked() {
                renderedBannerEventId = -1L
                SecretBannerStateHolder.dismiss()
                val i = Intent(this@BanglaIME, SecretComposeActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }

            override fun onDismissed() {
                renderedBannerEventId = -1L
                SecretBannerStateHolder.dismiss()
            }
        }

        pv.listener = object : NotepadClipboardPanelView.Listener {
            override fun onBackToKeyboard() {
                host?.setMode(KeyboardHostLayout.HostMode.KEYBOARD)
            }

            override fun onPasteItem(text: String) {
                commitComposing()
                currentInputConnection?.commitText(text, 1)
                host?.setMode(KeyboardHostLayout.HostMode.KEYBOARD)
                refreshStrip()
                updateAutoShift()
            }

            override fun onAddNewNote() {
                startNoteEdit(NoteEditTarget.NEW_NOTE, -1, "")
            }

            override fun onPinRecent(text: String) {
                notes = Notes(this@BanglaIME)
                notes.add(text)
                refreshPanelData()
                Toast.makeText(this@BanglaIME, "📌 Pinned হিসেবে সেভ হয়েছে", Toast.LENGTH_SHORT).show()
            }

            override fun onUnpinNote(index: Int, text: String) {
                notes = Notes(this@BanglaIME)
                if (!notes.removeByText(text)) notes.remove(index)
                clipboardMemory.add(text)
                refreshPanelData()
            }

            override fun onEditPinned(index: Int, text: String) {
                startNoteEdit(NoteEditTarget.PINNED_NOTE, index, text)
            }

            override fun onEditRecent(text: String) {
                startNoteEdit(NoteEditTarget.RECENT_CLIP, -1, text)
            }

            override fun onRemovePinned(index: Int, text: String) {
                notes = Notes(this@BanglaIME)
                if (!notes.removeByText(text)) notes.remove(index)
                refreshPanelData()
            }

            override fun onRemoveRecent(text: String) {
                clipboardMemory.remove(text)
                refreshPanelData()
            }

            override fun onClearRecent() {
                clipboardMemory.clear()
                refreshPanelData()
            }

            override fun onDecodeCipher(text: String) {
                SecretClipboardMonitorService.inspectNow(this@BanglaIME, text, force = true)
            }
        }

        ev.listener = object : EmojiPanelView.Listener {
            override fun onBackToKeyboard() {
                host?.setMode(KeyboardHostLayout.HostMode.KEYBOARD)
            }

            override fun onEmojiSelected(emoji: String) {
                onFeedback()
                when {
                    noteEditMode -> {
                        commitNoteEditRoman()
                        noteEditBuf.append(emoji)
                        updateNoteEditStrip()
                    }
                    secretMode -> {
                        secretText.append(emoji)
                        kv?.setComposing("🔐 " + secretPlain())
                    }
                    else -> {
                        commitComposing()
                        currentInputConnection?.commitText(emoji, 1)
                        refreshStrip()
                        updateAutoShift()
                    }
                }
            }

            override fun onBackspace() {
                onFeedback()
                when {
                    noteEditMode -> onDelete()
                    secretMode -> onDelete()
                    else -> {
                        if (buf.isNotEmpty()) {
                            onDelete()
                            return
                        }
                        val ic = currentInputConnection ?: return
                        val before = ic.getTextBeforeCursor(16, 0)?.toString().orEmpty()
                        if (before.isNotEmpty()) {
                            val after = EmojiRepository.deleteLastGrapheme(before)
                            val dropCount = before.length - after.length
                            if (dropCount > 1) {
                                ic.deleteSurroundingText(dropCount, 0)
                                refreshStrip()
                                updateAutoShift()
                                return
                            }
                        }
                        onDelete()
                    }
                }
            }
        }

        root.addView(banner)
        root.addView(h)

        secretBannerView = banner
        host = h
        kv = v
        panelView = pv
        emojiPanelView = ev
        bannerCollectorJob?.cancel()
        bannerCollectorJob = serviceScope.launch {
            SecretBannerStateHolder.events.collectLatest { event ->
                renderBannerEvent(event)
            }
        }
        loadPrefs()
        refreshLayout()
        refreshStrip()
        return root
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        loadPrefs()
        buf.setLength(0)
        // অন্য field-এ গেলে secret mode ও note-edit ছেড়ে দিই, নইলে লেখা হারিয়ে যেত
        if (!restarting) {
            secretMode = false
            secretText.setLength(0)
            kv?.setSecretMode(false)
        }
        noteEditMode = false
        noteEditBuf.setLength(0)
        noteEditRoman.setLength(0)
        kv?.setNoteEditMode(false)
        host?.setMode(KeyboardHostLayout.HostMode.KEYBOARD)

        val type = info?.inputType ?: 0
        inputClass = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION

        val isPassword = inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
        val isNumericField = inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME

        val codeLike = isPassword || (inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI
            ))

        allowSuggest = !isPassword && !isNumericField
        allowLearn = !codeLike
        lastAllowSecretBanner = !isPassword && !isNumericField

        bangla = userBangla && !codeLike
        symbols = when (inputClass) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> 1
            else -> 0
        }
        refreshLayout()
        refreshStrip()
        updateAutoShift()

        if (!lastAllowSecretBanner) {
            renderedBannerEventId = -1L
            SecretBannerStateHolder.dismiss()
            secretBannerView?.hide()
        } else {
            if (SecretConfig.clipboardMonitorEnabled(this) && !SecretClipboardMonitorService.running.value) {
                SecretClipboardMonitorService.start(this)
            }
            renderBannerEvent(SecretBannerStateHolder.activeEvent())
            checkClipboardForSecret(force = false)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (voice.listening) voice.stop()
        renderedBannerEventId = -1L
        SecretBannerStateHolder.dismiss()
        secretBannerView?.hide()
        secretMode = false
        secretText.setLength(0)
        kv?.setSecretMode(false)
        noteEditMode = false
        noteEditBuf.setLength(0)
        noteEditRoman.setLength(0)
        kv?.setNoteEditMode(false)
        host?.setMode(KeyboardHostLayout.HostMode.KEYBOARD)
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
        if (buf.isEmpty()) return

        if (bangla) {
            // Bangla phonetic mode-এ Android composing span সরে গেলে composing বাদ
            if (newSelStart != candidatesEnd || newSelEnd != candidatesEnd) {
                buf.setLength(0)
                currentInputConnection?.finishComposingText()
                refreshStrip()
            }
        } else {
            // English mode-এ user cursor অন্যত্র সরালে buffer সিঙ্ক রাখা হয়
            val before = currentInputConnection?.getTextBeforeCursor(buf.length, 0)?.toString()
            if (newSelStart != newSelEnd || (before != null && before != buf.toString())) {
                buf.setLength(0)
                refreshStrip()
            }
        }
    }

    // ---------------- prefs / layout ----------------

    private fun loadPrefs() {
        haptic = prefs.getBoolean("haptic", true)
        sound = prefs.getBoolean("sound", false)
        numRow = prefs.getBoolean("numrow", false)
        bnDigitsPref = prefs.getBoolean("bndigits", true)
        heightPct = prefs.getInt("height", 100)
        secretBannerView?.reloadTheme()
        kv?.reloadTheme()
        panelView?.reloadTheme()
        emojiPanelView?.reloadTheme()
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

    // ---------------- composing & strip sync ----------------

    private fun refreshStrip() {
        if (!allowSuggest) {
            kv?.setComposing("")
            kv?.setSuggestions(emptyList())
            return
        }
        val activePrefix = buf.toString()
        val composingDisplay = when {
            activePrefix.isEmpty() -> ""
            bangla -> Phonetic.convert(activePrefix)
            else -> activePrefix
        }
        kv?.setComposing(composingDisplay)
        kv?.setSuggestions(suggestions.suggestionsFor(activePrefix, bangla))
    }

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        if (buf.isEmpty()) {
            if (bangla) ic.finishComposingText()
            refreshStrip()
            return
        }
        if (bangla) {
            val bn = Phonetic.convert(buf.toString())
            ic.setComposingText(bn, 1)
        }
        refreshStrip()
    }

    private fun commitComposing() {
        if (buf.isEmpty()) return
        val raw = buf.toString()
        val finalWord = if (bangla) Phonetic.convert(raw) else raw
        if (allowLearn) {
            suggestions.learn(raw, finalWord, bangla)
        }
        if (bangla) {
            currentInputConnection?.finishComposingText()
        }
        buf.setLength(0)
        refreshStrip()
    }

    // ---------------- KeyboardView.Listener ----------------

    override fun onText(text: String) {
        if (noteEditMode) {
            val ch = text.singleOrNull()
            val phoneticBn = bangla && symbols == 0
            if (phoneticBn && ch != null &&
                (ch in 'a'..'z' || ch in 'A'..'Z' || ((ch == '^' || ch == '`') && noteEditRoman.isNotEmpty()))
            ) {
                noteEditRoman.append(ch)
            } else {
                commitNoteEditRoman()
                noteEditBuf.append(text)
            }
            updateNoteEditStrip()
            return
        }
        val ic = currentInputConnection ?: return
        if (secretMode) {
            secretText.append(text)
            kv?.setComposing("🔐 " + secretPlain())
            return
        }
        val ch = text.singleOrNull()
        val phoneticBn = bangla && symbols == 0
        val englishWord = !bangla && symbols == 0 && allowSuggest

        if (phoneticBn && ch != null &&
            (ch in 'a'..'z' || ch in 'A'..'Z' || ((ch == '^' || ch == '`') && buf.isNotEmpty()))
        ) {
            buf.append(ch)
            updateComposing()
            return
        }

        if (englishWord && ch != null &&
            (ch in 'a'..'z' || ch in 'A'..'Z' || (ch == '\'' && buf.isNotEmpty()))
        ) {
            buf.append(ch)
            ic.commitText(ch.toString(), 1)
            refreshStrip()
            return
        }

        commitComposing()
        ic.commitText(text, 1)
        if (text in SENTENCE_TERMINATORS) {
            suggestions.resetSentenceContext()
        }
        refreshStrip()
        updateAutoShift()
    }

    override fun onDelete() {
        if (noteEditMode) {
            when {
                noteEditRoman.isNotEmpty() -> noteEditRoman.deleteCharAt(noteEditRoman.length - 1)
                noteEditBuf.isNotEmpty() -> {
                    val trimmed = EmojiRepository.deleteLastGrapheme(noteEditBuf.toString())
                    noteEditBuf.setLength(0)
                    noteEditBuf.append(trimmed)
                }
            }
            updateNoteEditStrip()
            return
        }
        if (secretMode) {
            if (secretText.isNotEmpty()) {
                val trimmed = EmojiRepository.deleteLastGrapheme(secretText.toString())
                secretText.setLength(0)
                secretText.append(trimmed)
            }
            kv?.setComposing(if (secretText.isEmpty()) "" else "🔐 " + secretPlain())
            return
        }
        if (buf.isNotEmpty()) {
            buf.setLength(buf.length - 1)
            if (bangla) {
                updateComposing()
                return
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                refreshStrip()
                updateAutoShift()
                return
            }
        }

        // English মোডে আগের লেখা শব্দের শেষে ব্যাকস্পেস করলে prefix পুনরুদ্ধার
        if (!bangla && symbols == 0 && allowSuggest) {
            val before = currentInputConnection?.getTextBeforeCursor(32, 0)?.toString()
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            if (!before.isNullOrEmpty()) {
                val afterDel = before.dropLast(1)
                val trailing = extractTrailingEnglishWord(afterDel)
                buf.append(trailing)
            }
            refreshStrip()
            updateAutoShift()
            return
        }

        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoShift()
    }

    override fun onSpace() {
        if (noteEditMode) {
            commitNoteEditRoman()
            noteEditBuf.append(" ")
            updateNoteEditStrip()
            return
        }
        if (secretMode) {
            secretText.append(" ")
            kv?.setComposing("🔐 " + secretPlain())
            return
        }
        commitComposing()
        currentInputConnection?.commitText(" ", 1)
        refreshStrip()
        updateAutoShift()
    }

    override fun onSpaceLongPress() {
        commitComposing()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    override fun onEnter() {
        if (noteEditMode) {
            onNoteEditSave()
            return
        }
        if (secretMode) {
            sendSecretMessage()
            return
        }
        commitComposing()
        suggestions.resetSentenceContext()
        refreshStrip()
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
        commitNoteEditRoman()
        userBangla = !userBangla
        prefs.edit().putBoolean("bn", userBangla).apply()
        bangla = userBangla
        symbols = 0
        refreshLayout()
        if (noteEditMode) updateNoteEditStrip() else refreshStrip()
        updateAutoShift()
    }

    override fun onSymbols(mode: Int) {
        commitComposing()
        commitNoteEditRoman()
        symbols = mode
        refreshLayout()
        if (noteEditMode) updateNoteEditStrip() else refreshStrip()
    }

    override fun onComposingTap() {
        if (noteEditMode) {
            commitNoteEditRoman()
            updateNoteEditStrip()
            return
        }
        commitComposing()
        updateAutoShift()
    }

    override fun onSuggestionTap(word: String) {
        val ic = currentInputConnection ?: return
        val typedLen = buf.length
        if (typedLen > 0) {
            if (allowLearn) {
                suggestions.learn(buf.toString(), word, bangla)
            }
            if (!bangla) {
                // English-এ আগে commit হওয়া partial prefix মুছে সম্পূর্ণ শব্দ বসানো হয়
                val before = ic.getTextBeforeCursor(typedLen, 0)?.toString()
                if (before != null && before == buf.toString()) {
                    ic.deleteSurroundingText(typedLen, 0)
                }
            }
        } else if (allowLearn) {
            suggestions.learn("", word, bangla)
        }
        buf.setLength(0)
        ic.commitText("$word ", 1)
        refreshStrip()
        updateAutoShift()
    }

    override fun onSuggestionLongPress(word: String) {
        val removed = suggestions.forget(word, bangla)
        if (removed) {
            Toast.makeText(this, "“$word” suggestion তালিকা থেকে সরানো হয়েছে", Toast.LENGTH_SHORT).show()
            refreshStrip()
        } else {
            Toast.makeText(this, "ডিফল্ট শব্দ মুছে ফেলা যায় না", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSnippet(text: String) {
        if (noteEditMode) {
            commitNoteEditRoman()
            noteEditBuf.append(text)
            updateNoteEditStrip()
            return
        }
        commitComposing()
        currentInputConnection?.commitText(text, 1)
        refreshStrip()
    }

    override fun onEmojiToggle() {
        commitComposing()
        if (host?.mode == KeyboardHostLayout.HostMode.EMOJI) {
            host?.setMode(KeyboardHostLayout.HostMode.KEYBOARD)
        } else {
            emojiPanelView?.openPanel()
            host?.setMode(KeyboardHostLayout.HostMode.EMOJI)
        }
    }

    override fun onNotepad() {
        commitComposing()
        if (noteEditMode) {
            exitNoteEdit(returnToPanel = false)
        }
        if (host?.mode == KeyboardHostLayout.HostMode.CLIPBOARD) {
            host?.setMode(KeyboardHostLayout.HostMode.KEYBOARD)
        } else {
            refreshPanelData()
            host?.setMode(KeyboardHostLayout.HostMode.CLIPBOARD)
        }
    }

    override fun onNoteEditSave() {
        if (!noteEditMode) return
        commitNoteEditRoman()
        val finalText = noteEditBuf.toString().trim()
        if (finalText.isEmpty()) {
            Toast.makeText(this, "খালি নোট সেভ করা যাবে না", Toast.LENGTH_SHORT).show()
            return
        }
        when (noteEditTarget) {
            NoteEditTarget.NEW_NOTE -> {
                notes = Notes(this)
                notes.add(finalText)
            }
            NoteEditTarget.PINNED_NOTE -> {
                notes = Notes(this)
                if (!notes.updateByText(noteEditOriginalText, finalText)) {
                    notes.update(noteEditIndex, finalText)
                }
            }
            NoteEditTarget.RECENT_CLIP -> {
                if (!clipboardMemory.update(noteEditOriginalText, finalText)) {
                    notes = Notes(this)
                    notes.add(finalText)
                }
            }
        }
        exitNoteEdit(returnToPanel = true)
        Toast.makeText(this, "✓ নোট সেভ হয়েছে", Toast.LENGTH_SHORT).show()
    }

    override fun onNoteEditCancel() {
        if (!noteEditMode) return
        exitNoteEdit(returnToPanel = true)
    }

    private fun refreshPanelData() {
        notes = Notes(this)
        clipboardMemory.reload()
        panelView?.setData(notes.all(), clipboardMemory.clips())
    }

    private fun startNoteEdit(target: NoteEditTarget, index: Int, initialText: String) {
        commitComposing()
        noteEditMode = true
        noteEditTarget = target
        noteEditIndex = index
        noteEditOriginalText = initialText
        noteEditBuf.setLength(0)
        noteEditBuf.append(initialText)
        noteEditRoman.setLength(0)
        symbols = 0
        refreshLayout()
        kv?.setNoteEditMode(true)
        updateNoteEditStrip()
        host?.setMode(KeyboardHostLayout.HostMode.KEYBOARD)
    }

    private fun exitNoteEdit(returnToPanel: Boolean) {
        noteEditMode = false
        noteEditBuf.setLength(0)
        noteEditRoman.setLength(0)
        kv?.setNoteEditMode(false)
        if (returnToPanel) {
            refreshPanelData()
            host?.setMode(KeyboardHostLayout.HostMode.CLIPBOARD)
        } else {
            refreshStrip()
        }
    }

    private fun commitNoteEditRoman() {
        if (noteEditRoman.isEmpty()) return
        val raw = noteEditRoman.toString()
        noteEditBuf.append(if (bangla) Phonetic.convert(raw) else raw)
        noteEditRoman.setLength(0)
    }

    private fun noteEditDisplayText(): String {
        val tail = if (noteEditRoman.isEmpty()) ""
        else if (bangla) Phonetic.convert(noteEditRoman.toString()) else noteEditRoman.toString()
        return noteEditBuf.toString() + tail
    }

    private fun updateNoteEditStrip() {
        val text = noteEditDisplayText()
        kv?.setComposing(if (text.isEmpty()) "✏️ নতুন নোট লিখুন" else "✏️ $text")
    }

    /** Secret mode-এ টাইপ করা Roman অক্ষর বাংলা মোডে phonetic করে (আগে কাঁচা "ami" যেত)। */
    private fun secretPlain(): String {
        val raw = secretText.toString()
        if (!(bangla && symbols == 0)) return raw
        return Regex("[A-Za-z^`]+").replace(raw) { Phonetic.convert(it.value) }
    }

    private fun secretTarget(): Pair<String, String>? {
        val contacts = SecretConfig.getContacts(this)
        if (contacts.isEmpty()) return null
        val active = SecretConfig.getActiveContact(this)
        val pass = active?.let { contacts[it] }
        return if (active != null && pass != null) active to pass
        else contacts.entries.first().let { it.key to it.value }
    }

    private fun secretLabel(): String {
        val name = secretTarget()?.first ?: "?"
        return "🔐 → $name  (লিখুন, শেষে Enter)"
    }

    private fun startSecretClipboardListener() {
        if (secretClipManager != null) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            if (lastAllowSecretBanner && SecretConfig.clipboardMonitorEnabled(this)) {
                checkClipboardForSecret(force = false)
            }
        }
        secretClipManager = cm
        secretClipListener = l
        cm.addPrimaryClipChangedListener(l)
    }

    private fun stopSecretClipboardListener() {
        val cm = secretClipManager ?: return
        secretClipListener?.let { cm.removePrimaryClipChangedListener(it) }
        secretClipManager = null
        secretClipListener = null
    }

    private fun checkClipboardForSecret(force: Boolean = false) {
        if (!lastAllowSecretBanner) return
        if (!force && !SecretConfig.clipboardMonitorEnabled(this)) return
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            if (text.isNullOrBlank()) return
            SecretClipboardMonitorService.inspectNow(this, text, force = force)
        } catch (_: Exception) {
        }
    }

    private fun renderBannerEvent(event: SecretBannerStateHolder.BannerEvent) {
        val banner = secretBannerView ?: return
        if (!lastAllowSecretBanner) {
            renderedBannerEventId = -1L
            banner.hide()
            return
        }
        val nowMs = System.currentTimeMillis()
        when (event) {
            is SecretBannerStateHolder.BannerEvent.Hidden -> {
                renderedBannerEventId = -1L
                banner.hide()
            }
            is SecretBannerStateHolder.BannerEvent.Decoded -> {
                val remaining = event.expiresAtMs - nowMs
                if (remaining <= 0L) {
                    renderedBannerEventId = -1L
                    SecretBannerStateHolder.dismiss()
                    banner.hide()
                } else if (renderedBannerEventId != event.id || banner.visibility != View.VISIBLE) {
                    renderedBannerEventId = event.id
                    banner.showDecoded(
                        senderName = event.senderName,
                        plainText = event.plainText,
                        cipherText = event.cipherText,
                        durationMs = remaining,
                    )
                }
            }
            is SecretBannerStateHolder.BannerEvent.Unrecognized -> {
                val remaining = event.expiresAtMs - nowMs
                if (remaining <= 0L) {
                    renderedBannerEventId = -1L
                    SecretBannerStateHolder.dismiss()
                    banner.hide()
                } else if (renderedBannerEventId != event.id || banner.visibility != View.VISIBLE) {
                    renderedBannerEventId = event.id
                    banner.showUnrecognized(
                        cipherText = event.cipherText,
                        durationMs = remaining,
                    )
                }
            }
        }
    }

    private fun enterSecretModeFor(contactName: String) {
        commitComposing()
        if (noteEditMode) {
            exitNoteEdit(returnToPanel = false)
        }
        secretMode = true
        secretText.setLength(0)
        kv?.setSecretMode(true, contactName)
        kv?.setComposing("")
    }

    override fun onSecretToggle() {
        commitComposing()
        val contacts = SecretConfig.getContacts(this)

        if (!secretMode) {
            if (contacts.isEmpty()) {
                secretMode = false
                kv?.setSecretMode(false)
                kv?.setComposing("")
                Toast.makeText(
                    this,
                    "আগে 🔐 Secret Message screen-এ contact বানান বা বন্ধুর পাঠানো code import করুন",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            val targetName = secretTarget()?.first ?: contacts.keys.first()
            enterSecretModeFor(targetName)
            checkClipboardForSecret(force = true)
        } else {
            onSecretCancel()
        }
    }

    override fun onSecretContactCycle() {
        if (!secretMode) return
        val contacts = SecretConfig.getContacts(this)
        if (contacts.size <= 1) {
            Toast.makeText(this, "একটি মাত্র contact সেভ আছে", Toast.LENGTH_SHORT).show()
            return
        }
        val names = contacts.keys.toList()
        val cur = secretTarget()?.first
        val next = names[(names.indexOf(cur) + 1).mod(names.size)]
        SecretConfig.setActiveContact(this, next)
        kv?.setSecretMode(true, next)
        Toast.makeText(this, "🔐 এখন: $next-এর জন্য", Toast.LENGTH_SHORT).show()
    }

    override fun onSecretCancel() {
        secretMode = false
        secretText.setLength(0)
        kv?.setSecretMode(false)
        kv?.setComposing("")
        refreshStrip()
    }

    private fun sendSecretMessage() {
        val text = secretPlain().trim()
        secretMode = false
        secretText.setLength(0)
        kv?.setSecretMode(false)
        kv?.setComposing("")
        refreshStrip()
        if (text.isBlank()) return

        val target = secretTarget()
        if (target == null) {
            Toast.makeText(this, "আগে Secret Message screen-এ contact/passphrase সেভ করুন", Toast.LENGTH_LONG).show()
            return
        }
        val (name, passphrase) = target
        val style = SecretConfig.getStyle(this)
        val glyph = try {
            GlyphAlphabet.encode(SecretCipher.encrypt(text, passphrase), style)
        } catch (e: Exception) {
            Toast.makeText(this, "Encrypt করা যায়নি", Toast.LENGTH_SHORT).show()
            return
        }
        secretDetector.ignoreOutgoingCipher(glyph)
        SecretConfig.saveSecretCacheEntry(
            ctx = this,
            contactName = name,
            plainText = text,
            glyphText = glyph,
            style = style,
            isOutgoing = true,
        )
        // commitText ব্যর্থ হলে (field নেই) লেখা হারাতে না দিয়ে clipboard-এ রাখি
        val ok = currentInputConnection?.commitText(glyph, 1) ?: false
        if (!ok) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("secret", glyph))
            Toast.makeText(this, "লেখার জায়গা পাইনি — secret লেখা Copy হয়েছে, Paste করুন", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "🔒 $name-এর জন্য encrypt হয়ে বসেছে", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Assistant বন্ধ", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (isPtt) {
                AssistantListenerService.triggerPtt(this)
            } else {
                AssistantListenerService.start(this)
                Toast.makeText(
                    this, "Assistant চালু — \"Hey Bot\" বলুন", Toast.LENGTH_SHORT
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
            return
        }
        if (secretMode) {
            Toast.makeText(this, "Secret mode-এ voice বন্ধ — আগে Secret mode বন্ধ করুন", Toast.LENGTH_SHORT).show()
            return
        }
        commitComposing()
        AssistantListenerService.setPaused(true)
        kv?.setSuggestions(emptyList())
        kv?.setComposing("🎤 মাইক চালু হচ্ছে…")
        voice.start(bangla)
    }

    override fun onVoiceReady() {
        kv?.setComposing("🎤 বলুন…  (আবার 🎤 চাপলে বন্ধ)")
    }

    override fun onVoicePartial(text: String) {
        kv?.setSuggestions(emptyList())
        kv?.setComposing("🎤 $text")
    }

    override fun onVoiceResult(text: String) {
        currentInputConnection?.commitText("$text ", 1)
        kv?.setComposing("🎤 বলুন…")
    }

    override fun onVoiceError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onVoiceEnd() {
        AssistantListenerService.setPaused(false)
        kv?.setComposing("")
        refreshStrip()
    }

    override fun onTranslate() {
        val ic = currentInputConnection ?: return
        val text = if (buf.isNotEmpty()) {
            if (bangla) Phonetic.convert(buf.toString()) else buf.toString()
        } else {
            ic.getSelectedText(0)?.toString()
        }
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "অনুবাদ করতে আগে শব্দ লিখুন বা সিলেক্ট করুন", Toast.LENGTH_SHORT).show()
            return
        }
        val from = if (bangla) "bn" else "en"
        val to = if (bangla) "en" else "bn"
        Translator.translate(text, from, to) { result ->
            if (result != null) {
                if (buf.isNotEmpty()) {
                    if (!bangla) {
                        val before = ic.getTextBeforeCursor(buf.length, 0)?.toString()
                        if (before != null && before == buf.toString()) {
                            ic.deleteSurroundingText(buf.length, 0)
                        }
                    }
                    buf.setLength(0)
                    ic.finishComposingText()
                }
                ic.commitText(result, 1)
                refreshStrip()
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

    private fun extractTrailingEnglishWord(text: String): String {
        var i = text.length - 1
        while (i >= 0 && (text[i] in 'a'..'z' || text[i] in 'A'..'Z' || text[i] == '\'')) {
            i--
        }
        return text.substring(i + 1)
    }

    companion object {
        private val SENTENCE_TERMINATORS = setOf(".", "।", "!", "?")
    }
}
