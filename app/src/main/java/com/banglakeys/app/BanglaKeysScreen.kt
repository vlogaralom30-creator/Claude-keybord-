package com.banglakeys.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class ImeStatus {
  ACTIVE,
  ENABLED_ONLY,
  DISABLED,
}

private fun checkImeStatus(context: Context): ImeStatus {
  val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  val expectedComponent = ComponentName(context, BanglaIME::class.java)
  val isEnabled =
    imm.enabledInputMethodList.any { info ->
      info.component == expectedComponent || info.packageName == context.packageName
    }
  val defaultIme =
    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
  val isDefault =
    defaultIme == expectedComponent.flattenToShortString() ||
      defaultIme == expectedComponent.flattenToString()

  return when {
    isEnabled && isDefault -> ImeStatus.ACTIVE
    isEnabled -> ImeStatus.ENABLED_ONLY
    else -> ImeStatus.DISABLED
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BanglaKeysScreen(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val prefs = remember { context.getSharedPreferences("bk", Context.MODE_PRIVATE) }

  var imeStatus by remember { mutableStateOf(checkImeStatus(context)) }
  var assistantReady by remember { mutableStateOf(AssistantSetup.isReady(context)) }
  var assistantRunning by remember { mutableStateOf(AssistantListenerService.running) }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    imeStatus = checkImeStatus(context)
    assistantReady = AssistantSetup.isReady(context)
    assistantRunning = AssistantListenerService.running
  }

  var haptic by remember { mutableStateOf(prefs.getBoolean("haptic", true)) }
  var sound by remember { mutableStateOf(prefs.getBoolean("sound", false)) }
  var numRow by remember { mutableStateOf(prefs.getBoolean("numrow", false)) }
  var bnDigits by remember { mutableStateOf(prefs.getBoolean("bndigits", true)) }
  var heightPct by remember { mutableIntStateOf(prefs.getInt("height", 100).coerceIn(85, 125)) }
  var themeVersion by remember { mutableIntStateOf(0) }

  var showEmbeddedKeyboard by rememberSaveable { mutableStateOf(true) }
  var committedText by rememberSaveable { mutableStateOf("") }
  var composingRoman by rememberSaveable { mutableStateOf("") }
  var previewBanglaMode by rememberSaveable { mutableStateOf(true) }
  var previewSymbolsMode by rememberSaveable { mutableIntStateOf(0) }
  var previewPanelMode by rememberSaveable { mutableStateOf("none") }
  var previewSecretMode by rememberSaveable { mutableStateOf(false) }
  var previewSecretText by rememberSaveable { mutableStateOf("") }
  var previewSecretTarget by remember { mutableStateOf<String?>(null) }
  var previewNoteEditMode by rememberSaveable { mutableStateOf(false) }
  var previewNoteEditKind by rememberSaveable { mutableStateOf("new") }
  var previewNoteEditIndex by rememberSaveable { mutableIntStateOf(-1) }
  var previewNoteEditOldText by rememberSaveable { mutableStateOf("") }
  var previewNoteEditCommitted by rememberSaveable { mutableStateOf("") }
  var previewNoteEditRoman by rememberSaveable { mutableStateOf("") }
  var previewPanelVersion by remember { mutableIntStateOf(0) }
  var previewEmojiVersion by remember { mutableIntStateOf(0) }
  var suggestVersion by remember { mutableIntStateOf(0) }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    previewPanelVersion++
    previewEmojiVersion++
  }

  val suggestionsRepo = remember { Suggestions(context) }

  val activeSecretPlain =
    remember(previewSecretText, previewBanglaMode, previewSymbolsMode) {
      if (!(previewBanglaMode && previewSymbolsMode == 0)) previewSecretText
      else Regex("[A-Za-z^`]+").replace(previewSecretText) { Phonetic.convert(it.value) }
    }
  val activeNoteEditPlain =
    remember(previewNoteEditCommitted, previewNoteEditRoman, previewBanglaMode, previewSymbolsMode) {
      val tail =
        if (previewNoteEditRoman.isEmpty()) ""
        else if (previewBanglaMode && previewSymbolsMode == 0) Phonetic.convert(previewNoteEditRoman)
        else previewNoteEditRoman
      previewNoteEditCommitted + tail
    }
  val composingDisplay =
    remember(
      composingRoman,
      previewBanglaMode,
      previewSecretMode,
      activeSecretPlain,
      previewSecretTarget,
      previewNoteEditMode,
      activeNoteEditPlain
    ) {
      if (previewNoteEditMode) {
        if (activeNoteEditPlain.isEmpty()) "" else "✏️ $activeNoteEditPlain"
      } else if (previewSecretMode) {
        val target = previewSecretTarget ?: "?"
        if (activeSecretPlain.isEmpty()) "🔐 → $target (লিখুন, শেষে 🔒 Send)"
        else "🔐 $target: $activeSecretPlain"
      } else {
        if (composingRoman.isEmpty()) ""
        else if (previewBanglaMode) Phonetic.convert(composingRoman)
        else composingRoman
      }
    }
  val suggestionsList =
    remember(
      composingRoman,
      previewBanglaMode,
      previewSecretMode,
      previewNoteEditMode,
      previewSecretText,
      suggestVersion
    ) {
      if (previewNoteEditMode) {
        emptyList()
      } else if (previewSecretMode) {
        val trailing =
          previewSecretText.takeLastWhile {
            it in 'a'..'z' || it in 'A'..'Z' || it == '^' || it == '`' || it == '\''
          }
        if (trailing.isEmpty()) emptyList() else suggestionsRepo.suggestionsFor(trailing, previewBanglaMode)
      } else {
        suggestionsRepo.suggestionsFor(composingRoman, previewBanglaMode)
      }
    }
  val combinedTestText = committedText + if (previewSecretMode || previewNoteEditMode) "" else composingDisplay

  fun savePreviewNoteEdit() {
    val finalText = activeNoteEditPlain.trim()
    val notesRepo = Notes(context)
    val clipRepo = ClipboardMemory(context)
    when (previewNoteEditKind) {
      "new" -> if (finalText.isNotEmpty()) notesRepo.add(finalText)
      "pinned" -> {
        if (finalText.isNotEmpty()) notesRepo.update(previewNoteEditIndex, finalText)
        else notesRepo.remove(previewNoteEditIndex)
      }
      "recent" -> {
        if (finalText.isNotEmpty()) clipRepo.update(previewNoteEditOldText, finalText)
        else clipRepo.remove(previewNoteEditOldText)
      }
    }
    previewNoteEditMode = false
    previewNoteEditCommitted = ""
    previewNoteEditRoman = ""
    previewPanelVersion++
    previewPanelMode = "clipboard"
  }

  var phoneticSampleInput by rememberSaveable { mutableStateOf("ami tOmake bhalobashi") }
  val convertedBangla = remember(phoneticSampleInput) { Phonetic.convert(phoneticSampleInput) }
  val copiedMsg = stringResource(R.string.copied_snackbar)

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .widthIn(max = 600.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        HeroHeaderCard(
          imeStatus = imeStatus,
          assistantRunning = assistantRunning,
          assistantReady = assistantReady,
        )

        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              SectionTitle(
                icon = Icons.Outlined.TouchApp,
                title = stringResource(R.string.section_test),
              )
              TextButton(
                onClick = { showEmbeddedKeyboard = !showEmbeddedKeyboard },
                modifier = Modifier.testTag("toggle_preview_keyboard"),
              ) {
                Text(
                  if (showEmbeddedKeyboard) stringResource(R.string.hide_preview_keyboard)
                  else stringResource(R.string.show_preview_keyboard)
                )
              }
            }

            OutlinedTextField(
              value = combinedTestText,
              onValueChange = { newValue ->
                committedText = newValue
                composingRoman = ""
              },
              label = { Text(stringResource(R.string.test_field_label)) },
              placeholder = { Text(stringResource(R.string.test_field_placeholder)) },
              minLines = 2,
              modifier = Modifier.fillMaxWidth().testTag("ime_test_input"),
              shape = RoundedCornerShape(14.dp),
            )

            if (showEmbeddedKeyboard) {
              Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
              ) {
                EmbeddedKeyboardPreview(
                  banglaMode = previewBanglaMode,
                  symbolsMode = previewSymbolsMode,
                  panelMode = previewPanelMode,
                  secretMode = previewSecretMode,
                  secretTarget = previewSecretTarget,
                  noteEditMode = previewNoteEditMode,
                  numRow = numRow,
                  bnDigitsPref = bnDigits,
                  heightPct = heightPct,
                  themeVersion = themeVersion,
                  panelVersion = previewPanelVersion,
                  emojiVersion = previewEmojiVersion,
                  composingText = composingDisplay,
                  suggestionsList = suggestionsList,
                  onText = { ch ->
                    if (previewNoteEditMode) {
                      val single = ch.singleOrNull()
                      val isBnWord =
                        previewBanglaMode &&
                          previewSymbolsMode == 0 &&
                          single != null &&
                          (single in 'a'..'z' ||
                            single in 'A'..'Z' ||
                            ((single == '^' || single == '`') && previewNoteEditRoman.isNotEmpty()))
                      if (isBnWord) {
                        previewNoteEditRoman += single
                      } else {
                        if (previewNoteEditRoman.isNotEmpty()) {
                          previewNoteEditCommitted += Phonetic.convert(previewNoteEditRoman)
                          previewNoteEditRoman = ""
                        }
                        previewNoteEditCommitted += ch
                      }
                      return@EmbeddedKeyboardPreview
                    }
                    if (previewSecretMode) {
                      previewSecretText += ch
                      return@EmbeddedKeyboardPreview
                    }
                    val single = ch.singleOrNull()
                    val isBnWordChar =
                      previewBanglaMode &&
                        previewSymbolsMode == 0 &&
                        single != null &&
                        (single in 'a'..'z' ||
                          single in 'A'..'Z' ||
                          ((single == '^' || single == '`') && composingRoman.isNotEmpty()))
                    val isEnWordChar =
                      !previewBanglaMode &&
                        previewSymbolsMode == 0 &&
                        single != null &&
                        (single in 'a'..'z' ||
                          single in 'A'..'Z' ||
                          (single == '\'' && composingRoman.isNotEmpty()))

                    if (isBnWordChar || isEnWordChar) {
                      composingRoman += single
                    } else {
                      if (composingDisplay.isNotEmpty()) {
                        suggestionsRepo.learn(composingRoman, composingDisplay, previewBanglaMode)
                        committedText += composingDisplay
                        composingRoman = ""
                      }
                      committedText += ch
                      if (ch in setOf(".", "।", "!", "?")) {
                        suggestionsRepo.resetSentenceContext()
                      }
                      suggestVersion++
                    }
                  },
                  onDelete = {
                    if (previewNoteEditMode) {
                      if (previewNoteEditRoman.isNotEmpty()) {
                        previewNoteEditRoman = previewNoteEditRoman.dropLast(1)
                      } else if (previewNoteEditCommitted.isNotEmpty()) {
                        previewNoteEditCommitted = EmojiRepository.deleteLastGrapheme(previewNoteEditCommitted)
                      }
                      return@EmbeddedKeyboardPreview
                    }
                    if (previewSecretMode) {
                      if (previewSecretText.isNotEmpty()) {
                        previewSecretText = EmojiRepository.deleteLastGrapheme(previewSecretText)
                      }
                      return@EmbeddedKeyboardPreview
                    }
                    if (composingRoman.isNotEmpty()) {
                      composingRoman = composingRoman.dropLast(1)
                    } else if (committedText.isNotEmpty()) {
                      val after = EmojiRepository.deleteLastGrapheme(committedText)
                      if (!previewBanglaMode && previewSymbolsMode == 0) {
                        val trailing =
                          after.takeLastWhile { it in 'a'..'z' || it in 'A'..'Z' || it == '\'' }
                        committedText = after.dropLast(trailing.length)
                        composingRoman = trailing
                      } else {
                        committedText = after
                      }
                    }
                  },
                  onSpace = {
                    if (previewNoteEditMode) {
                      if (previewNoteEditRoman.isNotEmpty()) {
                        previewNoteEditCommitted +=
                          if (previewBanglaMode && previewSymbolsMode == 0) Phonetic.convert(previewNoteEditRoman)
                          else previewNoteEditRoman
                        previewNoteEditRoman = ""
                      }
                      previewNoteEditCommitted += " "
                      return@EmbeddedKeyboardPreview
                    }
                    if (previewSecretMode) {
                      previewSecretText = "$activeSecretPlain "
                      return@EmbeddedKeyboardPreview
                    }
                    if (composingDisplay.isNotEmpty()) {
                      suggestionsRepo.learn(composingRoman, composingDisplay, previewBanglaMode)
                      committedText += composingDisplay
                      composingRoman = ""
                      suggestVersion++
                    }
                    committedText += " "
                  },
                  onEnter = {
                    if (previewNoteEditMode) {
                      savePreviewNoteEdit()
                      return@EmbeddedKeyboardPreview
                    }
                    if (previewSecretMode) {
                      val plain = activeSecretPlain.trim()
                      val contacts = SecretConfig.getContacts(context)
                      val target = previewSecretTarget ?: contacts.keys.firstOrNull()
                      val pass = target?.let { contacts[it] }
                      previewSecretMode = false
                      previewSecretText = ""
                      if (plain.isNotEmpty() && target != null && !pass.isNullOrBlank()) {
                        val style = SecretConfig.getStyle(context)
                        val glyph = GlyphAlphabet.encode(SecretCipher.encrypt(plain, pass), style)
                        SecretConfig.saveSecretCacheEntry(context, target, plain, glyph, style, isOutgoing = true)
                        committedText += glyph
                        scope.launch { snackbarHostState.showSnackbar("🔒 $target-এর জন্য encrypt হয়েছে") }
                      }
                      return@EmbeddedKeyboardPreview
                    }
                    if (composingDisplay.isNotEmpty()) {
                      suggestionsRepo.learn(composingRoman, composingDisplay, previewBanglaMode)
                      committedText += composingDisplay
                      composingRoman = ""
                    }
                    suggestionsRepo.resetSentenceContext()
                    suggestVersion++
                    committedText += "\n"
                  },
                  onSwitchLang = {
                    if (previewNoteEditMode) {
                      if (previewNoteEditRoman.isNotEmpty()) {
                        previewNoteEditCommitted +=
                          if (previewBanglaMode && previewSymbolsMode == 0) Phonetic.convert(previewNoteEditRoman)
                          else previewNoteEditRoman
                        previewNoteEditRoman = ""
                      }
                    } else if (previewSecretMode) {
                      previewSecretText = activeSecretPlain
                    } else if (composingDisplay.isNotEmpty()) {
                      suggestionsRepo.learn(composingRoman, composingDisplay, previewBanglaMode)
                      committedText += composingDisplay
                      composingRoman = ""
                    }
                    previewPanelMode = "none"
                    previewSymbolsMode = 0
                    previewBanglaMode = !previewBanglaMode
                    prefs.edit().putBoolean("bn", previewBanglaMode).apply()
                    suggestVersion++
                  },
                  onSymbols = { mode ->
                    if (previewNoteEditMode) {
                      if (previewNoteEditRoman.isNotEmpty()) {
                        previewNoteEditCommitted +=
                          if (previewBanglaMode && previewSymbolsMode == 0) Phonetic.convert(previewNoteEditRoman)
                          else previewNoteEditRoman
                        previewNoteEditRoman = ""
                      }
                    } else if (previewSecretMode) {
                      previewSecretText = activeSecretPlain
                    } else if (composingDisplay.isNotEmpty()) {
                      suggestionsRepo.learn(composingRoman, composingDisplay, previewBanglaMode)
                      committedText += composingDisplay
                      composingRoman = ""
                      suggestVersion++
                    }
                    previewPanelMode = "none"
                    previewSymbolsMode = mode
                  },
                  onComposingTap = {
                    if (previewSecretMode) {
                      val names = SecretConfig.getContacts(context).keys.toList()
                      if (names.size > 1) {
                        val next = names[(names.indexOf(previewSecretTarget) + 1).mod(names.size)]
                        previewSecretTarget = next
                        SecretConfig.setActiveContact(context, next)
                      }
                    } else if (!previewNoteEditMode && composingDisplay.isNotEmpty()) {
                      suggestionsRepo.learn(composingRoman, composingDisplay, previewBanglaMode)
                      committedText += composingDisplay
                      composingRoman = ""
                      suggestVersion++
                    }
                  },
                  onSuggestionTap = { word ->
                    if (previewSecretMode) {
                      val trailing =
                        previewSecretText.takeLastWhile {
                          it in 'a'..'z' || it in 'A'..'Z' || it == '^' || it == '`' || it == '\''
                        }
                      previewSecretText = previewSecretText.dropLast(trailing.length) + "$word "
                    } else {
                      suggestionsRepo.learn(composingRoman, word, previewBanglaMode)
                      composingRoman = ""
                      committedText += "$word "
                      suggestVersion++
                    }
                  },
                  onSuggestionLongPress = { word ->
                    val removed = suggestionsRepo.forget(word, previewBanglaMode)
                    if (removed) {
                      suggestVersion++
                      Toast.makeText(context, "“$word” suggestion তালিকা থেকে সরানো হয়েছে", Toast.LENGTH_SHORT).show()
                    } else {
                      Toast.makeText(context, "ডিফল্ট শব্দ মুছে ফেলা যায় না", Toast.LENGTH_SHORT).show()
                    }
                  },
                  onSnippet = { snippet ->
                    if (previewSecretMode) {
                      previewSecretText += snippet
                      previewPanelMode = "none"
                    } else {
                      if (composingDisplay.isNotEmpty()) {
                        committedText += composingDisplay
                        composingRoman = ""
                      }
                      committedText += snippet
                      suggestVersion++
                    }
                  },
                  onNotepad = {
                    if (previewNoteEditMode) {
                      previewNoteEditMode = false
                      previewNoteEditCommitted = ""
                      previewNoteEditRoman = ""
                      previewPanelVersion++
                      previewPanelMode = "clipboard"
                      return@EmbeddedKeyboardPreview
                    }
                    if (composingDisplay.isNotEmpty() && !previewSecretMode) {
                      committedText += composingDisplay
                      composingRoman = ""
                    }
                    previewPanelVersion++
                    previewPanelMode = if (previewPanelMode == "clipboard") "none" else "clipboard"
                  },
                  onEmojiToggle = {
                    if (composingDisplay.isNotEmpty() && !previewSecretMode && !previewNoteEditMode) {
                      suggestionsRepo.learn(composingRoman, composingDisplay, previewBanglaMode)
                      committedText += composingDisplay
                      composingRoman = ""
                      suggestVersion++
                    }
                    if (previewPanelMode == "emoji") {
                      previewPanelMode = "none"
                    } else {
                      previewEmojiVersion++
                      previewPanelMode = "emoji"
                    }
                  },
                  onEmojiSelected = { emoji ->
                    if (previewNoteEditMode) {
                      if (previewNoteEditRoman.isNotEmpty()) {
                        previewNoteEditCommitted +=
                          if (previewBanglaMode && previewSymbolsMode == 0) Phonetic.convert(previewNoteEditRoman)
                          else previewNoteEditRoman
                        previewNoteEditRoman = ""
                      }
                      previewNoteEditCommitted += emoji
                    } else if (previewSecretMode) {
                      previewSecretText = activeSecretPlain + emoji
                    } else {
                      if (composingDisplay.isNotEmpty()) {
                        suggestionsRepo.learn(composingRoman, composingDisplay, previewBanglaMode)
                        committedText += composingDisplay
                        composingRoman = ""
                        suggestVersion++
                      }
                      committedText += emoji
                    }
                  },
                  onAddNewNote = {
                    previewNoteEditKind = "new"
                    previewNoteEditIndex = -1
                    previewNoteEditOldText = ""
                    previewNoteEditCommitted = ""
                    previewNoteEditRoman = ""
                    previewNoteEditMode = true
                    previewSymbolsMode = 0
                    previewPanelMode = "none"
                  },
                  onPinRecent = { text ->
                    Notes(context).add(text)
                    ClipboardMemory(context).remove(text)
                    previewPanelVersion++
                  },
                  onUnpinNote = { index, text ->
                    Notes(context).remove(index)
                    ClipboardMemory(context).add(text)
                    previewPanelVersion++
                  },
                  onEditPinned = { index, text ->
                    previewNoteEditKind = "pinned"
                    previewNoteEditIndex = index
                    previewNoteEditOldText = text
                    previewNoteEditCommitted = text
                    previewNoteEditRoman = ""
                    previewNoteEditMode = true
                    previewSymbolsMode = 0
                    previewPanelMode = "none"
                  },
                  onEditRecent = { text ->
                    previewNoteEditKind = "recent"
                    previewNoteEditIndex = -1
                    previewNoteEditOldText = text
                    previewNoteEditCommitted = text
                    previewNoteEditRoman = ""
                    previewNoteEditMode = true
                    previewSymbolsMode = 0
                    previewPanelMode = "none"
                  },
                  onRemovePinned = { index, _ ->
                    Notes(context).remove(index)
                    previewPanelVersion++
                  },
                  onRemoveRecent = { text ->
                    ClipboardMemory(context).remove(text)
                    previewPanelVersion++
                  },
                  onClearRecent = {
                    ClipboardMemory(context).clear()
                    previewPanelVersion++
                  },
                  onNoteEditSave = { savePreviewNoteEdit() },
                  onNoteEditCancel = {
                    previewNoteEditMode = false
                    previewNoteEditCommitted = ""
                    previewNoteEditRoman = ""
                    previewPanelVersion++
                    previewPanelMode = "clipboard"
                  },
                  onTranslate = {
                    val target =
                      if (previewSecretMode) activeSecretPlain.trim()
                      else if (composingDisplay.isNotEmpty()) composingDisplay
                      else committedText.trim()
                    if (target.isNotEmpty()) {
                      val from = if (previewBanglaMode) "bn" else "en"
                      val to = if (previewBanglaMode) "en" else "bn"
                      Translator.translate(target, from, to) { result ->
                        if (result != null) {
                          if (previewSecretMode) {
                            previewSecretText = result
                          } else if (composingDisplay.isNotEmpty()) {
                            composingRoman = ""
                            committedText += "$result "
                          } else {
                            committedText = result
                          }
                        } else {
                          Toast.makeText(context, "ইন্টারনেট সংযোগ প্রয়োজন", Toast.LENGTH_SHORT).show()
                        }
                      }
                    }
                  },
                  onVoice = {
                    Toast.makeText(
                      context,
                      "সিস্টেম কিবোর্ড হিসেবে সক্রিয় করে যেকোনো অ্যাপে ভয়েস টাইপিং ব্যবহার করুন",
                      Toast.LENGTH_SHORT
                    ).show()
                  },
                  onAssistant = {
                    context.startActivity(Intent(context, AssistantActivity::class.java))
                  },
                  onSecretToggle = {
                    previewNoteEditMode = false
                    previewNoteEditCommitted = ""
                    previewNoteEditRoman = ""
                    val contacts = SecretConfig.getContacts(context)
                    if (contacts.isEmpty()) {
                      scope.launch {
                        snackbarHostState.showSnackbar("আগে Secret Message ও AES Vault-এ একটি contact সেভ করুন")
                      }
                      return@EmbeddedKeyboardPreview
                    }
                    if (!previewSecretMode) {
                      val active = SecretConfig.getActiveContact(context)
                      previewSecretTarget = if (active != null && contacts.containsKey(active)) active else contacts.keys.first()
                      previewSecretText = ""
                      previewSecretMode = true
                      previewPanelMode = "none"
                      previewSymbolsMode = 0
                    } else {
                      previewSecretMode = false
                      previewSecretText = ""
                    }
                  },
                )
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(
              text = stringResource(R.string.phonetic_preview_title),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
              value = phoneticSampleInput,
              onValueChange = { phoneticSampleInput = it },
              placeholder = { Text(stringResource(R.string.phonetic_preview_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("phonetic_preview_input"),
              shape = RoundedCornerShape(14.dp),
            )
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(
                  text = convertedBangla.ifEmpty { "—" },
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.weight(1f).testTag("phonetic_converted_output"),
                )
                IconButton(
                  onClick = {
                    if (convertedBangla.isNotEmpty()) {
                      clipboardManager.setText(AnnotatedString(convertedBangla))
                      scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                    }
                  },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_bangla_button"),
                ) {
                  Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy_icon),
                    tint = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              listOf("ami", "bangla", "sOnar", "kkhomota", "tOmake", "bhalobashi").forEach { sample ->
                AssistChip(
                  onClick = { phoneticSampleInput = sample },
                  label = { Text(sample) },
                  modifier = Modifier.testTag("sample_chip_$sample"),
                )
              }
            }
          }
        }

        SetupCard(
          onEnableClick = {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
          },
          onSelectClick = {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
          },
        )

        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            SectionTitle(
              icon = Icons.Filled.RecordVoiceOver,
              title = stringResource(R.string.tools_section_title),
            )
            Text(
              text = stringResource(R.string.notepad_card_desc),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = { context.startActivity(Intent(context, AssistantActivity::class.java)) },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("open_assistant_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.RecordVoiceOver,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.btn_open_assistant))
            }
            OutlinedButton(
              onClick = { context.startActivity(Intent(context, CommandGuideActivity::class.java)) },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("open_command_guide_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.btn_open_command_guide))
            }
            OutlinedButton(
              onClick = { context.startActivity(Intent(context, NotepadActivity::class.java)) },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("open_notepad_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.EditNote,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.btn_open_notepad))
            }
            OutlinedButton(
              onClick = { context.startActivity(Intent(context, SecretComposeActivity::class.java)) },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("open_secret_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.btn_open_secret))
            }
          }
        }

        ThemePickerCard(onChanged = { themeVersion++ })

        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SectionTitle(
              icon = Icons.Filled.Tune,
              title = stringResource(R.string.section_settings),
            )

            PreferenceToggleRow(
              title = stringResource(R.string.pref_haptic_title),
              subtitle = stringResource(R.string.pref_haptic_desc),
              checked = haptic,
              testTag = "toggle_haptic",
              onCheckedChange = {
                haptic = it
                prefs.edit().putBoolean("haptic", it).apply()
              },
            )
            PreferenceToggleRow(
              title = stringResource(R.string.pref_sound_title),
              subtitle = stringResource(R.string.pref_sound_desc),
              checked = sound,
              testTag = "toggle_sound",
              onCheckedChange = {
                sound = it
                prefs.edit().putBoolean("sound", it).apply()
              },
            )
            PreferenceToggleRow(
              title = stringResource(R.string.pref_numrow_title),
              subtitle = stringResource(R.string.pref_numrow_desc),
              checked = numRow,
              testTag = "toggle_numrow",
              onCheckedChange = {
                numRow = it
                prefs.edit().putBoolean("numrow", it).apply()
              },
            )
            PreferenceToggleRow(
              title = stringResource(R.string.pref_bndigits_title),
              subtitle = stringResource(R.string.pref_bndigits_desc),
              checked = bnDigits,
              testTag = "toggle_bndigits",
              onCheckedChange = {
                bnDigits = it
                prefs.edit().putBoolean("bndigits", it).apply()
              },
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(R.string.pref_height_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
              )
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = "$heightPct%",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
            Slider(
              value = heightPct.toFloat(),
              onValueChange = { value ->
                val snapped = value.roundToInt().coerceIn(85, 125)
                heightPct = snapped
                prefs.edit().putInt("height", snapped).apply()
              },
              valueRange = 85f..125f,
              modifier = Modifier.fillMaxWidth().testTag("height_slider"),
            )
          }
        }

        ElevatedCard(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            SectionTitle(
              icon = Icons.Outlined.Info,
              title = stringResource(R.string.section_tips),
            )
            Text(
              text = stringResource(R.string.tips_summary),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf(
                "O → ও / ো",
                "o → অ / ভাঙা",
                "ng → ং",
                "Ng → ঙ",
                "NG → ঞ",
                "^ → ঁ",
                "t`` → ৎ",
                "kkh → ক্ষ",
                "rri → ঋ / ৃ",
              ).forEach { rule ->
                Surface(
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(
                    text = rule,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}

@Composable
private fun EmbeddedKeyboardPreview(
  banglaMode: Boolean,
  symbolsMode: Int,
  panelMode: String,
  secretMode: Boolean,
  secretTarget: String?,
  noteEditMode: Boolean,
  numRow: Boolean,
  bnDigitsPref: Boolean,
  heightPct: Int,
  themeVersion: Int,
  panelVersion: Int,
  emojiVersion: Int,
  composingText: String,
  suggestionsList: List<String>,
  onText: (String) -> Unit,
  onDelete: () -> Unit,
  onSpace: () -> Unit,
  onEnter: () -> Unit,
  onSwitchLang: () -> Unit,
  onSymbols: (Int) -> Unit,
  onComposingTap: () -> Unit,
  onSuggestionTap: (String) -> Unit,
  onSuggestionLongPress: (String) -> Unit,
  onSnippet: (String) -> Unit,
  onNotepad: () -> Unit,
  onEmojiToggle: () -> Unit,
  onEmojiSelected: (String) -> Unit,
  onAddNewNote: () -> Unit,
  onPinRecent: (String) -> Unit,
  onUnpinNote: (Int, String) -> Unit,
  onEditPinned: (Int, String) -> Unit,
  onEditRecent: (String) -> Unit,
  onRemovePinned: (Int, String) -> Unit,
  onRemoveRecent: (String) -> Unit,
  onClearRecent: () -> Unit,
  onNoteEditSave: () -> Unit,
  onNoteEditCancel: () -> Unit,
  onTranslate: () -> Unit,
  onVoice: () -> Unit,
  onAssistant: () -> Unit,
  onSecretToggle: () -> Unit,
) {
  val context = LocalContext.current
  val rows =
    remember(banglaMode, symbolsMode, numRow, bnDigitsPref) {
      val bnDigits = banglaMode && bnDigitsPref
      when (symbolsMode) {
        1 -> Layouts.symbols(bnDigits, banglaMode)
        2 -> Layouts.symbols2()
        else -> if (banglaMode) Layouts.phonetic(numRow, bnDigits) else Layouts.english(numRow)
      }
    }
  val spaceLabel = if (banglaMode) "বাংলা" else "English"

  AndroidView(
    modifier = Modifier.fillMaxWidth().testTag("embedded_keyboard_view"),
    factory = { ctx ->
      KeyboardHostLayout(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )
      }
    },
    update = { host ->
      val kv = host.keyboardView
      val panel = host.panelView
      val emojiPanel = host.emojiPanelView

      kv.listener =
        object : KeyboardView.Listener {
          override fun onText(text: String) = onText(text)
          override fun onDelete() = onDelete()
          override fun onEnter() = onEnter()
          override fun onSpace() = onSpace()
          override fun onSpaceLongPress() {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
          }
          override fun onSwitchLang() = onSwitchLang()
          override fun onSymbols(mode: Int) = onSymbols(mode)
          override fun onComposingTap() = onComposingTap()
          override fun onFeedback() {}
          override fun onSuggestionTap(word: String) = onSuggestionTap(word)
          override fun onSuggestionLongPress(word: String) = onSuggestionLongPress(word)
          override fun onSnippet(text: String) = onSnippet(text)
          override fun onNotepad() = onNotepad()
          override fun onTranslate() = onTranslate()
          override fun onVoice() = onVoice()
          override fun onAssistant() = onAssistant()
          override fun onSecretToggle() = onSecretToggle()
          override fun onEmojiToggle() = onEmojiToggle()
          override fun onNoteEditSave() = onNoteEditSave()
          override fun onNoteEditCancel() = onNoteEditCancel()
        }

      panel.listener =
        object : NotepadClipboardPanelView.Listener {
          override fun onBackToKeyboard() = onNotepad()
          override fun onPasteItem(text: String) = onSnippet(text)
          override fun onAddNewNote() = onAddNewNote()
          override fun onPinRecent(text: String) = onPinRecent(text)
          override fun onUnpinNote(index: Int, text: String) = onUnpinNote(index, text)
          override fun onEditPinned(index: Int, text: String) = onEditPinned(index, text)
          override fun onEditRecent(text: String) = onEditRecent(text)
          override fun onRemovePinned(index: Int, text: String) = onRemovePinned(index, text)
          override fun onRemoveRecent(text: String) = onRemoveRecent(text)
          override fun onClearRecent() = onClearRecent()
        }

      emojiPanel.listener =
        object : EmojiPanelView.Listener {
          override fun onBackToKeyboard() = onEmojiToggle()
          override fun onEmojiSelected(emoji: String) = onEmojiSelected(emoji)
          override fun onBackspace() = onDelete()
        }

      val layoutState = listOf(rows, spaceLabel, heightPct, themeVersion)
      if (kv.tag != layoutState) {
        kv.tag = layoutState
        kv.reloadTheme()
        panel.reloadTheme()
        emojiPanel.reloadTheme()
        kv.setLayout(rows, spaceLabel, heightPct / 100f)
      }

      val panelState = listOf(panelMode, panelVersion, themeVersion)
      if (panel.tag != panelState) {
        panel.tag = panelState
        if (panelMode == "clipboard") {
          panel.setData(Notes(context).all(), ClipboardMemory(context).clips())
        }
      }

      val emojiState = listOf(panelMode, emojiVersion, themeVersion)
      if (emojiPanel.tag != emojiState) {
        emojiPanel.tag = emojiState
        if (panelMode == "emoji") {
          emojiPanel.openPanel()
        }
      }

      val targetMode = when (panelMode) {
        "clipboard" -> KeyboardHostLayout.HostMode.CLIPBOARD
        "emoji" -> KeyboardHostLayout.HostMode.EMOJI
        else -> KeyboardHostLayout.HostMode.KEYBOARD
      }
      host.setMode(targetMode)

      kv.setSecretMode(secretMode, secretTarget)
      kv.setNoteEditMode(noteEditMode)
      kv.setComposing(composingText)
      kv.setSuggestions(suggestionsList)
    },
  )
}

@Composable
private fun HeroHeaderCard(
  imeStatus: ImeStatus,
  assistantRunning: Boolean,
  assistantReady: Boolean,
) {
  val gradient =
    Brush.linearGradient(
      colors = listOf(Color(0xFF015E57), Color(0xFF00796B), Color(0xFF00897B))
    )
  val statusText =
    when (imeStatus) {
      ImeStatus.ACTIVE -> stringResource(R.string.status_active)
      ImeStatus.ENABLED_ONLY -> stringResource(R.string.status_enabled)
      ImeStatus.DISABLED -> stringResource(R.string.status_disabled)
    }
  val assistantText =
    when {
      assistantRunning -> stringResource(R.string.assistant_badge_listening)
      assistantReady -> stringResource(R.string.assistant_badge_ready)
      else -> stringResource(R.string.assistant_badge_setup)
    }

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(gradient)
        .padding(20.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Image(
        painter = painterResource(id = R.drawable.banglakeys_icon),
        contentDescription = stringResource(R.string.cd_app_icon),
        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
      )
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = Color.White,
        )
        Text(
          text = stringResource(R.string.hero_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.88f),
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = Color.White.copy(alpha = 0.18f),
            shape = CircleShape,
            modifier = Modifier.testTag("ime_status_badge"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector =
                  if (imeStatus == ImeStatus.ACTIVE) Icons.Filled.CheckCircle else Icons.Filled.Keyboard,
                contentDescription = stringResource(R.string.cd_status_icon),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
              )
            }
          }
          Surface(
            color = Color.White.copy(alpha = 0.18f),
            shape = CircleShape,
            modifier = Modifier.testTag("assistant_status_badge"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.RecordVoiceOver,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = assistantText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SetupCard(
  onEnableClick: () -> Unit,
  onSelectClick: () -> Unit,
) {
  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = RoundedCornerShape(20.dp),
  ) {
    Column(
      modifier = Modifier.padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SectionTitle(
        icon = Icons.Filled.Settings,
        title = stringResource(R.string.setup_title),
      )
      Text(
        text = stringResource(R.string.setup_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(
        onClick = onEnableClick,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("enable_keyboard_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
      ) {
        Icon(
          imageVector = Icons.Filled.Settings,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.btn_enable_keyboard))
      }
      FilledTonalButton(
        onClick = onSelectClick,
        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("select_keyboard_button"),
        shape = RoundedCornerShape(14.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Keyboard,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.btn_select_keyboard))
      }
    }
  }
}

@Composable
private fun SectionTitle(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
private fun PreferenceToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  testTag: String,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.minimumInteractiveComponentSize().testTag(testTag),
    )
  }
}
