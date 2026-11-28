package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.robot.EmoBrainHub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmoAppOpenScreenTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Before
  fun setUp() {
    EmoBrainHub.resetMemory()
  }

  @Test
  fun `emo launcher icon resources and branding load cleanly`() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("EMO Keys", ctx.getString(R.string.app_name))
    assertEquals("EMO Keys", ctx.getString(R.string.ime_name))
    assertTrue(ctx.getString(R.string.hero_subtitle).contains("EMO"))

    val drawables = listOf(
      R.drawable.emo_keys_icon,
      R.drawable.ic_launcher_foreground,
      R.drawable.ic_launcher_background,
      R.mipmap.ic_launcher,
      R.mipmap.ic_launcher_round,
    )
    for (resId in drawables) {
      val d = ContextCompat.getDrawable(ctx, resId)
      assertNotNull("Drawable $resId must load", d)
      d!!.setBounds(0, 0, 108, 108)
      val bmp = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
      d.draw(Canvas(bmp))
    }
  }

  @Test
  fun `mainActivity shows EMO app open screen and transitions on enter tap`() {
    composeRule.onNodeWithTag("emo_open_screen").assertIsDisplayed()
    composeRule.onNodeWithTag("open_screen_hd_eyes").assertIsDisplayed()
    composeRule.onNodeWithTag("open_screen_enter_button").assertIsDisplayed().performClick()
    composeRule.mainClock.advanceTimeBy(400L)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("ime_status_badge").assertIsDisplayed()
    composeRule.onNodeWithTag("open_assistant_button").performScrollTo().assertIsDisplayed()
  }
}
