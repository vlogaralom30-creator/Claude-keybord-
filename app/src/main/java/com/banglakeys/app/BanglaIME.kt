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
import com.banglakeys.app.robot.EmoBrainHub
import com.banglakeys.app.robot.EmoLearningStore
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
    private val secretBuf = StringBuilder()
    private val secretRoman = StringBuilder()

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
    private var bnLayout = "phonetic"          // "phonetic" | "probhat" (Ridmik fixed)
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
        // PBKDF2 একবার background-এ সেরে রাখি — প্রথম 🔐 send/decode-এ keyboard আটকাবে না
        Thread { SecretCipher.warmUp(SecretConfig.allPassphrases(applicationContext)) }.start()
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

            override fun onGuideActionClicked(action: SetupGuidance.GuideAction) {
                if (action == SetupGuidance.GuideAction.EXIT_SECRET_MODE) {
                    onSecretCancel()
                } else {
                    SetupGuidance.openAction(this@BanglaIME, action, fromService = true)
                }
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
                        commitSecretRoman()
                        secretBuf.append(emoji)
                        updateSecretStrip()
                    }
                    else -> {
                        commitComposing()
                        currentInputConnection?.commitText(emoji, 1)
                        if (allowLearn) {
                            EmoLearningStore.recordEmojiUsed(this@BanglaIME, emoji)
                        }
                        kv?.notifyEmoWord(emoji, !allowLearn)
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
                        // বাংলা অক্ষরে এক চাপে এক code point (আগে কার/হসন্ত) — Ridmik/Android-এর মতো
                        val lastIsBangla = before.lastOrNull()?.let { it in '\u0980'..'\u09FF' } == true
                        if (before.isNotEmpty() && !lastIsBangla) {
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
        if (!restarting) {
            ThemeAutoSwitcher.onKeyboardOpen(this)
        }
        loadPrefs()
        buf.setLength(0)
        translateRequestId++
        translating = false
        // অন্য field-এ গেলে secret mode ও note-edit ছেড়ে দিই, নইলে লেখা হারিয়ে যেত
        if (!restarting) {
            secretMode = false
            secretBuf.setLength(0)
            secretRoman.setLength(0)
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

        // numeric PIN field (TYPE_CLASS_NUMBER + VARIATION_PASSWORD)-ও password
        val isPassword = (inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )) || (inputClass == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        // incognito / private mode: app "শেখা বন্ধ" চাইলে (IME_FLAG_NO_PERSONALIZED_LEARNING) মানি
        val noLearning = ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        val isNumericField = inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME

        val codeLike = isPassword || (inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI
            ))

        allowSuggest = !isPassword && !isNumericField
        allowLearn = !codeLike && !noLearning
        lastAllowSecretBanner = !isPassword && !isNumericField
        kv?.setEmoPrivacyMode(isPassword)

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
        translateRequestId++
        translating = false
        renderedBannerEventId = -1L
        secretBannerView?.hide()
        secretMode = false
        secretBuf.setLength(0)
        secretRoman.setLength(0)
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

        if (isAvroActive()) {
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
        bnLayout = prefs.getString("bn_layout", "phonetic") ?: "phonetic"
        heightPct = prefs.getInt("height", 100)
        reloadAllThemeViews()
    }

    private fun reloadAllThemeViews() {
        secretBannerView?.reloadTheme()
        kv?.reloadTheme()
        panelView?.reloadTheme()
        emojiPanelView?.reloadTheme()
    }

    private fun isAvroActive() = bangla && bnLayout != "probhat"

    /** বাংলা ব্লকের অক্ষর/কার/হসন্ত (বাংলা অঙ্ক ০-৯ বাদে) — প্রভাত-এর শব্দ buffer-এর জন্য। */
    private fun isBnWordChar(c: Char): Boolean =
        c in '\u0980'..'\u09FF' && c !in '\u09E6'..'\u09EF'

    private fun spaceLabelFor(): String =
        KeyboardLanguageMode.from(bangla, bnLayout).spaceLabel()

    /** 🌐 দীর্ঘ চাপ: সরাসরি অভ্র ⇄ বাংলা (প্রভাত) */
    override fun onLayoutToggle() {
        commitComposing()
        commitNoteEditRoman()
        commitSecretRoman()
        val next = when (KeyboardLanguageMode.from(bangla, bnLayout)) {
            KeyboardLanguageMode.AVRO -> KeyboardLanguageMode.BANGLA
            KeyboardLanguageMode.BANGLA -> KeyboardLanguageMode.AVRO
            KeyboardLanguageMode.ENGLISH -> KeyboardLanguageMode.AVRO
        }
        applyLanguageMode(next, showFeedbackToast = true)
    }

    private fun refreshLayout() {
        val bnDigits = bangla && bnDigitsPref &&
            inputClass != InputType.TYPE_CLASS_NUMBER &&
            inputClass != InputType.TYPE_CLASS_PHONE
        val rows = when (symbols) {
            1 -> Layouts.symbols(bnDigits, bangla)
            2 -> Layouts.symbols2()
            else -> when {
                bangla && bnLayout == "probhat" -> Layouts.probhat(bnDigits)
                bangla -> Layouts.phonetic(numRow, bnDigits)
                else -> Layouts.english(numRow)
            }
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
            isAvroActive() -> Phonetic.convert(activePrefix)
            else -> activePrefix
        }
        kv?.setComposing(composingDisplay)
        kv?.setSuggestions(suggestions.suggestionsFor(activePrefix, bangla))
    }

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        if (buf.isEmpty()) {
            if (isAvroActive()) ic.finishComposingText()
            refreshStrip()
            return
        }
        if (isAvroActive()) {
            val bn = Phonetic.convert(buf.toString())
            ic.setComposingText(bn, 1)
        }
        refreshStrip()
    }

    private fun commitComposing() {
        if (buf.isEmpty()) return
        val raw = buf.toString()
        val avro = isAvroActive()
        val finalWord = if (avro) Phonetic.convert(raw) else raw
        if (allowLearn) {
            suggestions.learn(raw, finalWord, avro || bangla)
            EmoLearningStore.recordWordCommitted(
                ctx = this,
                rawWord = finalWord,
                isBangla = avro || bangla,
                viaSuggestion = false,
            )
        }
        kv?.notifyEmoWord(finalWord, !allowLearn)
        if (avro) {
            currentInputConnection?.finishComposingText()
        }
        buf.setLength(0)
        refreshStrip()
    }

    // ---------------- KeyboardView.Listener ----------------

    override fun onText(text: String) {
        if (noteEditMode) {
            val ch = text.singleOrNull()
            val phoneticBn = isAvroActive() && symbols == 0
            if (phoneticBn && ch != null &&
                (ch in 'a'..'z' || ch in 'A'..'Z' || ((ch == '^' || ch == '`' || ch == ':') && noteEditRoman.isNotEmpty()))
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
            val ch = text.singleOrNull()
            val phoneticBn = isAvroActive() && symbols == 0
            if (phoneticBn && ch != null &&
                (ch in 'a'..'z' || ch in 'A'..'Z' || ((ch == '^' || ch == '`' || ch == ':') && secretRoman.isNotEmpty()))
            ) {
                secretRoman.append(ch)
            } else {
                commitSecretRoman()
                secretBuf.append(text)
            }
            updateSecretStrip()
            return
        }
        val ch = text.singleOrNull()
        val phoneticBn = isAvroActive() && symbols == 0
        val englishWord = !bangla && symbols == 0 && allowSuggest

        if (phoneticBn && ch != null &&
            (ch in 'a'..'z' || ch in 'A'..'Z' || ((ch == '^' || ch == '`' || ch == ':') && buf.isNotEmpty()))
        ) {
            buf.append(ch)
            updateComposing()
            if (ThemeAutoSwitcher.onKeyTyped(this)) reloadAllThemeViews()
            return
        }

        if (englishWord && ch != null &&
            (ch in 'a'..'z' || ch in 'A'..'Z' || (ch == '\'' && buf.isNotEmpty()))
        ) {
            buf.append(ch)
            ic.commitText(ch.toString(), 1)
            refreshStrip()
            if (ThemeAutoSwitcher.onKeyTyped(this)) reloadAllThemeViews()
            return
        }

        // প্রভাত (fixed Bangla): অক্ষর সরাসরি commit হয়, কিন্তু buf-এ জমিয়ে suggestion/learning চালু রাখি
        val probhatWord = bangla && !isAvroActive() && symbols == 0 && allowSuggest &&
            text.isNotEmpty() && text.all { isBnWordChar(it) }
        if (probhatWord) {
            buf.append(text)
            ic.commitText(text, 1)
            refreshStrip()
            if (ThemeAutoSwitcher.onKeyTyped(this)) reloadAllThemeViews()
            return
        }

        commitComposing()
        ic.commitText(text, 1)
        if (text.isNotBlank()) {
            if (allowLearn && EmoLearningStore.extractEmojis(text).isNotEmpty()) {
                EmoLearningStore.recordEmojiUsed(this, text)
            }
            kv?.notifyEmoWord(text, !allowLearn)
        }
        if (text in SENTENCE_TERMINATORS) {
            suggestions.resetSentenceContext()
        }
        refreshStrip()
        updateAutoShift()
        if (ThemeAutoSwitcher.onKeyTyped(this)) reloadAllThemeViews()
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
            when {
                secretRoman.isNotEmpty() -> secretRoman.deleteCharAt(secretRoman.length - 1)
                secretBuf.isNotEmpty() -> {
                    val trimmed = EmojiRepository.deleteLastGrapheme(secretBuf.toString())
                    secretBuf.setLength(0)
                    secretBuf.append(trimmed)
                }
            }
            updateSecretStrip()
            return
        }
        if (allowLearn) {
            EmoLearningStore.recordBackspace(this)
        }
        if (buf.isNotEmpty()) {
            buf.setLength(buf.length - 1)
            if (isAvroActive()) {
                updateComposing()
                return
            } else {
                // English ও প্রভাত: commit হওয়া লেখা থেকে এক অক্ষর মুছে buffer সিঙ্ক রাখি
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
            commitSecretRoman()
            secretBuf.append(" ")
            updateSecretStrip()
            return
        }
        commitComposing()
        currentInputConnection?.commitText(" ", 1)
        refreshStrip()
        updateAutoShift()
        if (ThemeAutoSwitcher.onKeyTyped(this)) reloadAllThemeViews()
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
        kv?.notifyEmoSend(!allowLearn)
        if (ThemeAutoSwitcher.onKeyTyped(this)) reloadAllThemeViews()
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
        commitSecretRoman()
        val next = KeyboardLanguageMode.from(bangla, bnLayout).next()
        applyLanguageMode(next, showFeedbackToast = true)
    }

    private fun applyLanguageMode(mode: KeyboardLanguageMode, showFeedbackToast: Boolean) {
        userBangla = mode.isBangla
        bangla = mode.isBangla
        if (mode.isBangla) {
            bnLayout = mode.prefLayoutValue()
            prefs.edit()
                .putBoolean("bn", true)
                .putString("bn_layout", bnLayout)
                .apply()
        } else {
            prefs.edit()
                .putBoolean("bn", false)
                .apply()
        }
        symbols = 0
        refreshLayout()
        when {
            noteEditMode -> updateNoteEditStrip()
            secretMode -> updateSecretStrip()
            else -> refreshStrip()
        }
        updateAutoShift()
        if (showFeedbackToast) {
            Toast.makeText(this, mode.toastLabel(), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSymbols(mode: Int) {
        commitComposing()
        commitNoteEditRoman()
        commitSecretRoman()
        symbols = mode
        refreshLayout()
        when {
            noteEditMode -> updateNoteEditStrip()
            secretMode -> updateSecretStrip()
            else -> refreshStrip()
        }
    }

    override fun onComposingTap() {
        if (noteEditMode) {
            commitNoteEditRoman()
            updateNoteEditStrip()
            return
        }
        if (secretMode) {
            commitSecretRoman()
            updateSecretStrip()
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
            if (!isAvroActive()) {
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
        if (allowLearn) {
            EmoLearningStore.recordWordCommitted(
                ctx = this,
                rawWord = word,
                isBangla = bangla,
                viaSuggestion = true,
            )
        }
        kv?.notifyEmoWord(word, !allowLearn)
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
        if (secretMode) {
            commitSecretRoman()
            secretBuf.append(text)
            updateSecretStrip()
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
        noteEditBuf.append(if (isAvroActive()) Phonetic.convert(raw) else raw)
        noteEditRoman.setLength(0)
    }

    private fun noteEditDisplayText(): String {
        val tail = if (noteEditRoman.isEmpty()) ""
        else if (isAvroActive()) Phonetic.convert(noteEditRoman.toString()) else noteEditRoman.toString()
        return noteEditBuf.toString() + tail
    }

    private fun updateNoteEditStrip() {
        val text = noteEditDisplayText()
        kv?.setComposing(if (text.isEmpty()) "✏️ নতুন নোট লিখুন" else "✏️ $text")
    }

    private fun commitSecretRoman() {
        if (secretRoman.isEmpty()) return
        val raw = secretRoman.toString()
        secretBuf.append(if (isAvroActive()) Phonetic.convert(raw) else raw)
        secretRoman.setLength(0)
    }

    private fun secretDisplayText(): String {
        val tail = if (secretRoman.isEmpty()) ""
        else if (isAvroActive()) Phonetic.convert(secretRoman.toString()) else secretRoman.toString()
        return secretBuf.toString() + tail
    }

    private fun updateSecretStrip() {
        val text = secretDisplayText()
        kv?.setComposing(if (text.isEmpty()) "" else "🔐 $text")
    }

    private fun secretTarget(): Pair<String, String>? {
        val contacts = SecretConfig.getContacts(this)
        if (contacts.isEmpty()) return null
        val active = SecretConfig.getActiveContact(this)
        val pass = active?.let { contacts[it] }
        return if (active != null && pass != null) active to pass
        else contacts.entries.first().let { it.key to it.value }
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
        secretBuf.setLength(0)
        secretRoman.setLength(0)
        kv?.setSecretMode(true, contactName)
        kv?.setComposing("")
    }

    private fun showGuideBanner(guide: SetupGuidance.SetupGuide) {
        renderedBannerEventId = -1L
        host?.setMode(KeyboardHostLayout.HostMode.KEYBOARD)
        secretBannerView?.showGuide(guide)
    }

    override fun onSecretToggle() {
        commitComposing()
        val contacts = SecretConfig.getContacts(this)

        if (!secretMode) {
            val guide = SetupGuidance.secretGuide(this)
            if (guide != null) {
                secretMode = false
                kv?.setSecretMode(false)
                kv?.setComposing("")
                showGuideBanner(guide)
                return
            }
            val targetName = secretTarget()?.first ?: contacts.keys.first()
            enterSecretModeFor(targetName)
            checkClipboardForSecret(force = false)
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
        secretBuf.setLength(0)
        secretRoman.setLength(0)
        kv?.setSecretMode(false)
        kv?.setComposing("")
        refreshStrip()
    }

    private fun sendSecretMessage() {
        commitSecretRoman()
        val text = secretBuf.toString().trim()
        secretMode = false
        secretBuf.setLength(0)
        secretRoman.setLength(0)
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
        val guide = SetupGuidance.assistantGuide(this)
        if (guide != null) {
            showGuideBanner(guide)
            return
        }
        if (voice.listening) {
            voice.stop()
        }
        AssistantListenerService.setPaused(false)
        AssistantListenerService.triggerPtt(this)
        kv?.triggerEmoListeningVisual(true) ?: EmoBrainHub.onListeningStarted(isPtt = true)
        Toast.makeText(this, "🤖 EMO শুনছে — বলুন", Toast.LENGTH_SHORT).show()
    }

    override fun onVoice() {
        if (!voice.isAvailable()) {
            showGuideBanner(SetupGuidance.voiceUnavailableGuide())
            return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val asked = prefs.getBoolean("voice_perm_asked", false)
            val blocked = prefs.getBoolean("voice_perm_blocked", false)
            if (!asked && !blocked) {
                prefs.edit().putBoolean("voice_perm_asked", true).apply()
                SetupGuidance.openAction(this, SetupGuidance.GuideAction.REQUEST_VOICE_PERMISSION, fromService = true)
            } else {
                showGuideBanner(SetupGuidance.voicePermissionGuide(blocked))
            }
            return
        }
        if (voice.listening) {
            voice.stop()
            return
        }
        if (secretMode) {
            showGuideBanner(SetupGuidance.voiceSecretConflictGuide())
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
        SetupGuidance.voiceRuntimeErrorGuide(message)?.let {
            showGuideBanner(it)
            return
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onVoiceEnd() {
        AssistantListenerService.setPaused(false)
        kv?.setComposing("")
        refreshStrip()
    }

    private var translating = false
    private var translateRequestId = 0L

    /**
     * অনুবাদ: ১) চলতি লেখা (buf) ২) সিলেক্ট করা অংশ ৩) কিছু না থাকলে কার্সরের আগের লাইন।
     * direction লেখা দেখে নিজে ঠিক হয় (বাংলা থাকলে → English, নইলে → বাংলা)।
     * ব্যর্থ হলে এখন কারণসহ Toast আসে (আগে চুপচাপ কিছুই হত না)।
     */
    override fun onTranslate() {
        if (translating) return
        val ic = currentInputConnection ?: return

        val fromBuf = buf.isNotEmpty()
        val bufSnapshot = buf.toString()
        var source: String? = null
        var selected = false
        var line = ""
        if (fromBuf) {
            source = if (isAvroActive()) Phonetic.convert(bufSnapshot) else bufSnapshot
        } else {
            source = ic.getSelectedText(0)?.toString()
            selected = !source.isNullOrBlank()
            if (!selected) {
                line = (ic.getTextBeforeCursor(400, 0)?.toString() ?: "").substringAfterLast('\n')
                source = line.trim()
            }
        }
        if (source.isNullOrBlank()) {
            showGuideBanner(SetupGuidance.translateEmptyGuide())
            return
        }
        val (from, to) = Translator.autoDirection(source)
        val original = source
        val reqId = ++translateRequestId
        translating = true
        Toast.makeText(this, "🌐 অনুবাদ হচ্ছে…", Toast.LENGTH_SHORT).show()
        Translator.translateEx(original, from, to) { outcome ->
            if (reqId != translateRequestId) return@translateEx
            translating = false
            val c = currentInputConnection ?: return@translateEx
            when (outcome) {
                is Translator.Outcome.Fail ->
                    showGuideBanner(SetupGuidance.translateFailGuide(outcome))
                is Translator.Outcome.Ok -> {
                    val result = outcome.text
                    when {
                        fromBuf -> {
                            if (buf.toString() != bufSnapshot) return@translateEx
                            if (!bangla) {
                                val before = c.getTextBeforeCursor(bufSnapshot.length, 0)?.toString()
                                if (before != null && before == bufSnapshot) c.deleteSurroundingText(bufSnapshot.length, 0)
                            }
                            buf.setLength(0)
                            c.finishComposingText()
                            c.commitText(result, 1)
                        }
                        selected -> c.commitText(result, 1) // সিলেকশন থাকলে প্রতিস্থাপন হয়
                        else -> {
                            val before = c.getTextBeforeCursor(line.length, 0)?.toString()
                            if (before == line) c.deleteSurroundingText(line.length, 0)
                            c.commitText(result + line.takeLastWhile { it.isWhitespace() }, 1)
                        }
                    }
                    refreshStrip()
                }
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
