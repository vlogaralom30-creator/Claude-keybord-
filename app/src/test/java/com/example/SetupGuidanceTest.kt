package com.example

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.SecretConfig
import com.banglakeys.app.SecretDecodeBannerView
import com.banglakeys.app.SetupGuidance
import com.banglakeys.app.Translator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SetupGuidanceTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("secret_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("secret_vault_kp", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("bk", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun `secretGuide prompts setup when no contacts exist and clears once contact added`() {
    val guide = SetupGuidance.secretGuide(context)
    assertNotNull(guide)
    assertEquals(SetupGuidance.GuideAction.OPEN_SECRET_SETTINGS, guide!!.action)
    assertTrue(guide.steps.contains("Contact"))
    assertTrue(guide.steps.contains("Passphrase"))
    assertNotNull(guide.actionLabel)

    SecretConfig.setContact(context, "রহিম", "secret-passphrase-123")
    assertNull(SetupGuidance.secretGuide(context))
  }

  @Test
  fun `assistantGuide reports missing accessibility and mic setup`() {
    val guide = SetupGuidance.assistantGuide(context)
    assertNotNull(guide)
    assertTrue(guide!!.title.contains("EMO"))
    assertTrue(!guide.title.contains("Voice Assistant"))
    assertEquals(SetupGuidance.GuideAction.OPEN_ASSISTANT_SETUP, guide.action)
    assertTrue(guide.steps.contains("Accessibility"))
    assertTrue(guide.steps.contains("Microphone"))
    assertNotNull(guide.actionLabel)
  }

  @Test
  fun `voice and translate guides explain missing permissions and settings`() {
    val reqPerm = SetupGuidance.voicePermissionGuide(permanentlyDenied = false)
    assertEquals(SetupGuidance.GuideAction.REQUEST_VOICE_PERMISSION, reqPerm.action)
    assertNotNull(reqPerm.actionLabel)

    val blockedPerm = SetupGuidance.voicePermissionGuide(permanentlyDenied = true)
    assertEquals(SetupGuidance.GuideAction.OPEN_APP_SETTINGS, blockedPerm.action)
    assertTrue(blockedPerm.steps.contains("Microphone"))

    val offlineVoice = SetupGuidance.voiceRuntimeErrorGuide("ইন্টারনেট সংযোগ নেই")
    assertNotNull(offlineVoice)
    assertEquals(SetupGuidance.GuideAction.OPEN_WIFI_SETTINGS, offlineVoice!!.action)

    val emptyTranslate = SetupGuidance.translateEmptyGuide()
    assertEquals(SetupGuidance.GuideAction.NONE, emptyTranslate.action)
    assertTrue(emptyTranslate.steps.contains("⇄"))

    val offlineTranslate = SetupGuidance.translateFailGuide(
      Translator.Outcome.Fail(Translator.Reason.OFFLINE)
    )
    assertEquals(SetupGuidance.GuideAction.OPEN_WIFI_SETTINGS, offlineTranslate.action)
    assertTrue(offlineTranslate.steps.contains("Wi-Fi"))
  }

  @Test
  fun `banner showGuide renders steps and triggers listener action on click`() {
    val banner = SecretDecodeBannerView(context)
    var triggeredAction: SetupGuidance.GuideAction? = null
    banner.listener = object : SecretDecodeBannerView.Listener {
      override fun onReplyClicked(senderName: String) {}
      override fun onCopyPlainClicked(plainText: String) {}
      override fun onOpenVaultClicked() {}
      override fun onGuideActionClicked(action: SetupGuidance.GuideAction) {
        triggeredAction = action
      }
    }

    val guide = SetupGuidance.secretGuide(context)!!
    banner.showGuide(guide)

    assertEquals(View.VISIBLE, banner.visibility)
    assertTrue(banner.currentState() is SecretDecodeBannerView.BannerState.SetupGuide)

    val titleView = banner.findViewWithTag<TextView>("secret_banner_sender")
    val stepsView = banner.findViewWithTag<TextView>("secret_banner_text")
    val actionBtn = banner.findViewWithTag<TextView>("secret_banner_guide_action_btn")
    val replyBtn = banner.findViewWithTag<TextView>("secret_banner_reply_btn")

    assertEquals(guide.title, titleView.text.toString())
    assertEquals(guide.steps, stepsView.text.toString())
    assertEquals(View.VISIBLE, actionBtn.visibility)
    assertEquals(guide.actionLabel, actionBtn.text.toString())
    assertEquals(View.GONE, replyBtn.visibility)

    actionBtn.performClick()
    assertEquals(SetupGuidance.GuideAction.OPEN_SECRET_SETTINGS, triggeredAction)
    assertEquals(View.GONE, banner.visibility)
  }
}
