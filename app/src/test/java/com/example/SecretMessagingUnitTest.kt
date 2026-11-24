package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.GlyphAlphabet
import com.banglakeys.app.GlyphStyle
import com.banglakeys.app.KeyboardView
import com.banglakeys.app.Layouts
import com.banglakeys.app.NotepadClipboardPanelView
import com.banglakeys.app.SecretBannerStateHolder
import com.banglakeys.app.SecretCipher
import com.banglakeys.app.SecretClipboardDetector
import com.banglakeys.app.SecretClipboardMonitorService
import com.banglakeys.app.SecretConfig
import com.banglakeys.app.SecretDecodeBannerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecretMessagingUnitTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("secret_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("secret_vault_kp", Context.MODE_PRIVATE).edit().clear().commit()
    SecretBannerStateHolder.resetForTest()
  }

  @Test
  fun `secret clipboard detector auto-decodes cipher, deduplicates, ignores outgoing, and handles unknown keys`() {
    SecretConfig.setContact(context, "রহিম", "rahim-secret-key")
    var nowMs = 1_000L
    val directExecutor = Executor { it.run() }
    val detector = SecretClipboardDetector(
      context = context,
      workerExecutor = directExecutor,
      mainExecutor = directExecutor,
      clockMs = { nowMs },
    )

    val glyph = GlyphAlphabet.encode(
      SecretCipher.encrypt("আজ সন্ধ্যায় দেখা হবে", "rahim-secret-key"),
      GlyphStyle.RUNIC
    )
    val results = ArrayList<SecretClipboardDetector.DetectionResult>()

    // Non-cipher plain text is ignored completely
    detector.inspect("সাধারণ টেক্সট") { results.add(it) }
    assertTrue(results.isEmpty())

    // Matching cipher decrypts and caches incoming message
    detector.inspect(glyph) { results.add(it) }
    assertEquals(1, results.size)
    val decoded = results.first() as SecretClipboardDetector.DetectionResult.Decoded
    assertEquals("রহিম", decoded.senderName)
    assertEquals("আজ সন্ধ্যায় দেখা হবে", decoded.plainText)
    assertEquals(GlyphStyle.RUNIC, decoded.style)
    val cache = SecretConfig.getSecretCacheEntries(context)
    assertEquals(1, cache.size)
    assertFalse(cache.first().isOutgoing)

    // Duplicate clipboard check without force is skipped
    detector.inspect(glyph) { results.add(it) }
    assertEquals(1, results.size)

    // Outgoing cipher is suppressed within the 10s ignore window
    val outgoing = GlyphAlphabet.encode(
      SecretCipher.encrypt("নিজের মেসেজ", "rahim-secret-key"),
      GlyphStyle.SHAPES
    )
    detector.ignoreOutgoingCipher(outgoing)
    detector.inspect(outgoing) { results.add(it) }
    assertEquals(1, results.size)

    // After ignore window expires, inspecting outgoing with force=true still decodes if needed
    nowMs += 11_000L
    detector.resetDeduplication()
    detector.inspect(outgoing) { results.add(it) }
    assertEquals(2, results.size)
    assertTrue(results[1] is SecretClipboardDetector.DetectionResult.Decoded)

    // Fresh cipher with unknown passphrase yields Unrecognized
    val unknown = GlyphAlphabet.encode(
      SecretCipher.encrypt("অজানা বার্তা", "wrong-key-999"),
      GlyphStyle.DOTS
    )
    detector.inspect(unknown) { results.add(it) }
    assertEquals(3, results.size)
    assertTrue(results[2] is SecretClipboardDetector.DetectionResult.Unrecognized)
  }

  @Test
  fun `secret decode banner shows decrypted message, dispatches reply and dismisses after 15 seconds`() {
    val banner = SecretDecodeBannerView(context)
    var repliedTo: String? = null
    var copiedPlain: String? = null
    var vaultOpenedCount = 0
    var dismissedCount = 0

    banner.listener = object : SecretDecodeBannerView.Listener {
      override fun onReplyClicked(senderName: String) { repliedTo = senderName }
      override fun onCopyPlainClicked(plainText: String) { copiedPlain = plainText }
      override fun onOpenVaultClicked() { vaultOpenedCount++ }
      override fun onDismissed() { dismissedCount++ }
    }

    assertEquals(View.GONE, banner.visibility)
    banner.showDecoded(senderName = "রহিম", plainText = "গোপন কোড ঠিক আছে", cipherText = "glyph")
    assertEquals(View.VISIBLE, banner.visibility)
    assertEquals("গোপন কোড ঠিক আছে", banner.findViewWithTag<TextView>("secret_banner_text").text.toString())
    assertTrue(banner.findViewWithTag<TextView>("secret_banner_sender").text.toString().contains("রহিম"))

    // Copy action delivers plaintext
    banner.findViewWithTag<View>("secret_banner_copy_btn").performClick()
    assertEquals("গোপন কোড ঠিক আছে", copiedPlain)

    // Reply action delivers sender name and hides banner
    banner.findViewWithTag<View>("secret_banner_reply_btn").performClick()
    assertEquals("রহিম", repliedTo)
    assertEquals(View.GONE, banner.visibility)
    assertTrue(banner.currentState() is SecretDecodeBannerView.BannerState.Hidden)

    // Close button immediately dismisses and clears displayed text
    banner.showDecoded(senderName = "সালমা", plainText = "গোপন বার্তা", cipherText = "glyph1b")
    assertEquals(View.VISIBLE, banner.visibility)
    banner.findViewWithTag<View>("secret_banner_close_btn").performClick()
    assertEquals(View.GONE, banner.visibility)
    assertEquals(1, dismissedCount)
    assertEquals("", banner.findViewWithTag<TextView>("secret_banner_text").text.toString())

    // Unrecognized cipher exposes Vault action
    banner.showUnrecognized("glyph_unknown")
    assertEquals(View.VISIBLE, banner.visibility)
    assertEquals(View.VISIBLE, banner.findViewWithTag<View>("secret_banner_vault_btn").visibility)
    banner.findViewWithTag<View>("secret_banner_vault_btn").performClick()
    assertEquals(1, vaultOpenedCount)
    assertEquals(View.GONE, banner.visibility)

    // 15-second auto-hide clears text & invokes onDismissed
    banner.showDecoded(senderName = "করিম", plainText = "১৫ সেকেন্ড পর মুছে যাবে", cipherText = "glyph2")
    assertEquals(View.VISIBLE, banner.visibility)
    val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
    mainLooper.idleFor(Duration.ofMillis(SecretDecodeBannerView.DEFAULT_AUTO_HIDE_MS))
    assertEquals(View.GONE, banner.visibility)
    assertEquals(2, dismissedCount)
    assertEquals("", banner.findViewWithTag<TextView>("secret_banner_text").text.toString())
  }

  @Test
  fun `keyboardView secret mode strip renders recipient cycle and exit controls and dispatches taps`() {
    val kv = KeyboardView(context)
    kv.setLayout(Layouts.phonetic(numberRow = false, bnDigits = true), "বাংলা", 1f)
    kv.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST)
    )
    kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)

    var contactCycleCount = 0
    var cancelCount = 0
    var secretToggleCount = 0

    kv.listener = object : KeyboardView.Listener {
      override fun onText(text: String) {}
      override fun onDelete() {}
      override fun onEnter() {}
      override fun onSpace() {}
      override fun onSpaceLongPress() {}
      override fun onSwitchLang() {}
      override fun onSymbols(mode: Int) {}
      override fun onComposingTap() {}
      override fun onFeedback() {}
      override fun onSuggestionTap(word: String) {}
      override fun onSnippet(text: String) {}
      override fun onNotepad() {}
      override fun onTranslate() {}
      override fun onVoice() {}
      override fun onAssistant() {}
      override fun onSecretToggle() { secretToggleCount++ }
      override fun onSecretContactCycle() { contactCycleCount++ }
      override fun onSecretCancel() { cancelCount++ }
    }

    fun tap(x: Float, y: Float) {
      val now = SystemClock.uptimeMillis()
      val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
      val up = MotionEvent.obtain(now, now + 25, MotionEvent.ACTION_UP, x, y, 0)
      kv.onTouchEvent(down)
      kv.onTouchEvent(up)
      down.recycle()
      up.recycle()
    }

    // Idle mode: Lock quick action dispatches onSecretToggle
    val secretActionRect = kv.stripQuickActionRects()[KeyboardView.QuickAction.SECRET]
    assertNotNull(secretActionRect)
    tap(secretActionRect!!.centerX(), secretActionRect.centerY())
    assertEquals(1, secretToggleCount)

    // Secret Compose Header (empty buffer): Contact cycle pill + Exit pill
    kv.setSecretMode(true, "রহিম")
    val contactRect = kv.stripSecretContactRect()
    val exitRect = kv.stripSecretCancelRect()
    assertFalse(contactRect.isEmpty)
    assertFalse(exitRect.isEmpty)

    tap(contactRect.centerX(), contactRect.centerY())
    tap(exitRect.centerX(), exitRect.centerY())
    assertEquals(1, contactCycleCount)
    assertEquals(1, cancelCount)
  }

  @Test
  fun `notepad clipboard panel renders decode pill for secret cipher card and dispatches callback`() {
    val panel = NotepadClipboardPanelView(context)
    var decodedTarget: String? = null
    panel.listener = object : NotepadClipboardPanelView.Listener {
      override fun onBackToKeyboard() {}
      override fun onPasteItem(text: String) {}
      override fun onAddNewNote() {}
      override fun onPinRecent(text: String) {}
      override fun onUnpinNote(index: Int, text: String) {}
      override fun onEditPinned(index: Int, text: String) {}
      override fun onEditRecent(text: String) {}
      override fun onRemovePinned(index: Int, text: String) {}
      override fun onRemoveRecent(text: String) {}
      override fun onClearRecent() {}
      override fun onDecodeCipher(text: String) { decodedTarget = text }
    }

    val glyph = GlyphAlphabet.encode(SecretCipher.encrypt("secret note", "key-123"), GlyphStyle.DOTS)
    panel.setData(pinned = emptyList(), recent = listOf(glyph, "normal text"))

    val decodeBtn = panel.findViewWithTag<View>("panel_decode_recent_0")
    assertNotNull(decodeBtn)
    assertNull(panel.findViewWithTag<View>("panel_decode_recent_1"))
    decodeBtn.performClick()
    assertEquals(glyph, decodedTarget)
  }

  @Test
  fun `background clipboard monitor service detects encrypted clipboard copy and triggers banner state`() {
    SecretConfig.setContact(context, "তানিয়া", "tania-secret-pass")
    SecretConfig.setClipboardMonitorEnabled(context, true)
    val directExecutor = Executor { it.run() }

    val controller = Robolectric.buildService(SecretClipboardMonitorService::class.java)
      .create()
    val service = controller.get()
    service.setDetectorForTest(
      SecretClipboardDetector(
        context = context,
        workerExecutor = directExecutor,
        mainExecutor = directExecutor,
      )
    )
    controller.startCommand(0, 0)
    assertTrue(SecretClipboardMonitorService.running.value)

    // Simulate external app copying encrypted glyph string into system clipboard
    val cipher = GlyphAlphabet.encode(
      SecretCipher.encrypt("সার্ভিস দিয়ে ডিকোড হলো", "tania-secret-pass"),
      GlyphStyle.RUNIC
    )
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("secret", cipher))
    Shadows.shadowOf(Looper.getMainLooper()).idle()

    val event = SecretBannerStateHolder.events.value
    assertTrue(event is SecretBannerStateHolder.BannerEvent.Decoded)
    val decoded = event as SecretBannerStateHolder.BannerEvent.Decoded
    assertEquals("তানিয়া", decoded.senderName)
    assertEquals("সার্ভিস দিয়ে ডিকোড হলো", decoded.plainText)

    // Active before expiry, hidden after 15s expiry
    assertTrue(SecretBannerStateHolder.activeEvent(decoded.expiresAtMs - 1_000L) is SecretBannerStateHolder.BannerEvent.Decoded)
    assertTrue(SecretBannerStateHolder.activeEvent(decoded.expiresAtMs + 1L) is SecretBannerStateHolder.BannerEvent.Hidden)

    // Stopping service cleans up running state
    controller.destroy()
    assertFalse(SecretClipboardMonitorService.running.value)
  }
}
