package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.AssistantConfig
import com.banglakeys.app.AssistantPrefs
import com.banglakeys.app.robot.EmoBodyActivity
import com.banglakeys.app.robot.EmoBrainHub
import com.banglakeys.app.robot.FaceId
import com.banglakeys.app.robot.HdEyeRenderer
import com.banglakeys.app.robot.HdEyeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HdEyeAnimationsTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences(AssistantConfig.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        EmoBrainHub.resetMemory()
    }

    @Test
    fun `all 8 HD eye styles render cleanly onto canvas with glow blink and gaze`() {
        assertEquals(8, HdEyeStyle.entries.size)
        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0F18") }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#36D1FF")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        for (style in HdEyeStyle.entries) {
            val bmp = Bitmap.createBitmap(440, 280, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            HdEyeRenderer.draw(
                canvas = canvas,
                style = style,
                cx = 220f,
                cy = 140f,
                plateW = 340f,
                plateH = 200f,
                eyeGap = 145f,
                eyeRadius = 52f,
                blink = 0f,
                gazeNormX = 0.45f,
                gazeNormY = -0.25f,
                scaleX = 0.96f,
                scaleY = 1.04f,
                platePaint = platePaint,
                plateOutline = outlinePaint,
                nowMs = 2_400L,
                glow = true,
            )
            assertTrue(
                "HD eye style $style must draw eye rims/paths",
                shadowOf(canvas).pathPaintHistoryCount >= 2,
            )

            // Also verify mid-blink without glow does not throw
            val blinkCanvas = Canvas(Bitmap.createBitmap(440, 280, Bitmap.Config.ARGB_8888))
            HdEyeRenderer.draw(
                canvas = blinkCanvas,
                style = style,
                cx = 220f,
                cy = 140f,
                plateW = 340f,
                plateH = 200f,
                eyeGap = 145f,
                eyeRadius = 52f,
                blink = 0.72f,
                nowMs = 4_800L,
                glow = false,
            )
            assertTrue(shadowOf(blinkCanvas).pathPaintHistoryCount >= 2)
        }
    }

    @Test
    fun `every FaceId maps cleanly to an HdEyeStyle`() {
        for (face in FaceId.entries) {
            val mapped = HdEyeStyle.fromFace(face)
            assertNotNull(mapped)
        }
    }

    @Test
    fun `emoBodyActivity displays HD eye animation chips and switches between HD and Classic modes`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(AssistantPrefs.emoHdEyesEnabled(ctx))

        val controller = Robolectric.buildActivity(EmoBodyActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.window.decorView

        val autoChip = root.findViewWithTag<View>("btn_hd_eyes_auto")
        val classicChip = root.findViewWithTag<View>("btn_hd_eyes_classic")
        assertNotNull(autoChip)
        assertNotNull(classicChip)

        for (style in HdEyeStyle.entries) {
            val chip = root.findViewWithTag<View>("btn_hd_eye_${style.name.lowercase()}")
            assertNotNull("Missing HD eye chip for $style", chip)
        }

        // Tap Googly HD eye chip
        root.findViewWithTag<View>("btn_hd_eye_googly").performClick()
        assertTrue(AssistantPrefs.emoHdEyesEnabled(ctx))
        assertEquals(FaceId.SWIRL, EmoBrainHub.brain.mood)

        // Tap Fierce HD eye chip
        root.findViewWithTag<View>("btn_hd_eye_fierce").performClick()
        assertEquals(FaceId.ANGRY, EmoBrainHub.brain.mood)

        // Switch to Classic mode
        classicChip.performClick()
        assertFalse(AssistantPrefs.emoHdEyesEnabled(ctx))

        // Switch back to HD Auto mode
        autoChip.performClick()
        assertTrue(AssistantPrefs.emoHdEyesEnabled(ctx))

        controller.pause().stop().destroy()
    }
}
