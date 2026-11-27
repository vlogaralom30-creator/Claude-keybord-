package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.AssistantActivity
import com.banglakeys.app.KeyboardView
import com.banglakeys.app.Layouts
import com.banglakeys.app.ThemeStore
import com.banglakeys.app.Themes
import com.banglakeys.app.robot.BlinkAnimator
import com.banglakeys.app.robot.EmoBrainHub
import com.banglakeys.app.robot.EmoFrameAnimator
import com.banglakeys.app.robot.FaceId
import com.banglakeys.app.robot.FaceRenderer
import com.banglakeys.app.robot.RobotBrain
import com.banglakeys.app.robot.RobotEvent
import com.banglakeys.app.robot.RobotFaceDemoActivity
import com.banglakeys.app.robot.RobotFaceView
import com.banglakeys.app.robot.TypingMoodTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class RobotFaceTest {

  @Before
  fun setUp() {
    EmoBrainHub.resetMemory()
  }

  @Test
  fun `all 32 faces issue properly styled drawPath commands onto canvas with and without glow`() {
    assertEquals(32, FaceId.entries.size)
    assertEquals(12, FaceId.CLASSIC_12.size)
    assertEquals(20, FaceId.EXPRESSIVE_20.size)
    val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#1A1D24")
      style = Paint.Style.FILL
    }
    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#353C4A")
      style = Paint.Style.STROKE
      strokeWidth = 3f
    }

    val expectedStyles = mapOf(
      FaceId.REST to listOf(Paint.Style.STROKE, Paint.Style.STROKE),
      FaceId.CURIOUS to listOf(Paint.Style.FILL, Paint.Style.STROKE, Paint.Style.FILL, Paint.Style.STROKE),
      FaceId.SLY to listOf(Paint.Style.FILL, Paint.Style.STROKE, Paint.Style.FILL),
      FaceId.ANGRY to listOf(Paint.Style.FILL, Paint.Style.STROKE, Paint.Style.FILL, Paint.Style.STROKE),
      FaceId.MEH to listOf(Paint.Style.FILL, Paint.Style.STROKE, Paint.Style.FILL, Paint.Style.STROKE),
      FaceId.NORMAL to listOf(Paint.Style.FILL, Paint.Style.FILL),
      FaceId.SMILE to listOf(Paint.Style.STROKE, Paint.Style.STROKE),
      FaceId.CALM to listOf(Paint.Style.STROKE, Paint.Style.STROKE),
      FaceId.SQUEEZE to listOf(Paint.Style.FILL, Paint.Style.FILL),
      FaceId.DIZZY to listOf(Paint.Style.STROKE, Paint.Style.STROKE),
      FaceId.STARE to listOf(Paint.Style.FILL, Paint.Style.FILL, Paint.Style.STROKE),
      FaceId.JOY to listOf(Paint.Style.STROKE, Paint.Style.STROKE),
    )

    for (face in FaceId.entries) {
      val bmp = Bitmap.createBitmap(400, 260, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#36D1FF")
      }
      FaceRenderer.draw(
        canvas = canvas,
        face = face,
        cx = 200f,
        cy = 130f,
        plateW = 300f,
        plateH = 180f,
        eyeGap = 114f,
        eyeSize = 40f,
        blink = 0f,
        eyePaint = eyePaint,
        platePaint = platePaint,
        plateOutline = outlinePaint,
        glow = false,
      )

      val sc = shadowOf(canvas)
      val expected = expectedStyles[face]
      if (expected != null) {
        assertEquals("Unexpected path count for $face", expected.size, sc.pathPaintHistoryCount)
        for (i in expected.indices) {
          val paintAtDraw = sc.getDrawnPathPaint(i)
          assertEquals(Color.parseColor("#36D1FF"), paintAtDraw.color)
          assertEquals("Face $face path $i style mismatch", expected[i], paintAtDraw.style)
          if (expected[i] == Paint.Style.STROKE) {
            assertTrue("Face $face strokeWidth must be > 0", paintAtDraw.strokeWidth > 0f)
          }
        }
      } else {
        assertTrue("Expressive face $face must issue >= 3 paths", sc.pathPaintHistoryCount >= 3)
      }

      // Glow + gaze + bounce transform renders cleanly for every face without throwing
      val glowCanvas = Canvas(Bitmap.createBitmap(400, 260, Bitmap.Config.ARGB_8888))
      FaceRenderer.draw(
        canvas = glowCanvas,
        face = face,
        cx = 200f,
        cy = 130f,
        plateW = 300f,
        plateH = 180f,
        eyeGap = 114f,
        eyeSize = 40f,
        blink = 0.25f,
        eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#36D1FF") },
        gazeX = 8f,
        gazeY = -4f,
        scaleX = 0.92f,
        scaleY = 1.12f,
        glow = true,
      )
      val minGlowPaths = expected?.size ?: 3
      assertTrue(
        "Glow path count for $face must be >= $minGlowPaths",
        shadowOf(glowCanvas).pathPaintHistoryCount >= minGlowPaths,
      )
    }
  }

  @Test
  fun `blink animator pulses 0 to 1 and returns to 0`() {
    var clock = 10_000L
    val anim = BlinkAnimator(nowMs = { clock })
    assertEquals(0f, anim.tick(), 0.001f)

    clock += 3_000L
    assertEquals(0f, anim.tick(), 0.01f)

    clock += 70L
    assertTrue(anim.tick() > 0.85f)

    clock += 80L
    assertEquals(0f, anim.tick(), 0.001f)
  }

  @Test
  fun `robotBrain handles events, rate limits speech, and respects forced sleep and wake`() {
    var clock = 100_000L
    val brain = RobotBrain(nowMs = { clock })

    assertEquals(FaceId.NORMAL, brain.mood)
    assertEquals(80, brain.needs.energy)
    assertEquals(50, brain.needs.bond)

    // Tap increases bond and shows speech immediately
    brain.handle(RobotEvent.Tap)
    assertEquals(FaceId.SMILE, brain.mood)
    assertEquals(52, brain.needs.bond)
    assertEquals("হাই! 👋", brain.speechText)

    // Within 5s speech rate-limit: mood changes immediately, previous speech stays
    clock += 1_000L
    brain.handle(RobotEvent.KeywordLove)
    assertEquals(FaceId.JOY, brain.mood)
    assertEquals(58, brain.needs.bond)
    assertEquals("হাই! 👋", brain.speechText)

    // After 5s: new speech displays
    clock += 5_000L
    brain.handle(RobotEvent.Shake)
    assertEquals(FaceId.ANGRY, brain.mood)
    assertEquals("আরে আস্তে! 😠", brain.speechText)

    // Forced sleep via FaceDown blocks casual events like Tap, but FaceUp wakes cleanly
    clock += 6_000L
    brain.handle(RobotEvent.FaceDown)
    assertTrue(brain.sleeping)
    assertEquals(FaceId.REST, brain.mood)

    clock += 6_000L
    brain.handle(RobotEvent.Tap)
    assertTrue(brain.sleeping)
    assertEquals(FaceId.REST, brain.mood)

    clock += 6_000L
    brain.handle(RobotEvent.FaceUp)
    assertFalse(brain.sleeping)
    assertEquals(FaceId.NORMAL, brain.mood)
    assertEquals("উঠে গেলাম", brain.speechText)

    // Reset restores initial state
    brain.resetMemory()
    assertEquals(FaceId.NORMAL, brain.mood)
    assertEquals(80, brain.needs.energy)
    assertEquals(50, brain.needs.bond)
    assertEquals(60, brain.needs.funLevel)
    assertNull(brain.speechText)
    assertFalse(brain.sleeping)
  }

  @Test
  fun `robotBrain natural sleep and wake cycle completes without getting stuck`() {
    var clock = 10_000L
    val brain = RobotBrain(nowMs = { clock })

    // Simulate 4 minutes passing in 3-second steps until energy drops below 18 -> falls asleep
    repeat(80) {
      clock += 3_000L
      brain.tick()
    }
    assertTrue("Robot should fall asleep once energy decays below 18", brain.sleeping)
    assertEquals(FaceId.REST, brain.mood)

    // Simulate sleeping -> energy regenerates to >= 75 -> wakes up automatically
    var wakeSteps = 0
    while (brain.sleeping && wakeSteps < 90) {
      clock += 2_000L
      brain.tick()
      wakeSteps++
    }
    assertFalse("Robot should wake up once sleep regenerates energy >= 75", brain.sleeping)
    assertTrue("Expected energy >= 75 upon waking, got ${brain.needs.energy}", brain.needs.energy >= 75)
  }

  @Test
  fun `robotBrain idle picker avoids repeating current face or choosing sleep face`() {
    var clock = 50_000L
    val brain = RobotBrain(nowMs = { clock })
    var prev = brain.mood
    repeat(10) {
      clock += 9_000L
      brain.tick()
      assertNotEquals("Idle picker must not repeat $prev immediately", prev, brain.mood)
      assertNotEquals("Idle picker must not pick sleep face REST", 's', brain.mood.tag)
      prev = brain.mood
    }
  }

  @Test
  fun `robotFaceView draws cleanly and tap increases bond`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val view = RobotFaceView(context)
    view.measure(
      View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, 800, 600)

    val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bmp))

    val initialBond = view.brain.needs.bond
    view.performClick()
    assertEquals(FaceId.SMILE, view.brain.mood)
    assertEquals(initialBond + 2, view.brain.needs.bond)
  }

  @Test
  fun `robotFaceDemoActivity renders face and fake event buttons update brain`() {
    val controller = Robolectric.buildActivity(RobotFaceDemoActivity::class.java).setup()
    val activity = controller.get()
    val root = activity.window.decorView

    val faceView = activity.faceView
    assertNotNull(faceView)

    val shakeBtn = root.findViewWithTag<Button>("event_shake")
    val faceDownBtn = root.findViewWithTag<Button>("event_face_down")
    val faceUpBtn = root.findViewWithTag<Button>("event_face_up")
    val resetBtn = root.findViewWithTag<Button>("btn_reset_memory")

    assertNotNull(shakeBtn)
    assertNotNull(faceDownBtn)
    assertNotNull(faceUpBtn)
    assertNotNull(resetBtn)

    shakeBtn.performClick()
    assertEquals(FaceId.ANGRY, faceView.brain.mood)

    faceDownBtn.performClick()
    assertTrue(faceView.brain.sleeping)
    assertEquals(FaceId.REST, faceView.brain.mood)

    faceUpBtn.performClick()
    assertFalse(faceView.brain.sleeping)
    assertEquals(FaceId.NORMAL, faceView.brain.mood)

    resetBtn.performClick()
    assertEquals(80, faceView.brain.needs.energy)
    assertEquals(50, faceView.brain.needs.bond)
  }

  @Test
  fun `typingMoodTracker detects fast bursts, slow pauses, backspace bursts, and bilingual keywords`() {
    val tracker = TypingMoodTracker()
    var t = 10_000L

    // 8 rapid taps -> TypingFast
    var lastEv: RobotEvent? = null
    repeat(8) {
      lastEv = tracker.onKeyTap(isBackspace = false, nowMs = t)
      t += 120L
    }
    assertEquals(RobotEvent.TypingFast, lastEv)

    // Slow pause (~3.5s) after cooldown -> TypingSlow
    t += 6_100L
    assertEquals(RobotEvent.TypingSlow, tracker.onKeyTap(isBackspace = false, nowMs = t))

    // 4 rapid backspaces -> BackspaceBurst
    var bsEv: RobotEvent? = null
    repeat(4) {
      t += 150L
      bsEv = tracker.onKeyTap(isBackspace = true, nowMs = t)
    }
    assertEquals(RobotEvent.BackspaceBurst, bsEv)

    // Keywords in English, Bangla, Banglish, and emoji
    assertEquals(RobotEvent.KeywordLove, tracker.onWordCommitted("love!"))
    assertEquals(RobotEvent.KeywordLove, tracker.onWordCommitted("ভালোবাসি"))
    assertEquals(RobotEvent.KeywordLove, tracker.onWordCommitted("khushi"))
    assertEquals(RobotEvent.KeywordLove, tracker.onWordCommitted("😍"))
    assertEquals(RobotEvent.KeywordLaugh, tracker.onWordCommitted("haha!"))
    assertEquals(RobotEvent.KeywordLaugh, tracker.onWordCommitted("😂"))
    assertEquals(RobotEvent.KeywordKiss, tracker.onWordCommitted("kiss"))
    assertEquals(RobotEvent.KeywordKiss, tracker.onWordCommitted("😘"))
    assertEquals(RobotEvent.KeywordShock, tracker.onWordCommitted("omg"))
    assertEquals(RobotEvent.KeywordCry, tracker.onWordCommitted("কান্না"))
    assertEquals(RobotEvent.KeywordCute, tracker.onWordCommitted("uwu"))
    assertEquals(RobotEvent.KeywordAnger, tracker.onWordCommitted("angry"))
    assertEquals(RobotEvent.KeywordAnger, tracker.onWordCommitted("রাগ।"))
    assertNull(tracker.onWordCommitted("কবিতা"))
  }

  @Test
  fun `emoFrameAnimator draws backdrop smoothly and respects privacy mode`() {
    var clock = 20_000L
    val animator = EmoFrameAnimator(nowMs = { clock })
    animator.onWordCommitted("love", isPassword = false)
    assertEquals(FaceId.JOY, animator.brain.mood)

    val bmp = Bitmap.createBitmap(1080, 620, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    animator.onKeyTap(0.5f, -0.3f, isBackspace = false)
    clock += 16L
    animator.drawBackdrop(canvas, 540f, 310f, 1080f, 620f)
    assertTrue(shadowOf(canvas).pathPaintHistoryCount > 0)

    // Privacy mode keeps brain untouched on password words and renders calmly
    animator.privacyMode = true
    animator.onWordCommitted("angry", isPassword = true)
    assertEquals(FaceId.JOY, animator.brain.mood)
    animator.onWordCommitted("angry", isPassword = false)
    assertEquals(FaceId.JOY, animator.brain.mood)
  }

  @Test
  fun `keyboardView renders live EMO robot backdrop under translucent glass keys and reacts to typing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("bk", Context.MODE_PRIVATE).edit().clear().commit()

    ThemeStore.setEmoKeyAlpha(context, 10)
    assertEquals(ThemeStore.MIN_EMO_ALPHA, ThemeStore.emoKeyAlpha(context))
    ThemeStore.setEmoKeyAlpha(context, 99)
    assertEquals(ThemeStore.MAX_EMO_ALPHA, ThemeStore.emoKeyAlpha(context))

    ThemeStore.setSelectedId(context, Themes.EMO_ID)
    val kv = KeyboardView(context)
    kv.setLayout(Layouts.phonetic(numberRow = false, bnDigits = true), "অভ্র", 1f)
    kv.reloadTheme()
    assertTrue(kv.isEmoThemeActive())

    kv.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST),
    )
    kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)

    val canvas = Canvas(Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888))
    kv.draw(canvas)
    // Both EMO cyan eyes/halos and translucent glass keys issued draw paths cleanly
    assertTrue(shadowOf(canvas).pathPaintHistoryCount > 0)

    // Word moods react when EMO theme is active and not in password mode
    kv.notifyEmoWord("ভালোবাসি", isPassword = false)
    assertEquals(FaceId.JOY, kv.emoCurrentMood())

    kv.notifyEmoWord("angry", isPassword = true)
    assertEquals(FaceId.JOY, kv.emoCurrentMood())

    kv.setEmoPrivacyMode(true)
    kv.notifyEmoWord("angry", isPassword = false)
    assertEquals(FaceId.JOY, kv.emoCurrentMood())

    kv.setEmoPrivacyMode(false)
    kv.notifyEmoWord("haha", isPassword = false)
    assertEquals(FaceId.GIGGLE, kv.emoCurrentMood())
    kv.notifyEmoWord("😘", isPassword = false)
    assertEquals(FaceId.WINK_KISS, kv.emoCurrentMood())
    kv.notifyEmoSend(isPassword = false)
    assertEquals(FaceId.WINK, kv.emoCurrentMood())
  }

  @Test
  fun `unified EMO Control Center merges 32-face lab, events, and voice controls`() {
    val controller = Robolectric.buildActivity(AssistantActivity::class.java).setup()
    val activity = controller.get()
    val root = activity.window.decorView

    val faceView = activity.faceView
    assertNotNull(faceView)
    assertNotNull(root.findViewWithTag<View>("btn_emo_service_toggle"))
    assertNotNull(root.findViewWithTag<View>("btn_emo_ptt"))
    assertNotNull(root.findViewWithTag<View>("btn_emo_hero_ptt"))
    assertNotNull(root.findViewWithTag<View>("btn_emo_lang"))

    // All 32 expression tiles
    for (face in FaceId.entries) {
      val tile = root.findViewWithTag<View>("face_tile_${face.name.lowercase()}")
      assertNotNull("Missing tile for $face", tile)
    }
    root.findViewWithTag<View>("face_tile_joy").performClick()
    assertEquals(FaceId.JOY, faceView.brain.mood)

    root.findViewWithTag<View>("face_tile_wink_kiss").performClick()
    assertEquals(FaceId.WINK_KISS, faceView.brain.mood)

    root.findViewWithTag<View>("event_keyword_laugh").performClick()
    assertEquals(FaceId.GIGGLE, faceView.brain.mood)

    // Brain event lab + memory reset
    root.findViewWithTag<View>("event_shake").performClick()
    assertEquals(FaceId.ANGRY, faceView.brain.mood)

    root.findViewWithTag<View>("event_face_down").performClick()
    assertTrue(faceView.brain.sleeping)
    assertEquals(FaceId.REST, faceView.brain.mood)

    root.findViewWithTag<View>("event_face_up").performClick()
    assertFalse(faceView.brain.sleeping)
    assertEquals(FaceId.NORMAL, faceView.brain.mood)

    root.findViewWithTag<View>("btn_reset_memory").performClick()
    assertEquals(80, faceView.brain.needs.energy)
    assertEquals(50, faceView.brain.needs.bond)
  }
}
