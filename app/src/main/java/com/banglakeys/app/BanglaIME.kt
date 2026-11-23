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
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast

class BanglaIME : InputMethodService(), KeyboardView.Listener, VoiceInput.Listener {

    private sealed class NoteEditTarget {
        object NewNote : NoteEditTarget()
        data class EditPinned(val index: Int, val oldText: String) : NoteEditTarget()
        data class EditRecent(val oldText: String) : NoteEditTarget()
    }

    private var hostLayout: KeyboardHostLayout? = null
    private var kv: KeyboardView? = null
    private var panelView: NotepadClipboardPanelView? = null
    private var emojiPanelView: EmojiPanelView? = null
    private lateinit var prefs: SharedPreferences

    private val buf = StringBuilder()      // active roman/word prefix buffer
    private var secretMode = false
    private val secretText = StringBuilder()
    private var noteEditTarget: NoteEditTarget? = null
    private val noteCommittedBuf = StringBuilder()
    private val noteRomanBuf = StringBuilder()

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
    private var hostMode = KeyboardHostLayout.HostMode.KEYBOARD

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
        // voice.destroy() মাঝপথে হলে assistant যেন "paused" হয়ে আটকে না থাকে
        AssistantListenerService.setPaused(false)
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val host = KeyboardHostLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        hostLayout = host

        val v = host.keyboardView
        v.listener = this
        kv = v

        val panel = host.panelView.apply {
            listener = object : NotepadClipboardPanelView.Listener {
                override fun onBackToKeyboard() {
                    onFeedback()
                    closePanel()
                }

                override fun onPasteItem(text: String) {
                    onFeedback()
                    pasteFromPanel(text)
                }

                override fun onAddNewNote() {
                    onFeedback()
                    startNoteEdit(NoteEditTarget.NewNote, "")
                }

                override fun onPinRecent(text: String) {
                    onFeedback()
                    notes = Notes(this@BanglaIME)
                    clipboardMemory.reload()
                    notes.add(text)
                    clipboardMemory.remove(text)
                    refreshPanelData()
                    Toast.makeText(this@BanglaIME, "📌 Pinned হয়েছে", Toast.LENGTH_SHORT).show()
                }

                override fun onUnpinNote(index: Int, text: String) {
                    onFeedback()
                    notes = Notes(this@BanglaIME)
                    clipboardMemory.reload()
                    notes.remove(index)
                    clipboardMemory.add(text)
                    refreshPanelData()
                    Toast.makeText(this@BanglaIME, "Recent-এ সরানো হয়েছে", Toast.LENGTH_SHORT).show()
                }

                override fun onEditPinned(index: Int, text: String) {
                    onFeedback()
                    startNoteEdit(NoteEditTarget.EditPinned(index, text), text)
                }

                override fun onEditRecent(text: String) {
                    onFeedback()
                    startNoteEdit(NoteEditTarget.EditRecent(text), text)
                }

                override fun onRemovePinned(index: Int, text: String) {
                    onFeedback()
                    notes = Notes(this@BanglaIME)
                    notes.remove(index)
                    refreshPanelData()
                }

                override fun onRemoveRecent(text: String) {
                    onFeedback()
                    clipboardMemory.reload()
                    clipboardMemory.remove(text)
                    refreshPanelData()
                }

                override fun onClearRecent() {
                    onFeedback()
                    clipboardMemory.clear()
                    refreshPanelData()
                }
            }
        }
        panelView = panel

        emojiPanelView = host.emojiPanelView.apply {
            listener = object : EmojiPanelView.Listener {
                override fun onBackToKeyboard() {
                    onFeedback()
                    closePanel()
                }

                override fun onEmojiSelected(emoji: String) {
                    onFeedback()
                    insertEmoji(emoji)
                }

                override fun onBackspace() {
                    onFeedback()
                    onDelete()
                }
            }
        }

        loadPrefs()
        refreshLayout()
        refreshStrip()
        return host
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        loadPrefs()
        buf.setLength(0)
        // অন্য field-এ গেলে secret/note-edit mode ছেড়ে দিই, নইলে সেখানে টাইপ করা লেখা হারিয়ে যেত
        if (!restarting) {
            secretMode = false
            secretText.setLength(0)
            kv?.setSecretMode(false)
        }
        clearNoteEditState()
        closePanel()

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
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (voice.listening) voice.stop()
        if (secretMode) {
            val draft = secretPlain()
            if (draft.isNotBlank()) {
                SecretConfig.saveDraft(this, draft)
            } else {
                SecretConfig.clearDraft(this)
            }
            secretMode = false
            secretText.setLength(0)
            kv?.setSecretMode(false)
        }
        clearNoteEditState()
        closePanel()
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
        if (noteEditTarget != null) {
            updateNoteEditStrip()
            return
        }
        if (secretMode) {
            updateSecretStrip()
            return
        }
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
        if (noteEditTarget != null) {
            val ch = text.singleOrNull()
            val isBnWordChar = bangla && symbols == 0 && ch != null &&
                (ch in 'a'..'z' || ch in 'A'..'Z' || ((ch == '^' || ch == '`') && noteRomanBuf.isNotEmpty()))
            if (isBnWordChar) {
                noteRomanBuf.append(ch)
            } else {
                flushTrailingPhoneticInNoteEdit()
                noteCommittedBuf.append(text)
            }
            updateNoteEditStrip()
            return
        }
        if (secretMode) {
            val ch = text.singleOrNull()
            val isWordChar = ch != null &&
                (ch in 'a'..'z' || ch in 'A'..'Z' ||
                    ((ch == '^' || ch == '`' || ch == '\'') && secretText.isNotEmpty()))
            if (!isWordChar) {
                flushTrailingPhoneticInSecret()
            }
            secretText.append(text)
            updateSecretStrip()
            return
        }
        val ic = currentInputConnection ?: return
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
        if (noteEditTarget != null) {
            when {
                noteRomanBuf.isNotEmpty() -> noteRomanBuf.deleteCharAt(noteRomanBuf.length - 1)
                noteCommittedBuf.isNotEmpty() -> {
                    val trimmed = EmojiRepository.deleteLastGrapheme(noteCommittedBuf.toString())
                    noteCommittedBuf.setLength(0)
                    noteCommittedBuf.append(trimmed)
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
            updateSecretStrip()
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
        if (noteEditTarget != null) {
            flushTrailingPhoneticInNoteEdit()
            noteCommittedBuf.append(" ")
            updateNoteEditStrip()
            return
        }
        if (secretMode) {
            flushTrailingPhoneticInSecret()
            secretText.append(" ")
            updateSecretStrip()
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
        if (noteEditTarget != null) {
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
        if (noteEditTarget != null) {
            flushTrailingPhoneticInNoteEdit()
            userBangla = !userBangla
            prefs.edit().putBoolean("bn", userBangla).apply()
            bangla = userBangla
            closePanel()
            symbols = 0
            refreshLayout()
            updateNoteEditStrip()
            return
        }
        if (secretMode) {
            flushTrailingPhoneticInSecret()
            userBangla = !userBangla
            prefs.edit().putBoolean("bn", userBangla).apply()
            bangla = userBangla
            closePanel()
            symbols = 0
            refreshLayout()
            updateSecretStrip()
            return
        }
        commitComposing()
        userBangla = !userBangla
        prefs.edit().putBoolean("bn", userBangla).apply()
        bangla = userBangla
        closePanel()
        symbols = 0
        refreshLayout()
        refreshStrip()
        updateAutoShift()
    }

    override fun onSymbols(mode: Int) {
        if (noteEditTarget != null) {
            flushTrailingPhoneticInNoteEdit()
            closePanel()
            symbols = mode
            refreshLayout()
            updateNoteEditStrip()
            return
        }
        if (secretMode) {
            flushTrailingPhoneticInSecret()
            closePanel()
            symbols = mode
            refreshLayout()
            updateSecretStrip()
            return
        }
        commitComposing()
        closePanel()
        symbols = mode
        refreshLayout()
        refreshStrip()
    }

    override fun onComposingTap() {
        if (secretMode) {
            cycleSecretRecipient()
            return
        }
        commitComposing()
        updateAutoShift()
    }

    override fun onSuggestionTap(word: String) {
        if (secretMode) {
            val trailing = extractSecretTrailingWord()
            if (trailing.isNotEmpty()) {
                secretText.delete(secretText.length - trailing.length, secretText.length)
            }
            secretText.append(word).append(" ")
            updateSecretStrip()
            return
        }
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
        pasteFromPanel(text)
    }

    private fun pasteFromPanel(text: String) {
        if (secretMode) {
            flushTrailingPhoneticInSecret()
            secretText.append(text)
            closePanel()
            updateSecretStrip()
            return
        }
        commitComposing()
        currentInputConnection?.commitText(text, 1)
        refreshStrip()
    }

    private fun insertEmoji(emoji: String) {
        if (noteEditTarget != null) {
            flushTrailingPhoneticInNoteEdit()
            noteCommittedBuf.append(emoji)
            updateNoteEditStrip()
            return
        }
        if (secretMode) {
            flushTrailingPhoneticInSecret()
            secretText.append(emoji)
            updateSecretStrip()
            return
        }
        commitComposing()
        currentInputConnection?.commitText(emoji, 1)
        refreshStrip()
        updateAutoShift()
    }

    private fun refreshPanelData() {
        notes = Notes(this)
        clipboardMemory.reload()
        panelView?.setData(notes.all(), clipboardMemory.clips())
    }

    private fun openPanel() {
        hostMode = KeyboardHostLayout.HostMode.CLIPBOARD
        refreshPanelData()
        hostLayout?.setMode(hostMode)
    }

    private fun openEmojiPanel() {
        hostMode = KeyboardHostLayout.HostMode.EMOJI
        emojiPanelView?.openPanel()
        hostLayout?.setMode(hostMode)
    }

    private fun closePanel() {
        hostMode = KeyboardHostLayout.HostMode.KEYBOARD
        hostLayout?.setMode(hostMode)
    }

    private fun startNoteEdit(target: NoteEditTarget, initialText: String) {
        noteEditTarget = target
        noteCommittedBuf.setLength(0)
        noteCommittedBuf.append(initialText)
        noteRomanBuf.setLength(0)
        closePanel()
        if (symbols != 0) {
            symbols = 0
            refreshLayout()
        }
        updateNoteEditStrip()
    }

    private fun clearNoteEditState() {
        noteEditTarget = null
        noteCommittedBuf.setLength(0)
        noteRomanBuf.setLength(0)
        kv?.setNoteEditMode(false)
    }

    private fun noteEditPlain(): String {
        val active = if (noteRomanBuf.isEmpty()) "" else {
            if (bangla && symbols == 0) Phonetic.convert(noteRomanBuf.toString()) else noteRomanBuf.toString()
        }
        return noteCommittedBuf.toString() + active
    }

    private fun flushTrailingPhoneticInNoteEdit() {
        if (noteRomanBuf.isEmpty()) return
        val converted = if (bangla && symbols == 0) Phonetic.convert(noteRomanBuf.toString()) else noteRomanBuf.toString()
        noteCommittedBuf.append(converted)
        noteRomanBuf.setLength(0)
    }

    private fun updateNoteEditStrip() {
        kv?.setNoteEditMode(true)
        val plain = noteEditPlain()
        kv?.setComposing(if (plain.isEmpty()) "" else "✏️ $plain")
        kv?.setSuggestions(emptyList())
    }

    override fun onNoteEditSave() {
        val target = noteEditTarget ?: return
        val finalStr = noteEditPlain().trim()
        notes = Notes(this)
        when (target) {
            NoteEditTarget.NewNote -> {
                if (finalStr.isNotEmpty()) notes.add(finalStr)
            }
            is NoteEditTarget.EditPinned -> {
                if (finalStr.isNotEmpty()) notes.update(target.index, finalStr)
                else notes.remove(target.index)
            }
            is NoteEditTarget.EditRecent -> {
                clipboardMemory.reload()
                if (finalStr.isNotEmpty()) clipboardMemory.update(target.oldText, finalStr)
                else clipboardMemory.remove(target.oldText)
            }
        }
        clearNoteEditState()
        openPanel()
    }

    override fun onNoteEditCancel() {
        if (noteEditTarget == null) return
        clearNoteEditState()
        openPanel()
    }

    override fun onNotepad() {
        commitComposing()
        if (noteEditTarget != null) {
            clearNoteEditState()
            openPanel()
            return
        }
        if (hostMode == KeyboardHostLayout.HostMode.CLIPBOARD) closePanel() else openPanel()
    }

    override fun onEmojiToggle() {
        commitComposing()
        if (hostMode == KeyboardHostLayout.HostMode.EMOJI) closePanel() else openEmojiPanel()
    }

    /** Secret mode-এ টাইপ করা Roman অক্ষর বাংলা মোডে phonetic করে। */
    private fun secretPlain(): String {
        val raw = secretText.toString()
        if (!(bangla && symbols == 0)) return raw
        return Regex("[A-Za-z^`]+").replace(raw) { Phonetic.convert(it.value) }
    }

    private fun extractSecretTrailingWord(): String {
        val s = secretText.toString()
        var i = s.length - 1
        while (i >= 0) {
            val c = s[i]
            val ok = c in 'a'..'z' || c in 'A'..'Z' || c == '^' || c == '`' || c == '\''
            if (!ok) break
            i--
        }
        return s.substring(i + 1)
    }

    private fun flushTrailingPhoneticInSecret() {
        if (!(bangla && symbols == 0)) return
        val trailing = extractSecretTrailingWord()
        if (trailing.isEmpty()) return
        val converted = Phonetic.convert(trailing)
        secretText.replace(secretText.length - trailing.length, secretText.length, converted)
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
        return "🔐 → $name (লিখুন, শেষে 🔒 Send)"
    }

    private fun updateSecretStrip() {
        val recipient = secretTarget()?.first
        kv?.setSecretMode(true, recipient)
        if (secretText.isEmpty()) {
            kv?.setComposing(secretLabel())
            kv?.setSuggestions(emptyList())
            return
        }
        val plain = secretPlain()
        val prefix = if (recipient != null) "🔐 $recipient: " else "🔐 "
        kv?.setComposing(prefix + plain)

        val trailing = if (allowSuggest && symbols == 0) extractSecretTrailingWord() else ""
        if (trailing.isNotEmpty()) {
            kv?.setSuggestions(suggestions.suggestionsFor(trailing, bangla))
        } else {
            kv?.setSuggestions(emptyList())
        }
    }

    private fun cycleSecretRecipient(): Boolean {
        val contacts = SecretConfig.getContacts(this)
        if (contacts.size <= 1) return false
        val names = contacts.keys.toList()
        val cur = secretTarget()?.first
        val next = names[(names.indexOf(cur) + 1).mod(names.size)]
        SecretConfig.setActiveContact(this, next)
        updateSecretStrip()
        Toast.makeText(this, "🔐 এখন: $next-এর জন্য", Toast.LENGTH_SHORT).show()
        return true
    }

    /** কপি করা glyph-লেখা (পাওয়া secret message) থাকলে পড়ে Toast-এ দেখায়। */
    private fun tryDecodeClipboard() {
        val contacts = SecretConfig.getContacts(this)
        if (contacts.isEmpty()) return
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val t = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            if (!GlyphAlphabet.looksLikeGlyphText(t)) return
            val bytes = GlyphAlphabet.decodeAny(t) ?: return
            val matched = SecretCipher.decryptWithMatchingContact(bytes, contacts)
            if (matched != null) {
                val style = GlyphAlphabet.detectStyle(t) ?: GlyphStyle.RUNIC
                SecretConfig.saveSecretCacheEntry(this, matched.first, matched.second, t, style, isOutgoing = false)
                Toast.makeText(this, "🔓 ${matched.first}: ${matched.second}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "❌ এই secret message-এর passphrase আপনার কাছে নেই", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
        }
    }

    override fun onSecretToggle() {
        commitComposing()
        clearNoteEditState()
        val contacts = SecretConfig.getContacts(this)

        // Secret mode চালু + কিছু লেখা নেই + একাধিক contact => পরের contact-এ যাই
        if (secretMode && secretText.isEmpty() && contacts.size > 1) {
            cycleSecretRecipient()
            return
        }

        secretMode = !secretMode
        secretText.setLength(0)
        if (secretMode) {
            if (contacts.isEmpty()) {
                secretMode = false
                kv?.setSecretMode(false)
                refreshStrip()
                Toast.makeText(
                    this,
                    "আগে 🔐 Secret Message screen-এ contact বানান বা বন্ধুর পাঠানো code import করুন",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            val savedDraft = SecretConfig.getDraft(this)
            if (savedDraft.isNotBlank()) {
                secretText.append(savedDraft)
            }
            closePanel()
            if (symbols != 0) {
                symbols = 0
                refreshLayout()
            }
            updateSecretStrip()
            Toast.makeText(
                this,
                if (savedDraft.isNotBlank()) "🔐 Secret mode চালু (খসড়া পুনরুদ্ধার হয়েছে)" else "🔐 Secret mode চালু",
                Toast.LENGTH_SHORT
            ).show()
            tryDecodeClipboard()
        } else {
            SecretConfig.clearDraft(this)
            kv?.setSecretMode(false)
            refreshStrip()
            Toast.makeText(this, "Secret mode বন্ধ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSecretMessage() {
        val text = secretPlain().trim()
        secretMode = false
        secretText.setLength(0)
        SecretConfig.clearDraft(this)
        kv?.setSecretMode(false)
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
        } catch (_: Exception) {
            Toast.makeText(this, "Encrypt করা যায়নি", Toast.LENGTH_SHORT).show()
            return
        }
        // পাঠানোর আগেই AES-256-GCM ভল্ট ক্যাশে মেসেজটি সুরক্ষিতভাবে সংরক্ষণ করি
        SecretConfig.saveSecretCacheEntry(this, name, text, glyph, style, isOutgoing = true)

        // commitText ব্যর্থ হলে (field নেই) লেখা হারাতে না দিয়ে clipboard-এ রাখি
        val ok = currentInputConnection?.commitText(glyph, 1) ?: false
        if (!ok) {
            ClipboardMemory.ignoreNext(glyph)
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("secret", glyph))
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
        if (secretMode) {
            val text = secretPlain().trim()
            if (text.isBlank()) {
                Toast.makeText(this, "অনুবাদ করতে আগে শব্দ লিখুন", Toast.LENGTH_SHORT).show()
                return
            }
            val from = if (bangla) "bn" else "en"
            val to = if (bangla) "en" else "bn"
            Translator.translate(text, from, to) { result ->
                if (result != null && secretMode) {
                    secretText.setLength(0)
                    secretText.append(result)
                    updateSecretStrip()
                }
            }
            return
        }
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
