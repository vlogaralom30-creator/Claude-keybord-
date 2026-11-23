package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.AppFlows
import com.banglakeys.app.AppResolver
import com.banglakeys.app.AssistantPrefs
import com.banglakeys.app.ClipboardMemory
import com.banglakeys.app.Command
import com.banglakeys.app.CommandOverlayManager
import com.banglakeys.app.CommandParser
import com.banglakeys.app.Dir
import com.banglakeys.app.GeminiCommandInterpreter
import com.banglakeys.app.GeminiConfig
import com.banglakeys.app.KeyType
import com.banglakeys.app.Layouts
import com.banglakeys.app.ListeningMode
import com.banglakeys.app.Notes
import com.banglakeys.app.Phonetic
import com.banglakeys.app.SettingsTarget
import com.banglakeys.app.SmartCommandResolver
import com.banglakeys.app.Suggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Bangla Keys", appName)
  }

  @Test
  fun `phonetic conversion produces expected bangla`() {
    assertEquals("আমি", Phonetic.convert("ami"))
    assertEquals("বাংলা", Phonetic.convert("bangla"))
    assertEquals("তোমাকে", Phonetic.convert("tOmake"))
    assertEquals("ক্ষ", Phonetic.convert("kkh"))
  }

  @Test
  fun `suggestions learn and suggest words`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val suggestions = Suggestions(context)
    assertTrue(suggestions.suggestionsFor("am", true).contains("আমি"))
    suggestions.learn("dhk", "ঢাকা")
    assertTrue(suggestions.suggestionsFor("dhk", true).contains("ঢাকা"))
  }

  @Test
  fun `notes and clipboard memory persist items`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val notes = Notes(context)
    notes.add("আমার ঠিকানা")
    assertTrue(notes.all().contains("আমার ঠিকানা"))

    val clipboard = ClipboardMemory(context)
    clipboard.add("হ্যালো বাংলাদেশ")
    assertTrue(clipboard.clips().contains("হ্যালো বাংলাদেশ"))
    clipboard.remove("হ্যালো বাংলাদেশ")
    assertTrue(clipboard.clips().none { it == "হ্যালো বাংলাদেশ" })
  }

  @Test
  fun `layouts panel, voice, and assistant keys build properly`() {
    val rows = Layouts.panel(listOf("One", "Two", "Three"))
    assertEquals(3, rows.size)

    val phoneticBottom = Layouts.phonetic(numberRow = false, bnDigits = true).last()
    assertEquals(8, phoneticBottom.size)
    assertTrue(phoneticBottom.any { it.type == KeyType.EMOJI })
    assertTrue(phoneticBottom.any { it.type == KeyType.MIC })
    assertTrue(phoneticBottom.any { it.type == KeyType.ENTER })
    assertFalse(phoneticBottom.any { it.type == KeyType.NOTEPAD })
    assertFalse(phoneticBottom.any { it.type == KeyType.TRANSLATE })
    assertFalse(phoneticBottom.any { it.type == KeyType.ASSISTANT })

    val symbolsFirstRow = Layouts.symbols(bnDigits = true, taka = true).first()
    assertTrue(symbolsFirstRow.any { it.type == KeyType.NOTEPAD })
    assertTrue(symbolsFirstRow.any { it.type == KeyType.TRANSLATE })
    assertTrue(symbolsFirstRow.any { it.type == KeyType.ASSISTANT })
  }

  @Test
  fun `existing regression commands and v2 fixes parse accurately`() {
    val (woke, rest) = CommandParser.splitWake("Hey Bot open TikTok")
    assertTrue(woke)
    assertEquals(Command.OpenApp("tiktok"), CommandParser.parse(rest))
    assertEquals(Command.OpenApp("claude"), CommandParser.parse("open claude"))
    assertEquals(Command.OpenApp("chatgpt"), CommandParser.parse("open chatgpt"))
    assertEquals(Command.Swipe(Dir.UP), CommandParser.parse("up"))
    assertEquals(Command.Swipe(Dir.UP), CommandParser.parse("উপরে"))
    assertEquals(Command.Like, CommandParser.parse("sundor"))
    assertEquals(Command.Call("fatema"), CommandParser.parse("call fatema"))
    assertEquals(Command.Call("ammi"), CommandParser.parse("ammi ke call koro"))
    assertEquals(Command.TypeText("hello world"), CommandParser.parse("type hello world"))
    assertEquals(
      Command.YoutubeSearch("tum hi ho", mostViewed = true),
      CommandParser.parse("youtube search tum hi ho most viewed")
    )
    assertEquals(Command.PanicStop, CommandParser.parse("stop everything"))
    assertEquals(Command.PanicStop, CommandParser.parse("সব বন্ধ"))
    assertEquals(Command.Home, CommandParser.parse("exit app"))
    assertEquals(Command.Home, CommandParser.parse("close app"))
    assertEquals(Command.Home, CommandParser.parse("অ্যাপ বন্ধ"))
    assertEquals(Command.StopAssistant, CommandParser.parse("quit"))

    assertNotNull(AppResolver.ambiguousPrompt("reel"))
    assertNotNull(AppResolver.ambiguousPrompt("shorts"))
    assertNotNull(AppResolver.ambiguousPrompt("ভিডিও"))
    assertEquals(null, AppResolver.ambiguousPrompt("tiktok"))
  }

  @Test
  fun `pack F parses folder and file creation in english banglish and bangla`() {
    assertEquals(Command.CreateFolder("photos", null), CommandParser.parse("create folder photos"))
    assertEquals(Command.CreateFolder("ছবি", null), CommandParser.parse("ফোল্ডার বানাও ছবি"))
    assertEquals(
      Command.CreateFolder("tour", "photos"),
      CommandParser.parse("create folder tour inside photos")
    )
    assertEquals(
      Command.CreateFolder("tour", "photos"),
      CommandParser.parse("photos er moddhe folder banao tour")
    )
    assertEquals(Command.CreateFile("notes", "txt", null), CommandParser.parse("create file notes"))
    assertEquals(Command.CreateFile("notes", "txt", null), CommandParser.parse("create file notes dot txt"))
    assertEquals(Command.CreateFile("নোটস", "txt", null), CommandParser.parse("ফাইল বানাও নোটস"))
    assertEquals(
      Command.CreateFile("shopping", "txt", "milk eggs bread"),
      CommandParser.parse("create file shopping with text milk eggs bread")
    )
    assertEquals(
      Command.CreateFile("data", "csv", null),
      CommandParser.parse("create file data dot csv")
    )
  }

  @Test
  fun `pack G parses AI chat and current app type-and-send`() {
    assertEquals(
      Command.AppChat("claude", "hi", send = true),
      CommandParser.parse("open claude type hi and send")
    )
    assertEquals(
      Command.AppChat("claude", "hi", send = true),
      CommandParser.parse("claude khulo hi likho send koro")
    )
    assertEquals(
      Command.AppChat("claude", "হাই", send = true),
      CommandParser.parse("ক্লড খোলো হাই লিখে পাঠাও")
    )
    assertEquals(
      Command.AppChat("chatgpt", "hello", send = false),
      CommandParser.parse("open chatgpt type hello")
    )
    assertEquals(
      Command.AppChat("gemini", "tea and coffee", send = true),
      CommandParser.parse("open gemini type tea and coffee and send")
    )
    assertEquals(
      Command.AppChat(null, "hi", send = true),
      CommandParser.parse("type hi and send")
    )
    // Misheard alias check
    assertEquals("claude", AppFlows.find("cloud")?.key)
    assertEquals("imo", AppFlows.find("emo")?.key)
    assertEquals("imo", AppFlows.find("ইমু")?.key)
    assertNotNull(AppFlows.find("চ্যাট জিপিটি"))
  }

  @Test
  fun `pack H parses messaging app calls and messages`() {
    assertEquals(
      Command.AppCall("imo", "fatema", video = false),
      CommandParser.parse("open imo call fatema")
    )
    assertEquals(
      Command.AppCall("imo", "fatema", video = false),
      CommandParser.parse("imo te fatema ke call koro")
    )
    assertEquals(
      Command.AppCall("imo", "fatema", video = true),
      CommandParser.parse("open imo video call fatema")
    )
    assertEquals(
      Command.AppCall("imo", "fatema", video = true),
      CommandParser.parse("imo te fatema ke video call")
    )
    assertEquals(
      Command.AppCall("imo", "ফাতেমা", video = true),
      CommandParser.parse("ইমুতে ফাতেমাকে ভিডিও কল")
    )
    assertEquals(
      Command.AppCall("whatsapp", "ammi", video = false),
      CommandParser.parse("whatsapp call ammi audio")
    )
    assertEquals(
      Command.AppCall("imo", "fatema", video = true),
      CommandParser.parse("call fatema on imo video")
    )
    assertEquals(
      Command.AppMessage("whatsapp", "fatema", "hi", send = true),
      CommandParser.parse("whatsapp e fatema ke hi pathao")
    )
  }

  @Test
  fun `pack I parses everyday commands with bangla and english numbers`() {
    assertEquals(Command.OpenCamera, CommandParser.parse("open camera"))
    assertEquals(Command.TakePhoto, CommandParser.parse("take photo"))
    assertEquals(Command.SetAlarm(7, 30), CommandParser.parse("set alarm 7 30 am"))
    assertEquals(Command.SetAlarm(7, 30), CommandParser.parse("7:30 am e alarm"))
    assertEquals(Command.SetAlarm(7, 30), CommandParser.parse("সকাল ৭টা ৩০ এ alarm"))
    assertEquals(Command.SetTimer(300), CommandParser.parse("set timer 5 minutes"))
    assertEquals(Command.SetTimer(300), CommandParser.parse("5 minute timer"))
    assertEquals(Command.Navigate("dhaka airport"), CommandParser.parse("navigate to Dhaka airport"))
    assertEquals(Command.WebSearch("best phones"), CommandParser.parse("search google for best phones"))
    assertEquals(Command.PlaySong("tum hi ho"), CommandParser.parse("play song tum hi ho"))
    assertEquals(Command.SaveNote("buy milk"), CommandParser.parse("save note buy milk"))
    assertEquals(
      Command.WriteEmail("fatema", "meeting", "tomorrow at 10"),
      CommandParser.parse("write email to fatema subject meeting body tomorrow at 10")
    )
    assertEquals(
      Command.WriteSms("ammi", "ami ashchi"),
      CommandParser.parse("write sms to ammi ami ashchi")
    )
    assertEquals(
      Command.OpenSystemSettings(SettingsTarget.WIFI),
      CommandParser.parse("open wifi settings")
    )
    assertEquals(
      Command.OpenSystemSettings(SettingsTarget.BLUETOOTH),
      CommandParser.parse("open bluetooth settings")
    )
  }

  @Test
  fun `assistant prefs defaults and bounds match T11 spec`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(AssistantPrefs.confirmBeforeAppCall(context))
    assertTrue(AssistantPrefs.confirmBeforeHumanSend(context))
    assertFalse(AssistantPrefs.confirmBeforeAiChatSend(context))
    assertEquals(6, AssistantPrefs.appFlowTimeoutSec(context))
    AssistantPrefs.setAppFlowTimeoutSec(context, 99)
    assertEquals(15, AssistantPrefs.appFlowTimeoutSec(context))
    AssistantPrefs.setAppFlowTimeoutSec(context, 0)
    assertEquals(2, AssistantPrefs.appFlowTimeoutSec(context))
  }

  @Test
  fun `assistant activity and vector icons render without errors`() {
    val activity = org.robolectric.Robolectric.buildActivity(com.banglakeys.app.AssistantActivity::class.java)
      .create()
      .start()
      .resume()
      .get()
    assertNotNull(activity)

    val kv = com.banglakeys.app.KeyboardView(activity)
    kv.setLayout(Layouts.phonetic(numberRow = false, bnDigits = true), "বাংলা", 1f)
    kv.measure(
      android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
      android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.AT_MOST)
    )
    kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)
    val bmp = android.graphics.Bitmap.createBitmap(
      kv.measuredWidth.coerceAtLeast(1),
      kv.measuredHeight.coerceAtLeast(1),
      android.graphics.Bitmap.Config.ARGB_8888
    )
    kv.draw(android.graphics.Canvas(bmp))

    for (kind in com.banglakeys.app.AssistantIcons.Kind.values()) {
      val d = com.banglakeys.app.AssistantIcons.drawable(activity, kind, 0xFF00796B.toInt(), 24)
      d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
      d.draw(android.graphics.Canvas(bmp))
    }
  }

  @Test
  fun `smart command resolver handles N-best, fuzzy wake, and Banglish mishearings`() {
    // 1. Fuzzy wake word "hey board up" -> woke = true, Swipe(UP)
    val wakeRes = SmartCommandResolver.resolve(listOf("hey board up"), alreadyAwake = false)
    assertTrue(wakeRes.woke)
    assertEquals(Command.Swipe(Dir.UP), wakeRes.command)

    // 2. N-best picks accurate candidate from index 2 when index 0 is garbled
    val nBest = SmartCommandResolver.resolve(
      candidates = listOf("oh pen dick duck", "open tick tuck", "open tiktok"),
      alreadyAwake = true
    )
    assertEquals(Command.OpenApp("tiktok"), nBest.command)
    assertNotNull(nBest.summary)

    // 3. Phonetic & fuzzy token normalization: "kholoo whatsapp" / "open tiktak"
    val fuzzyOpen = SmartCommandResolver.resolve(listOf("open tiktak"), alreadyAwake = true)
    assertEquals(Command.OpenApp("tiktak"), fuzzyOpen.command)

    val fuzzyBanglish = SmartCommandResolver.resolve(listOf("kholoo whatsapp"), alreadyAwake = true)
    assertEquals(Command.OpenApp("whatsapp"), fuzzyBanglish.command)

    // 4. Imo call mishearing: "emo te fatema ke call koro"
    val emoCall = SmartCommandResolver.resolve(listOf("emo te fatema ke call koro"), alreadyAwake = true)
    assertEquals(Command.AppCall("imo", "fatema", video = false), emoCall.command)

    // 5. Payload preservation: free text after "type" must NOT be rewritten to keywords
    val typed = SmartCommandResolver.resolve(listOf("type tiktak kholoo"), alreadyAwake = true)
    assertEquals(Command.TypeText("tiktak kholoo"), typed.command)

    // 6. Describe produces human-readable summary
    assertTrue(SmartCommandResolver.describe(Command.AppCall("imo", "fatema", video = true)).contains("imo"))
  }

  @Test
  fun `listening mode and overlay HUD prefs persist properly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(ListeningMode.CONTINUOUS, AssistantPrefs.listeningMode(context))
    assertTrue(AssistantPrefs.overlayHudEnabled(context))

    AssistantPrefs.setListeningMode(context, ListeningMode.PUSH_TO_TALK)
    AssistantPrefs.setOverlayHudEnabled(context, false)

    assertEquals(ListeningMode.PUSH_TO_TALK, AssistantPrefs.listeningMode(context))
    assertFalse(AssistantPrefs.overlayHudEnabled(context))
  }

  @Test
  fun `command overlay manager renders all pill states cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var confirmClicks = 0
    var cancelClicks = 0
    var stopClicks = 0
    var pttClicks = 0
    var dismissClicks = 0

    val overlay = CommandOverlayManager(
      ctx = context,
      onConfirmClick = { confirmClicks++ },
      onCancelClick = { cancelClicks++ },
      onStopFlowClick = { stopClicks++ },
      onPttTriggerClick = { pttClicks++ },
      onDismissOrSleepClick = { dismissClicks++ }
    )

    val states = listOf(
      CommandOverlayManager.OverlayState.Dormant(isBangla = true),
      CommandOverlayManager.OverlayState.Listening(partialText = "open tiktok", awakeSec = 10, isPtt = false),
      CommandOverlayManager.OverlayState.AwaitingConfirm(
        heardText = "imo te fatema ke call koro",
        actionSummary = "imo অডিও কল: fatema",
        question = "imo-তে fatema-কে কল করবেন?"
      ),
      CommandOverlayManager.OverlayState.RunningFlow(appLabel = "WhatsApp", stepStatus = "অপেক্ষা করা হচ্ছে…"),
      CommandOverlayManager.OverlayState.ResultFeedback(
        heardText = "unknown phrase",
        commandSummary = null,
        message = "কমান্ড বোঝা যায়নি",
        isSuccess = false,
        canRetry = true
      )
    )

    for (state in states) {
      val v = overlay.buildViewForState(state)
      v.measure(
        android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.AT_MOST),
        android.view.View.MeasureSpec.makeMeasureSpec(800, android.view.View.MeasureSpec.AT_MOST)
      )
      assertTrue(v.measuredWidth > 0)
      assertTrue(v.measuredHeight > 0)
      v.layout(0, 0, v.measuredWidth, v.measuredHeight)
      val bmp = android.graphics.Bitmap.createBitmap(
        v.measuredWidth,
        v.measuredHeight,
        android.graphics.Bitmap.Config.ARGB_8888
      )
      v.draw(android.graphics.Canvas(bmp))
    }
    assertEquals(0, confirmClicks + cancelClicks + stopClicks + pttClicks + dismissClicks)
  }

  @Test
  fun `gemini config stores user key and ignores placeholder`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    GeminiConfig.setKey(context, "")
    assertEquals("", GeminiConfig.getUserKey(context))
    assertEquals("", GeminiConfig.getEnvKey())

    GeminiConfig.setKey(context, "AIzaSyTestExampleKey1234567890")
    assertTrue(GeminiConfig.hasKey(context))
    assertEquals(GeminiConfig.KeySource.USER_SETTINGS, GeminiConfig.keySource(context))
    assertTrue(GeminiConfig.looksLikeStandardGeminiKey(GeminiConfig.getKey(context)))
    assertFalse(GeminiConfig.looksLikeStandardGeminiKey("AQ.short_non_aiza"))

    GeminiConfig.setKey(context, "")
    assertFalse(GeminiConfig.hasKey(context))
  }

  @Test
  fun `gemini command interpreter maps structured json across all packs`() {
    assertEquals(
      Command.OpenApp("tiktok"),
      GeminiCommandInterpreter.parseJsonResponse("""{"type":"open_app","name":"tiktok"}""")
    )
    assertEquals(
      Command.Swipe(Dir.UP),
      GeminiCommandInterpreter.parseJsonResponse("""{"type":"swipe","direction":"up"}""")
    )
    assertEquals(
      Command.AppCall("imo", "fatema", video = true),
      GeminiCommandInterpreter.parseJsonResponse(
        """{"type":"app_call","app_name":"imo","contact":"fatema","video":true}"""
      )
    )
    assertEquals(
      Command.AppMessage("whatsapp", "ammi", "ami ashchi", send = true),
      GeminiCommandInterpreter.parseJsonResponse(
        """{"type":"app_message","app_name":"whatsapp","contact":"ammi","text":"ami ashchi","send":true}"""
      )
    )
    assertEquals(
      Command.CreateFolder("tour", "photos"),
      GeminiCommandInterpreter.parseJsonResponse(
        """{"type":"create_folder","name":"tour","parent_folder":"photos"}"""
      )
    )
    assertEquals(
      Command.SetTimer(300),
      GeminiCommandInterpreter.parseJsonResponse("""{"type":"set_timer","seconds":300}""")
    )
    assertEquals(
      Command.OpenSystemSettings(SettingsTarget.WIFI),
      GeminiCommandInterpreter.parseJsonResponse(
        """{"type":"open_system_settings","settings_target":"wifi"}"""
      )
    )
    assertEquals(
      Command.PanicStop,
      GeminiCommandInterpreter.parseJsonResponse("""{"type":"panic_stop"}""")
    )
    assertEquals(
      null,
      GeminiCommandInterpreter.parseJsonResponse("""{"type":"unknown"}""")
    )
  }

  @Test
  fun `suggestions engine supports english, case preservation, language isolation, and forget`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("bk_suggest", Context.MODE_PRIVATE).edit().clear().commit()
    val suggestions = Suggestions(context)

    // Cold-start idle suggestions should never be empty
    assertTrue(suggestions.suggestionsFor("", bangla = true).isNotEmpty())
    assertTrue(suggestions.suggestionsFor("", bangla = false).isNotEmpty())

    // English prefix completions and case preservation
    val lowerEn = suggestions.suggestionsFor("th", bangla = false)
    assertTrue(lowerEn.contains("the"))
    val titleEn = suggestions.suggestionsFor("Th", bangla = false)
    assertTrue(titleEn.any { it.startsWith("Th") })
    val capsEn = suggestions.suggestionsFor("TH", bangla = false)
    assertTrue(capsEn.contains("THE"))

    // Language isolation: learned Bangla must not pollute English, and vice versa
    suggestions.learn("khl", "খুলনা", bangla = true)
    assertTrue(suggestions.suggestionsFor("khl", bangla = true).contains("খুলনা"))
    assertFalse(suggestions.suggestionsFor("khl", bangla = false).contains("খুলনা"))

    suggestions.learn("ktl", "kotlin", bangla = false)
    assertTrue(suggestions.suggestionsFor("ktl", bangla = false).contains("kotlin"))
    assertFalse(suggestions.suggestionsFor("ktl", bangla = true).contains("kotlin"))

    // Forget removes learned word
    assertTrue(suggestions.isLearned("kotlin", bangla = false))
    assertTrue(suggestions.forget("kotlin", bangla = false))
    assertFalse(suggestions.isLearned("kotlin", bangla = false))
    assertFalse(suggestions.suggestionsFor("ktl", bangla = false).contains("kotlin"))

    // Bangla phonetic live fallback for novel words not in starter seed
    val customBn = suggestions.suggestionsFor("pkh", bangla = true)
    assertTrue(customBn.contains(Phonetic.convert("pkh")))
  }

  @Test
  fun `keyboardView renders typing composing badge plus pills and dispatches idle quick actions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val kv = com.banglakeys.app.KeyboardView(context)
    kv.setLayout(Layouts.phonetic(numberRow = false, bnDigits = true), "বাংলা", 1f)
    kv.measure(
      android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
      android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.AT_MOST)
    )
    kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)

    var composingTapped = 0
    val tappedWords = ArrayList<String>()
    var emojiTapped = 0
    var notepadTapped = 0
    var translateTapped = 0
    var voiceTapped = 0
    var assistantTapped = 0

    kv.listener = object : com.banglakeys.app.KeyboardView.Listener {
      override fun onText(text: String) {}
      override fun onDelete() {}
      override fun onEnter() {}
      override fun onSpace() {}
      override fun onSpaceLongPress() {}
      override fun onSwitchLang() {}
      override fun onSymbols(mode: Int) {}
      override fun onComposingTap() { composingTapped++ }
      override fun onFeedback() {}
      override fun onSuggestionTap(word: String) { tappedWords.add(word) }
      override fun onSnippet(text: String) {}
      override fun onNotepad() { notepadTapped++ }
      override fun onTranslate() { translateTapped++ }
      override fun onVoice() { voiceTapped++ }
      override fun onAssistant() { assistantTapped++ }
      override fun onEmojiToggle() { emojiTapped++ }
    }

    // 1) Typing state: Composing badge + 3 suggestion pills
    kv.setComposing("আমি")
    kv.setSuggestions(listOf("আমি", "আমাদের", "আমরা"))
    val composingRect = kv.stripComposingRect()
    val wordRects = kv.stripSuggestionRects()
    assertFalse(composingRect.isEmpty)
    assertEquals(3, wordRects.size)
    assertTrue(kv.stripQuickActionRects().isEmpty())

    // Draw once to verify zero exceptions
    val bmp = android.graphics.Bitmap.createBitmap(
      kv.measuredWidth,
      kv.measuredHeight,
      android.graphics.Bitmap.Config.ARGB_8888
    )
    kv.draw(android.graphics.Canvas(bmp))

    fun tap(x: Float, y: Float) {
      val now = android.os.SystemClock.uptimeMillis()
      val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
      val up = android.view.MotionEvent.obtain(now, now + 30, android.view.MotionEvent.ACTION_UP, x, y, 0)
      kv.onTouchEvent(down)
      kv.onTouchEvent(up)
      down.recycle()
      up.recycle()
    }

    tap(composingRect.centerX(), composingRect.centerY())
    assertEquals(1, composingTapped)

    tap(wordRects[1].centerX(), wordRects[1].centerY())
    assertEquals(listOf("আমাদের"), tappedWords)

    // 2) Idle state: 5 vector quick actions + starter/next-word pills
    kv.setComposing("")
    kv.setSuggestions(listOf("আমি", "কেমন", "আজ"))
    kv.draw(android.graphics.Canvas(bmp))

    assertTrue(kv.stripComposingRect().isEmpty)
    val actions = kv.stripQuickActionRects()
    assertEquals(5, actions.size)
    assertTrue(kv.stripSuggestionRects().isNotEmpty())

    tap(actions[com.banglakeys.app.KeyboardView.QuickAction.EMOJI]!!.centerX(),
      actions[com.banglakeys.app.KeyboardView.QuickAction.EMOJI]!!.centerY())
    tap(actions[com.banglakeys.app.KeyboardView.QuickAction.NOTEPAD]!!.centerX(),
      actions[com.banglakeys.app.KeyboardView.QuickAction.NOTEPAD]!!.centerY())
    tap(actions[com.banglakeys.app.KeyboardView.QuickAction.TRANSLATE]!!.centerX(),
      actions[com.banglakeys.app.KeyboardView.QuickAction.TRANSLATE]!!.centerY())
    tap(actions[com.banglakeys.app.KeyboardView.QuickAction.VOICE]!!.centerX(),
      actions[com.banglakeys.app.KeyboardView.QuickAction.VOICE]!!.centerY())
    tap(actions[com.banglakeys.app.KeyboardView.QuickAction.ASSISTANT]!!.centerX(),
      actions[com.banglakeys.app.KeyboardView.QuickAction.ASSISTANT]!!.centerY())

    assertEquals(1, emojiTapped)
    assertEquals(1, notepadTapped)
    assertEquals(1, translateTapped)
    assertEquals(1, voiceTapped)
    assertEquals(1, assistantTapped)
  }

  @Test
  fun `side-edge overlay prefs persist edge, clamped height, and compact vs expanded widths`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AssistantPrefs.resetOverlayPosition(context)
    assertEquals(com.banglakeys.app.OverlayEdge.RIGHT, AssistantPrefs.overlayEdge(context))
    assertEquals(AssistantPrefs.DEFAULT_OVERLAY_Y_RATIO, AssistantPrefs.overlayYRatio(context), 0.001f)

    AssistantPrefs.setOverlayEdge(context, com.banglakeys.app.OverlayEdge.LEFT)
    AssistantPrefs.setOverlayYRatio(context, 0.99f)
    assertEquals(com.banglakeys.app.OverlayEdge.LEFT, AssistantPrefs.overlayEdge(context))
    assertEquals(AssistantPrefs.MAX_OVERLAY_Y_RATIO, AssistantPrefs.overlayYRatio(context), 0.001f)

    val overlay = CommandOverlayManager(
      ctx = context,
      onConfirmClick = {},
      onCancelClick = {},
      onStopFlowClick = {},
      onPttTriggerClick = {},
      onDismissOrSleepClick = {}
    )

    val dormantState = CommandOverlayManager.OverlayState.Dormant(isBangla = true)
    val passiveListen = CommandOverlayManager.OverlayState.Listening(partialText = "", awakeSec = null, isPtt = false)
    val activeListen = CommandOverlayManager.OverlayState.Listening(partialText = "open youtube", awakeSec = 10, isPtt = true)

    assertTrue(overlay.isCompactTab(dormantState))
    assertTrue(overlay.isCompactTab(passiveListen))
    assertFalse(overlay.isCompactTab(activeListen))

    val tabView = overlay.buildViewForState(dormantState)
    tabView.measure(
      android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.AT_MOST),
      android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.AT_MOST)
    )
    val cardView = overlay.buildViewForState(activeListen)
    cardView.measure(
      android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.AT_MOST),
      android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.AT_MOST)
    )
    assertTrue(tabView.measuredWidth < cardView.measuredWidth)
  }

  @Test
  fun `adSkipper skips youtube and verified sdk ads while blocking registration, forms, and countdowns`() {
    fun node(
      id: Int,
      viewId: String = "",
      cls: String = "android.widget.TextView",
      text: String = "",
      desc: String = "",
      hint: String = "",
      clickable: Boolean = true,
      enabled: Boolean = true,
      visible: Boolean = true,
      editable: Boolean = false,
      focused: Boolean = false,
      password: Boolean = false,
      l: Int = 880,
      t: Int = 60,
      r: Int = 1040,
      b: Int = 140,
      parentId: Int = 0
    ) = com.banglakeys.app.AdSkipper.AdNode(
      id = id,
      viewId = viewId,
      className = cls,
      text = text,
      contentDescription = desc,
      hintText = hint,
      isClickable = clickable,
      isEnabled = enabled,
      isVisible = visible,
      isEditable = editable,
      isFocused = focused,
      isPassword = password,
      left = l,
      top = t,
      right = r,
      bottom = b,
      parentId = parentId
    )

    // 1. Positive: YouTube ready skip_ad_button
    val ytReady = listOf(
      node(1, viewId = "com.google.android.youtube:id/ad_badge", text = "Sponsored", clickable = false),
      node(2, viewId = "com.google.android.youtube:id/skip_ad_button", text = "Skip", l = 800, t = 900, r = 1040, b = 980)
    )
    val ytMatch = com.banglakeys.app.AdSkipper.findSkipTarget("com.google.android.youtube", 1080, 1920, ytReady, allowGameAds = false)
    assertNotNull(ytMatch)
    assertEquals(2, ytMatch?.nodeId)

    // 2. Negative: YouTube countdown still active ("Skip in 4")
    val ytCountdown = listOf(
      node(1, viewId = "com.google.android.youtube:id/skip_ad_button", text = "Skip in 4", l = 800, t = 900, r = 1040, b = 980)
    )
    assertEquals(null, com.banglakeys.app.AdSkipper.findSkipTarget("com.google.android.youtube", 1080, 1920, ytCountdown, allowGameAds = true))

    // 3. Positive: Game interstitial with verified AdMob/AppLovin proof + corner X close (ignores Install CTA)
    val gameAd = listOf(
      node(1, viewId = "com.applovin.sdk:id/al_ad_view", text = "Test Ad", clickable = false, l = 20, t = 40, r = 240, b = 90),
      node(2, text = "Install Now", l = 100, t = 1500, r = 980, b = 1650),
      node(3, viewId = "com.applovin.sdk:id/al_close_button", desc = "×", l = 960, t = 50, r = 1040, b = 130)
    )
    val gameMatch = com.banglakeys.app.AdSkipper.findSkipTarget("com.example.puzzle.game", 1080, 1920, gameAd, allowGameAds = true)
    assertNotNull(gameMatch)
    assertEquals(3, gameMatch?.nodeId)
    assertEquals(null, com.banglakeys.app.AdSkipper.findSkipTarget("com.example.puzzle.game", 1080, 1920, gameAd, allowGameAds = false))

    // 4. Negative: Registration / Sign-Up form with "Skip" button MUST NEVER be clicked
    val signUpForm = listOf(
      node(1, text = "Create Account", clickable = false, l = 40, t = 120, r = 600, b = 200),
      node(2, cls = "android.widget.EditText", hint = "Phone number", editable = true, clickable = true, l = 40, t = 300, r = 900, b = 420),
      node(3, text = "Skip", l = 860, t = 80, r = 1020, b = 150)
    )
    assertEquals(null, com.banglakeys.app.AdSkipper.findSkipTarget("com.some.social.app", 1080, 1920, signUpForm, allowGameAds = true))

    // 5. Negative: Onboarding "Skip for now" or generic Close dialog with no Ad-SDK proof MUST NOT be clicked
    val onboarding = listOf(
      node(1, text = "Welcome! Choose your interests", clickable = false, l = 40, t = 120, r = 800, b = 220),
      node(2, text = "Skip for now", l = 820, t = 80, r = 1040, b = 140)
    )
    assertEquals(null, com.banglakeys.app.AdSkipper.findSkipTarget("com.some.news.app", 1080, 1920, onboarding, allowGameAds = true))

    val regularDialog = listOf(
      node(1, text = "Save changes before closing?", clickable = false, l = 100, t = 700, r = 900, b = 850),
      node(2, desc = "×", l = 920, t = 80, r = 1000, b = 150)
    )
    assertEquals(null, com.banglakeys.app.AdSkipper.findSkipTarget("com.some.editor.app", 1080, 1920, regularDialog, allowGameAds = true))
  }

  @Test
  fun `banglaVocab update parses volume steps, mute, close apps, leave app, implicit youtube, and files`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // App opening including Files fallback
    assertEquals(Command.OpenApp("tiktok"), CommandParser.parse("tiktok khulo"))
    assertEquals(Command.OpenApp("file"), CommandParser.parse("file e dhuko"))
    assertEquals(Command.OpenApp("ফাইল"), CommandParser.parse("ফাইল এ ঢুকো"))
    val filesIntent = AppResolver.launchIntent(context, "file")
    assertNotNull(filesIntent)

    // Leave app & minimize
    assertEquals(Command.Home, CommandParser.parse("ber hao"))
    assertEquals(Command.Home, CommandParser.parse("বের হও"))
    assertEquals(Command.Home, CommandParser.parse("bahire jao"))
    assertEquals(Command.Home, CommandParser.parse("minimize koro"))
    assertEquals(Command.Home, CommandParser.parse("মিনিমাইজ করো"))

    // Back
    assertEquals(Command.Back, CommandParser.parse("back hao"))
    assertEquals(Command.Back, CommandParser.parse("pechone jao"))

    // Implicit YouTube play (with hyphen normalization and Bangla media terms)
    assertEquals(
      Command.YoutubeSearch("tum hi ho", mostViewed = false),
      CommandParser.parse("youtube search koro tum hi ho")
    )
    assertEquals(
      Command.YoutubeSearch("adele hello", mostViewed = false),
      CommandParser.parse("adele-hello video play koro")
    )
    assertEquals(
      Command.YoutubeSearch("tum hi ho", mostViewed = false),
      CommandParser.parse("tum hi ho gaan chalao")
    )

    // Swipes
    assertEquals(Command.Swipe(Dir.UP), CommandParser.parse("upore uthao"))
    assertEquals(Command.Swipe(Dir.UP), CommandParser.parse("উপরে উঠাও"))
    assertEquals(Command.Swipe(Dir.DOWN), CommandParser.parse("niche namao"))
    assertEquals(Command.Swipe(Dir.DOWN), CommandParser.parse("নিচে নামাও"))

    // Volume steps, Mute, Unmute
    assertEquals(Command.VolumeUp(1), CommandParser.parse("volume barao"))
    assertEquals(Command.VolumeDown(1), CommandParser.parse("awaj komao"))
    assertEquals(Command.VolumeUp(5), CommandParser.parse("volume full koro"))
    assertEquals(Command.VolumeDown(5), CommandParser.parse("volume komao onek"))
    assertEquals(Command.Mute, CommandParser.parse("mute"))
    assertEquals(Command.Mute, CommandParser.parse("chup"))
    assertEquals(Command.Unmute, CommandParser.parse("unmute"))
    assertEquals(Command.Unmute, CommandParser.parse("আওয়াজ চালু"))

    // CloseAllApps vs CloseApp vs Home
    assertEquals(Command.CloseAllApps, CommandParser.parse("sob app close koro"))
    assertEquals(Command.CloseAllApps, CommandParser.parse("সব অ্যাপ বন্ধ করো"))
    assertEquals(Command.CloseApp, CommandParser.parse("ei app bondho koro"))
    assertEquals(Command.CloseApp, CommandParser.parse("এই অ্যাপ বন্ধ করো"))

    // SmartCommandResolver & Gemini mappings for new commands
    assertTrue(SmartCommandResolver.describe(Command.VolumeUp(5)).contains("5x"))
    assertEquals("Sound Mute", SmartCommandResolver.describe(Command.Mute))
    assertEquals("সব অ্যাপ বন্ধ", SmartCommandResolver.describe(Command.CloseAllApps))
    assertEquals("বর্তমান অ্যাপ বন্ধ", SmartCommandResolver.describe(Command.CloseApp))

    assertEquals(
      Command.VolumeUp(5),
      GeminiCommandInterpreter.parseJsonResponse("""{"type":"volume_up","steps":5}""")
    )
    assertEquals(Command.Mute, GeminiCommandInterpreter.parseJsonResponse("""{"type":"mute"}"""))
    assertEquals(Command.Unmute, GeminiCommandInterpreter.parseJsonResponse("""{"type":"unmute"}"""))
    assertEquals(
      Command.CloseAllApps,
      GeminiCommandInterpreter.parseJsonResponse("""{"type":"close_all_apps"}""")
    )
    assertEquals(
      Command.CloseApp,
      GeminiCommandInterpreter.parseJsonResponse("""{"type":"close_app"}""")
    )
  }
}
