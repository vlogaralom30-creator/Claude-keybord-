package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.AssistantConfig
import com.banglakeys.app.AssistantPrefs
import com.banglakeys.app.robot.BodyEffect
import com.banglakeys.app.robot.EmoBodyActivity
import com.banglakeys.app.robot.EmoBodyState
import com.banglakeys.app.robot.EmoBrainHub
import com.banglakeys.app.robot.FaceId
import com.banglakeys.app.robot.HdEyeStyle
import com.banglakeys.app.robot.RobotBodyMotionEngine
import com.banglakeys.app.robot.RobotBodyMove
import com.banglakeys.app.robot.RobotBodyRenderer
import com.banglakeys.app.robot.RobotBodySkin
import com.banglakeys.app.robot.RobotBrain
import com.banglakeys.app.robot.RobotEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RobotBodyAnimationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences(AssistantConfig.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        EmoBrainHub.resetMemory()
    }

    @Test
    fun `resolveAutoMove maps brain moods and emergency states accurately`() {
        val brain = RobotBrain(nowMs = { 50_000L })
        val neutralBody = EmoBodyState()

        assertEquals(RobotBodyMove.IDLE, RobotBodyMotionEngine.resolveAutoMove(brain, neutralBody))

        brain.handle(RobotEvent.KeywordCute)
        assertEquals(RobotBodyMove.JUMP_WAVE, RobotBodyMotionEngine.resolveAutoMove(brain, neutralBody))

        brain.handle(RobotEvent.Lift)
        assertEquals(RobotBodyMove.HEAD_TILT, RobotBodyMotionEngine.resolveAutoMove(brain, neutralBody))

        brain.handle(RobotEvent.CompassSpin)
        assertEquals(RobotBodyMove.DIZZY_WOBBLE, RobotBodyMotionEngine.resolveAutoMove(brain, neutralBody))

        brain.handle(RobotEvent.FaceDown)
        assertEquals(RobotBodyMove.BELLY_FLOP, RobotBodyMotionEngine.resolveAutoMove(brain, neutralBody))

        brain.handle(RobotEvent.Dropped)
        assertEquals(RobotBodyMove.CRY_SIT, RobotBodyMotionEngine.resolveAutoMove(brain, neutralBody))
    }

    @Test
    fun `samplePose and lerpPose preserve cute body deformation curves`() {
        val flop = RobotBodyMotionEngine.samplePose(RobotBodyMove.BELLY_FLOP, nowMs = 1_200L)
        assertTrue(flop.squashX > 1.15f)
        assertTrue(flop.squashY < 0.72f)
        assertEquals(1f, flop.legsTuckedNorm, 0.01f)

        val tilt = RobotBodyMotionEngine.samplePose(RobotBodyMove.HEAD_TILT, nowMs = 1_500L)
        assertTrue(abs(tilt.bodyTiltDeg) > 10f)

        val jump = RobotBodyMotionEngine.samplePose(RobotBodyMove.JUMP_WAVE, nowMs = 1_370L)
        assertTrue(jump.rightArmRaise > 0.5f)
        assertTrue(jump.waveLinesAlpha > 0.3f)

        val idle = RobotBodyMotionEngine.samplePose(RobotBodyMove.IDLE, nowMs = 1_000L)
        val hop = RobotBodyMotionEngine.samplePose(RobotBodyMove.IDLE, nowMs = 1_200L, tapHopElapsedMs = 200L)
        assertTrue(hop.jumpLiftNorm > idle.jumpLiftNorm)
        assertTrue(hop.waveLinesAlpha > 0.2f)

        val mid = RobotBodyMotionEngine.lerpPose(idle, flop, 0.5f)
        assertTrue(mid.squashX in idle.squashX..flop.squashX)
    }

    @Test
    fun `robotBodyRenderer draws all moves across Lavender 3D and Classic Cyan skins`() {
        for (skin in RobotBodySkin.entries) {
            for (move in RobotBodyMove.entries) {
                val bmp = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val pose = RobotBodyMotionEngine.samplePose(move, nowMs = 2_350L)
                val bounds = RobotBodyRenderer.draw(
                    canvas = canvas,
                    cx = 240f,
                    cy = 240f,
                    maxW = 420f,
                    maxH = 420f,
                    skin = skin,
                    pose = pose,
                    hdEyesEnabled = true,
                    hdStyle = move.previewHdEye,
                    face = move.previewFace,
                    blink = 0f,
                    gazeNormX = 0.2f,
                    gazeNormY = -0.1f,
                    isCrying = move == RobotBodyMove.CRY_SIT,
                    effect = if (move == RobotBodyMove.DIZZY_WOBBLE) BodyEffect.STARS else BodyEffect.NONE,
                    effectAgeMs = 300L,
                    nowMs = 2_350L,
                )
                assertTrue("Bounds width must be positive for $skin / $move", bounds.width() > 80f)
                assertTrue("Bounds height must be positive for $skin / $move", bounds.height() > 80f)
                assertTrue(
                    "Canvas should record drawn paths for $skin / $move",
                    shadowOf(canvas).pathPaintHistoryCount >= 1,
                )

                // Classic face mode inside full body must also draw cleanly
                val classicCanvas = Canvas(Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888))
                val classicBounds = RobotBodyRenderer.draw(
                    canvas = classicCanvas,
                    cx = 240f,
                    cy = 240f,
                    maxW = 420f,
                    maxH = 420f,
                    skin = skin,
                    pose = pose,
                    hdEyesEnabled = false,
                    hdStyle = HdEyeStyle.SPARKLE,
                    face = FaceId.UWU,
                    nowMs = 2_350L,
                )
                assertTrue(classicBounds.width() > 80f)
            }
        }
    }

    @Test
    fun `emoBodyActivity supports switching skins and triggering cute body moves`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(RobotBodySkin.LAVENDER_3D, AssistantPrefs.emoBodySkin(ctx))

        val controller = Robolectric.buildActivity(EmoBodyActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.window.decorView

        val skinBtn = root.findViewWithTag<View>("btn_body_skin_toggle")
        assertNotNull(skinBtn)
        skinBtn.performClick()
        assertEquals(RobotBodySkin.CLASSIC_CYAN, AssistantPrefs.emoBodySkin(ctx))
        skinBtn.performClick()
        assertEquals(RobotBodySkin.LAVENDER_3D, AssistantPrefs.emoBodySkin(ctx))

        assertNotNull(root.findViewWithTag<View>("btn_move_auto"))
        val jumpChip = root.findViewWithTag<View>("btn_move_jump_wave")
        val tiltChip = root.findViewWithTag<View>("btn_move_head_tilt")
        val sleepyChip = root.findViewWithTag<View>("btn_move_belly_flop")
        val walkChip = root.findViewWithTag<View>("btn_move_walk_shuffle")
        val dizzyChip = root.findViewWithTag<View>("btn_move_dizzy_wobble")
        assertNotNull(jumpChip)
        assertNotNull(tiltChip)
        assertNotNull(sleepyChip)
        assertNotNull(walkChip)
        assertNotNull(dizzyChip)

        jumpChip.performClick()
        assertEquals(FaceId.BEAMING, EmoBrainHub.brain.mood)

        tiltChip.performClick()
        assertEquals(FaceId.CURIOUS, EmoBrainHub.brain.mood)

        sleepyChip.performClick()
        assertEquals(FaceId.REST, EmoBrainHub.brain.mood)

        dizzyChip.performClick()
        assertEquals(FaceId.SWIRL, EmoBrainHub.brain.mood)

        root.findViewWithTag<View>("btn_move_auto").performClick()
        assertFalse(EmoBrainHub.brain.isCrying)

        controller.pause().stop().destroy()
    }

    @Test
    fun `app open screen displays full body robot and lets user toggle skin`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        composeRule.onNodeWithTag("open_screen_full_body_robot").assertIsDisplayed()
        composeRule.onNodeWithTag("open_screen_skin_toggle").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(RobotBodySkin.CLASSIC_CYAN, AssistantPrefs.emoBodySkin(ctx))
    }
}
