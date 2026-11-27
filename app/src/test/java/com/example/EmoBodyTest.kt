package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Switch
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.AssistantActivity
import com.banglakeys.app.AssistantConfig
import com.banglakeys.app.AssistantPrefs
import com.banglakeys.app.robot.BodyEffect
import com.banglakeys.app.robot.BodyTiltFilter
import com.banglakeys.app.robot.CompassSpinTracker
import com.banglakeys.app.robot.DropAndShakeDetector
import com.banglakeys.app.robot.EmoBodyActivity
import com.banglakeys.app.robot.EmoBodyState
import com.banglakeys.app.robot.EmoBrainHub
import com.banglakeys.app.robot.EmoParticles
import com.banglakeys.app.robot.EmoSoundSynth
import com.banglakeys.app.robot.EmoTone
import com.banglakeys.app.robot.FaceId
import com.banglakeys.app.robot.RobotBrain
import com.banglakeys.app.robot.RobotEvent
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
class EmoBodyTest {

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences(AssistantConfig.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        EmoBrainHub.resetMemory()
    }

    @Test
    fun `dropAndShakeDetector detects free-fall followed by impact and ignores normal bumps`() {
        val det = DropAndShakeDetector()
        var t = 10_000L

        // Normal resting gravity (~9.8 m/s^2) -> no events
        assertTrue(det.onAccelerometerSample(0f, 0f, 9.81f, t).isEmpty())

        // Mild desk bump (~14 m/s^2) -> no false drop or shake
        t += 50L
        assertTrue(det.onAccelerometerSample(0f, 0f, 14.0f, t).isEmpty())

        // Free-fall (< 2.4 m/s^2) sustained >= 120ms -> FreeFall
        t += 50L
        assertTrue(det.onAccelerometerSample(0.2f, 0.3f, 0.4f, t).isEmpty())
        t += 140L
        val fallOut = det.onAccelerometerSample(0.1f, 0.2f, 0.5f, t)
        assertTrue(fallOut.contains(DropAndShakeDetector.Output.FreeFall))

        // Impact spike (> 18.5 m/s^2) within window -> Dropped
        t += 120L
        val impactOut = det.onAccelerometerSample(2f, 4f, 22f, t)
        assertTrue(impactOut.any { it is DropAndShakeDetector.Output.Dropped })
    }

    @Test
    fun `dropAndShakeDetector escalates repeated shaking to VigorousDizzyShake and detects faceDown`() {
        val det = DropAndShakeDetector()
        var t = 20_000L

        val out1 = det.onAccelerometerSample(19f, 0f, 0f, t)
        assertTrue(out1.contains(DropAndShakeDetector.Output.Shake))

        t += 800L
        val out2 = det.onAccelerometerSample(-19f, 0f, 0f, t)
        assertTrue(out2.contains(DropAndShakeDetector.Output.Shake))

        t += 800L
        val out3 = det.onAccelerometerSample(20f, 0f, 0f, t)
        assertTrue(out3.contains(DropAndShakeDetector.Output.VigorousDizzyShake))

        // Face down sustained >= 900ms
        t += 5_000L
        val faceDownOutputs = mutableListOf<DropAndShakeDetector.Output>()
        repeat(35) {
            faceDownOutputs.addAll(det.onAccelerometerSample(0f, 0f, -9.8f, t))
            t += 50L
        }
        assertTrue(faceDownOutputs.contains(DropAndShakeDetector.Output.FaceDown))
    }

    @Test
    fun `compassSpinTracker handles 360 degree wraparound and detects spin`() {
        val tracker = CompassSpinTracker()
        var t = 1_000L
        assertFalse(tracker.onAzimuth(350f, t))
        t += 200L
        assertFalse(tracker.onAzimuth(20f, t)) // +30 deg across 0
        t += 200L
        assertFalse(tracker.onAzimuth(140f, t)) // +120 deg
        t += 200L
        assertFalse(tracker.onAzimuth(250f, t)) // +110 deg (total 260)
        t += 200L
        assertTrue(tracker.onAzimuth(310f, t)) // +60 deg (total 320 >= 290)

        assertEquals("N", EmoBodyState.cardinalFromAzimuth(355f))
        assertEquals("E", EmoBodyState.cardinalFromAzimuth(90f))
        assertEquals("SW", EmoBodyState.cardinalFromAzimuth(225f))
    }

    @Test
    fun `bodyTiltFilter clamps tilt and normalizes gaze`() {
        val filter = BodyTiltFilter()
        repeat(25) {
            filter.update(-9.8f, 0f, 2f)
        }
        assertTrue(filter.rollDeg > 15f && filter.rollDeg <= 24f)
        assertTrue(filter.gazeX in 0.5f..1.0f)
    }

    @Test
    fun `robotBrain crying lifecycle comforts via Lift or Comforted and auto-soothes after 25s`() {
        var clock = 50_000L
        val brain = RobotBrain(nowMs = { clock })

        brain.handle(RobotEvent.FreeFall)
        assertTrue(brain.isFreeFalling)
        assertEquals(FaceId.SHOCKED, brain.mood)

        clock += 200L
        brain.handle(RobotEvent.Dropped)
        assertFalse(brain.isFreeFalling)
        assertTrue(brain.isCrying)
        assertEquals(FaceId.CRY_RIVER, brain.mood)

        // Comforted stops crying and moves to CALM
        clock += 2_000L
        brain.handle(RobotEvent.Comforted)
        assertFalse(brain.isCrying)
        assertEquals(FaceId.CALM, brain.mood)

        // Drop again -> auto-soothe after 25s tick
        clock += 1_000L
        brain.handle(RobotEvent.Dropped)
        assertTrue(brain.isCrying)
        clock += 25_100L
        brain.tick()
        assertFalse(brain.isCrying)
        assertEquals(FaceId.CALM, brain.mood)

        // FingerprintMatched boosts bond by +10 and clears crying
        clock += 1_000L
        brain.handle(RobotEvent.Dropped)
        val bondBefore = brain.needs.bond
        brain.handle(RobotEvent.FingerprintMatched)
        assertFalse(brain.isCrying)
        assertEquals(FaceId.BEAMING, brain.mood)
        assertEquals((bondBefore + 10).coerceAtMost(100), brain.needs.bond)
    }

    @Test
    fun `emoSoundSynth generates non-empty PCM buffers for every tone`() {
        for (tone in EmoTone.entries) {
            val soft = EmoSoundSynth.synthesize(tone, gain = 0.25f)
            val loud = EmoSoundSynth.synthesize(tone, gain = 0.75f)
            assertTrue("Synth empty for $tone", loud.size > 500)
            assertEquals(loud.size, soft.size)
            assertTrue("Expected audible peak for $tone", loud.maxOf { kotlin.math.abs(it.toInt()) } > 1000)
        }
    }

    @Test
    fun `emoParticles draws tears stars hearts and whisper rings cleanly`() {
        val bmp = Bitmap.createBitmap(400, 260, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        EmoParticles.draw(
            canvas = canvas,
            cx = 200f,
            cy = 130f,
            plateW = 300f,
            plateH = 180f,
            eyeGap = 110f,
            isCrying = true,
            mood = FaceId.SQUEEZE,
            effect = BodyEffect.TEARS,
            effectAgeMs = 400L,
            nowMs = 1_200L,
        )
        val tearsCount = shadowOf(canvas).pathPaintHistoryCount
        assertEquals(4, tearsCount)

        EmoParticles.draw(
            canvas = canvas,
            cx = 200f,
            cy = 130f,
            plateW = 300f,
            plateH = 180f,
            eyeGap = 110f,
            isCrying = false,
            mood = FaceId.DIZZY,
            effect = BodyEffect.STARS,
            effectAgeMs = 300L,
            nowMs = 1_400L,
        )
        assertEquals(8, shadowOf(canvas).pathPaintHistoryCount)

        EmoParticles.draw(
            canvas = canvas,
            cx = 200f,
            cy = 130f,
            plateW = 300f,
            plateH = 180f,
            eyeGap = 110f,
            isCrying = false,
            mood = FaceId.JOY,
            effect = BodyEffect.HEARTS,
            effectAgeMs = 500L,
            nowMs = 1_600L,
        )
        assertEquals(11, shadowOf(canvas).pathPaintHistoryCount)
    }

    @Test
    fun `emoBodyActivity displays telemetry dock and soothe banner when dropped`() {
        val controller = Robolectric.buildActivity(EmoBodyActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.window.decorView

        assertNotNull(root.findViewWithTag<View>("emo_body_back"))
        assertNotNull(root.findViewWithTag<View>("emo_body_visor"))
        assertNotNull(root.findViewWithTag<TextView>("hud_eye"))
        assertNotNull(root.findViewWithTag<TextView>("hud_ear"))
        assertNotNull(root.findViewWithTag<TextView>("hud_compass"))
        assertNotNull(root.findViewWithTag<TextView>("hud_tilt"))
        assertNotNull(root.findViewWithTag<TextView>("hud_battery"))
        assertNotNull(root.findViewWithTag<View>("btn_body_eye_cam"))
        assertNotNull(root.findViewWithTag<View>("btn_body_fingerprint"))
        assertNotNull(root.findViewWithTag<View>("btn_body_voice_ptt"))
        assertNotNull(root.findViewWithTag<View>("btn_body_audio_toggle"))
        assertNotNull(root.findViewWithTag<View>("btn_body_haptic_toggle"))

        val sootheBtn = root.findViewWithTag<View>("btn_soothe_emo")
        assertNotNull(sootheBtn)
        assertEquals(View.GONE, sootheBtn.visibility)

        // Simulate drop -> crying & soothe banner becomes VISIBLE
        EmoBrainHub.handleEvent(RobotEvent.Dropped)
        assertTrue(EmoBrainHub.brain.isCrying)
        assertEquals(View.VISIBLE, sootheBtn.visibility)

        // Click soothe banner -> comforts EMO and hides banner
        sootheBtn.performClick()
        assertFalse(EmoBrainHub.brain.isCrying)
        assertEquals(FaceId.CALM, EmoBrainHub.brain.mood)
        assertEquals(View.GONE, sootheBtn.visibility)

        // Audio toggle updates preference cleanly
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(AssistantPrefs.emoVoiceSfxEnabled(ctx))
        root.findViewWithTag<View>("btn_body_audio_toggle").performClick()
        assertFalse(AssistantPrefs.emoVoiceSfxEnabled(ctx))

        // CompassSpin triggers swirl dizzy mood and updates body effect
        EmoBrainHub.handleEvent(RobotEvent.CompassSpin)
        assertEquals(FaceId.SWIRL, EmoBrainHub.brain.mood)
        assertEquals(BodyEffect.STARS, EmoBrainHub.bodyState.lastEffect)

        controller.pause().stop().destroy()
    }

    @Test
    fun `assistantActivity exposes EMO Body Mode launcher and live body sensor switches`() {
        val controller = Robolectric.buildActivity(AssistantActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.window.decorView

        assertNotNull(root.findViewWithTag<View>("btn_open_emo_body"))
        val sensorSwitch = root.findViewWithTag<Switch>("switch_live_body_sensors")
        assertNotNull(sensorSwitch)
        assertFalse(sensorSwitch.isChecked)

        sensorSwitch.performClick()
        assertTrue(AssistantPrefs.emoBodySensorsInCenter(activity))

        // New body event buttons work in Control Center
        val dropBtn = root.findViewWithTag<View>("event_dropped")
        assertNotNull(dropBtn)
        dropBtn.performClick()
        assertTrue(EmoBrainHub.brain.isCrying)

        val comfortBtn = root.findViewWithTag<View>("event_comforted")
        assertNotNull(comfortBtn)
        comfortBtn.performClick()
        assertFalse(EmoBrainHub.brain.isCrying)
        assertEquals(FaceId.CALM, EmoBrainHub.brain.mood)

        controller.pause().stop().destroy()
    }
}
