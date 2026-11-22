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
    assertEquals(7, phoneticBottom.size)
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
    assertEquals(Command.OpenApp("tiktok"), fuzzyOpen.command)
    assertTrue(fuzzyOpen.fuzzyCorrected)

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
}
