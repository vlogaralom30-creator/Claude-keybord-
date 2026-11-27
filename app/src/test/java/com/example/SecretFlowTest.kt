package com.example

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.GlyphAlphabet
import com.banglakeys.app.GlyphStyle
import com.banglakeys.app.KeyType
import com.banglakeys.app.KeyboardView
import com.banglakeys.app.Layouts
import com.banglakeys.app.LocalAesVault
import com.banglakeys.app.NotepadClipboardPanelView
import com.banglakeys.app.SecretCipher
import com.banglakeys.app.SecretClipboardDetector
import com.banglakeys.app.SecretComposeActivity
import com.banglakeys.app.SecretConfig
import com.banglakeys.app.SecretDecodeBannerView
import com.banglakeys.app.SecretInvite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecretFlowTest {

  private lateinit var context: Context

  @get:Rule
  val secretActivityRule = createAndroidComposeRule<SecretComposeActivity>()

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("secret_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("secret_vault_kp", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `invite round trip keeps passphrase exactly`() {
    val pass = SecretInvite.generatePassphrase()
    assertEquals(24, pass.length)
    val text = SecretInvite.shareText(pass)
    assertEquals(pass, SecretInvite.parse("hello\n$text\nbye"))
    assertEquals("আমার গোপন 123!", SecretInvite.parse(SecretInvite.shareText("আমার গোপন 123!")))
  }

  @Test
  fun `invite parse rejects garbage`() {
    assertNull(SecretInvite.parse("just a normal message"))
    assertNull(SecretInvite.parse("BKEY1:"))
  }

  @Test
  fun `fingerprint is stable and differs per passphrase`() {
    assertEquals(SecretInvite.fingerprint("abc"), SecretInvite.fingerprint("abc"))
    assertNotEquals(SecretInvite.fingerprint("abc"), SecretInvite.fingerprint("abd"))
  }

  @Test
  fun `encrypt then glyph then decode works for all styles and wrong key fails`() {
    val pass = SecretInvite.generatePassphrase()
    for (style in GlyphStyle.entries) {
      val glyph = GlyphAlphabet.encode(SecretCipher.encrypt("আমি আসছি 😀", pass), style)
      val bytes = GlyphAlphabet.decodeAny(glyph)
      assertNotNull(bytes)
      assertEquals("আমি আসছি 😀", SecretCipher.decryptWithAny(bytes!!, listOf("wrong", pass)))
      assertNull(SecretCipher.decryptWithAny(bytes, listOf("wrong")))
      assertTrue(GlyphAlphabet.looksLikeGlyphText(glyph))
      assertEquals(style, GlyphAlphabet.detectStyle(glyph))
    }
  }

  @Test
  fun `decryptWithMatchingContact identifies exact sender contact`() {
    val contacts = linkedMapOf(
      "রহিম" to "alpha-key-1111",
      "করিম" to "beta-key-2222",
    )
    val cipherBytes = SecretCipher.encrypt("জরুরি মিটিং", "beta-key-2222")
    val match = SecretCipher.decryptWithMatchingContact(cipherBytes, contacts)
    assertNotNull(match)
    assertEquals("করিম", match!!.first)
    assertEquals("জরুরি মিটিং", match.second)
  }

  @Test
  fun `local aes vault encrypts with unique iv and rejects tampered ciphertext`() {
    val secret = "অতি গোপন বার্তা 🔐 1234"
    val enc1 = LocalAesVault.encrypt(context, secret)
    val enc2 = LocalAesVault.encrypt(context, secret)

    assertTrue(LocalAesVault.isEncryptedPayload(enc1))
    assertTrue(LocalAesVault.isEncryptedPayload(enc2))
    assertNotEquals(enc1, enc2)
    assertFalse(enc1.contains(secret))

    assertEquals(secret, LocalAesVault.decrypt(context, enc1))
    assertEquals(secret, LocalAesVault.decrypt(context, enc2))

    // Tamper payload
    val corrupted = enc1.dropLast(4) + "AAAA"
    assertNull(LocalAesVault.decrypt(context, corrupted))
  }

  @Test
  fun `secret config encrypts contacts draft and cache in AES vault with no plaintext in prefs`() {
    val rawSecretPass = "SuperSecretPass-9988"
    val rawPlainMessage = "গোপন কথাটা আজই পাঠাও"
    val rawDraft = "অর্ধেক লেখা খসড়া"
    val glyph = GlyphAlphabet.encode(SecretCipher.encrypt(rawPlainMessage, rawSecretPass), GlyphStyle.DOTS)

    SecretConfig.setContact(context, "বন্ধু", rawSecretPass)
    SecretConfig.setActiveContact(context, "বন্ধু")
    SecretConfig.saveDraft(context, rawDraft)
    SecretConfig.saveSecretCacheEntry(
      ctx = context,
      contactName = "বন্ধু",
      plainText = rawPlainMessage,
      glyphText = glyph,
      style = GlyphStyle.DOTS,
      isOutgoing = true,
    )

    // Verify logical reads succeed
    assertEquals(rawSecretPass, SecretConfig.getContacts(context)["বন্ধু"])
    assertEquals("বন্ধু", SecretConfig.getActiveContact(context))
    assertEquals(rawDraft, SecretConfig.getDraft(context))
    val cached = SecretConfig.getSecretCacheEntries(context)
    assertEquals(1, cached.size)
    assertEquals(rawPlainMessage, cached.first().plainText)
    assertEquals("বন্ধু", cached.first().contactName)
    assertTrue(SecretConfig.isStorageAesSecured(context))

    // Verify zero plaintext leakage inside SharedPreferences raw XML/map
    val rawPrefs = context.getSharedPreferences("secret_prefs", Context.MODE_PRIVATE).all
    val dumped = rawPrefs.entries.joinToString("\n") { "${it.key}=${it.value}" }
    assertFalse("Passphrase must not appear in plaintext", dumped.contains(rawSecretPass))
    assertFalse("Message must not appear in plaintext", dumped.contains(rawPlainMessage))
    assertFalse("Draft must not appear in plaintext", dumped.contains(rawDraft))
  }

  @Test
  fun `legacy plaintext contacts migrate automatically to AES vault`() {
    val legacyJson = """{"পুরনো বন্ধু":"legacy-pass-42"}"""
    context.getSharedPreferences("secret_prefs", Context.MODE_PRIVATE)
      .edit()
      .putString("contacts_json", legacyJson)
      .putString("active_contact", "পুরনো বন্ধু")
      .commit()

    val migrated = SecretConfig.getContacts(context)
    val active = SecretConfig.getActiveContact(context)

    assertEquals("legacy-pass-42", migrated["পুরনো বন্ধু"])
    assertEquals("পুরনো বন্ধু", active)
    assertTrue(SecretConfig.isStorageAesSecured(context))
    val prefs = context.getSharedPreferences("secret_prefs", Context.MODE_PRIVATE)
    assertFalse(prefs.contains("contacts_json"))
    assertFalse(prefs.contains("active_contact"))
  }

  @Test
  fun `quick action bar includes SECRET key and KeyboardView toggles secret mode`() {
    assertTrue(com.banglakeys.app.KeyboardView.QuickAction.values().any { it.name == "SECRET" })
    assertEquals(10, Layouts.symbols(bnDigits = true, taka = true).first().size)

    val kv = KeyboardView(context)
    kv.setLayout(Layouts.phonetic(numberRow = false, bnDigits = true), "বাংলা", 1f)
    kv.setSecretMode(true, "বন্ধু")
    assertTrue(kv.isSecretMode())
    kv.setSecretMode(false)
    assertFalse(kv.isSecretMode())
  }

  @Test
  fun `secret compose activity launches with AES vault card and style chips`() {
    secretActivityRule.onNodeWithTag("aes_vault_status_card").assertIsDisplayed()
    secretActivityRule.onNodeWithTag("secret_clipboard_service_switch").assertIsDisplayed()
    secretActivityRule.onNodeWithTag("secret_clipboard_service_status").assertIsDisplayed()
    secretActivityRule.onNodeWithTag("secret_autodetect_switch").performScrollTo().assertIsDisplayed()
    secretActivityRule.onNodeWithTag("style_chip_DOTS").performScrollTo().performClick()
    secretActivityRule.waitForIdle()
    assertEquals(GlyphStyle.DOTS, SecretConfig.getStyle(context))
  }
}
