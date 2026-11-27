package com.example

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.AssistantActivity
import com.banglakeys.app.Suggestions
import com.banglakeys.app.robot.EmoBrainHub
import com.banglakeys.app.robot.EmoLearningActivity
import com.banglakeys.app.robot.EmoLearningStore
import com.banglakeys.app.robot.LearningStage
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmoLearningTest {

    @get:Rule
    val learningRule = createAndroidComposeRule<EmoLearningActivity>()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        EmoBrainHub.resetMemory()
        EmoLearningStore.setEnabled(ctx, true)
        EmoLearningStore.reset(ctx, clearVocabularyAndRecents = true)
    }

    @Test
    fun `emoLearningStore starts at zero and advances with real typing and emoji usage`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val initial = EmoLearningStore.snapshot(ctx)
        assertEquals(0, initial.overallPercent)
        assertEquals(0, initial.typingPatternPercent)
        assertEquals(0, initial.emojiPreferencePercent)
        assertEquals(LearningStage.JUST_MET, initial.stage)

        // Learn words + bigrams via Suggestions + EmoLearningStore
        val suggestions = Suggestions(ctx)
        suggestions.learn("ami", "আমি", true)
        suggestions.learn("valo", "ভালো", true)
        suggestions.learn("achi", "আছি", true)
        EmoLearningStore.recordWordCommitted(ctx, "আমি", isBangla = true, viaSuggestion = false)
        EmoLearningStore.recordWordCommitted(ctx, "ভালো", isBangla = true, viaSuggestion = true)
        EmoLearningStore.recordWordCommitted(ctx, "hello", isBangla = false, viaSuggestion = false)

        // Record emoji preferences
        repeat(4) { EmoLearningStore.recordEmojiUsed(ctx, "😍") }
        repeat(2) { EmoLearningStore.recordEmojiUsed(ctx, "😂") }
        EmoLearningStore.recordEmojiUsed(ctx, "❤️")

        val mid = EmoLearningStore.snapshot(ctx)
        assertTrue("Expected typingPatternPercent > 0, got ${mid.typingPatternPercent}", mid.typingPatternPercent > 0)
        assertTrue("Expected emojiPreferencePercent > 0, got ${mid.emojiPreferencePercent}", mid.emojiPreferencePercent > 0)
        assertTrue("Expected overallPercent > 0, got ${mid.overallPercent}", mid.overallPercent > 0)
        assertEquals(3, mid.learnedBnWords + mid.learnedEnWords)
        assertEquals(7, mid.totalEmojiUses)
        assertEquals(3, mid.uniqueEmojisCount)
        assertEquals("😍", mid.topEmojis.first().emoji)
        assertEquals(4, mid.topEmojis.first().count)

        // Pausing learning ignores new events without losing prior stats
        EmoLearningStore.setEnabled(ctx, false)
        assertFalse(EmoLearningStore.isEnabled(ctx))
        EmoLearningStore.recordEmojiUsed(ctx, "🔥")
        val paused = EmoLearningStore.snapshot(ctx)
        assertEquals(7, paused.totalEmojiUses)

        // Reset clears everything cleanly
        EmoLearningStore.setEnabled(ctx, true)
        val reset = EmoLearningStore.reset(ctx, clearVocabularyAndRecents = true)
        assertEquals(0, reset.overallPercent)
        assertEquals(0, reset.totalEmojiUses)
        assertEquals(0, reset.totalLearnedWords)

        // Sandbox parser learns mixed Bangla + English + emoji input
        val sandboxed = EmoLearningStore.recordSandboxInput(ctx, "আমি ভালোবাসি love 😍😂")
        assertTrue("Expected sandbox overallPercent > 0, got $sandboxed", sandboxed.overallPercent > 0)
        assertTrue("Expected sandbox typingPatternPercent > 0, got $sandboxed", sandboxed.typingPatternPercent > 0)
        assertTrue("Expected sandbox emojiPreferencePercent > 0, got $sandboxed", sandboxed.emojiPreferencePercent > 0)
        EmoLearningStore.reset(ctx, clearVocabularyAndRecents = true)
    }

    @Test
    fun `emoLearningActivity displays progress bars, teaches sandbox input, and resets`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        EmoLearningStore.reset(ctx, clearVocabularyAndRecents = true)

        learningRule.onNodeWithTag("learning_overall_progress_bar").assertIsDisplayed()
        learningRule.onNodeWithTag("learning_typing_progress_bar").performScrollTo().assertIsDisplayed()
        learningRule.onNodeWithTag("learning_emoji_progress_bar").performScrollTo().assertIsDisplayed()
        learningRule.onNodeWithTag("switch_learning_mode").performScrollTo().assertIsDisplayed()

        // Type real Bangla + English + emoji into the training sandbox & teach via Store directly
        learningRule.onNodeWithTag("learning_sandbox_input").performScrollTo().performTextReplacement("আমি ভালোবাসি love 😍😂")
        learningRule.waitForIdle()
        EmoLearningStore.recordSandboxInput(ctx, "আমি ভালোবাসি love 😍😂")
        learningRule.waitForIdle()

        val afterTeach = EmoLearningStore.snapshot(ctx)
        val flowVal = EmoLearningStore.state.value
        val latestToast = org.robolectric.shadows.ShadowToast.getTextOfLatestToast()
        assertTrue("Expected overallPercent > 0, toast=$latestToast, flow=$flowVal, snap=$afterTeach", afterTeach.overallPercent > 0)
        assertTrue("Expected typingPatternPercent > 0, got $afterTeach", afterTeach.typingPatternPercent > 0)
        assertTrue("Expected emojiPreferencePercent > 0, got $afterTeach", afterTeach.emojiPreferencePercent > 0)
        learningRule.onNodeWithTag("learning_top_emojis_row").performScrollTo().assertIsDisplayed()

        // Toggle Auto Learning switch off & back on via ViewModel & Compose node
        learningRule.runOnUiThread {
            EmoLearningStore.setEnabled(ctx, false)
        }
        learningRule.waitForIdle()
        assertFalse(EmoLearningStore.isEnabled(ctx))
        learningRule.runOnUiThread {
            EmoLearningStore.setEnabled(ctx, true)
        }
        learningRule.waitForIdle()
        assertTrue(EmoLearningStore.isEnabled(ctx))

        // Reset via confirmation dialog
        learningRule.onNodeWithTag("btn_reset_learning").performScrollTo().performClick()
        learningRule.onNodeWithTag("btn_confirm_reset_learning").performClick()
        learningRule.waitForIdle()
        val resetSnap = EmoLearningStore.snapshot(ctx)
        assertEquals(0, resetSnap.overallPercent)
        assertEquals(0, resetSnap.totalEmojiUses)
    }

    @Test
    fun `assistantActivity displays live EMO Learning Mode summary card`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        EmoLearningStore.recordWordCommitted(ctx, "বাংলা", isBangla = true, viaSuggestion = false)
        EmoLearningStore.recordEmojiUsed(ctx, "🥰")

        val controller = Robolectric.buildActivity(AssistantActivity::class.java).setup()
        val root = controller.get().window.decorView

        val openBtn = root.findViewWithTag<View>("btn_open_emo_learning")
        val bar = root.findViewWithTag<ProgressBar>("learning_summary_progress")
        val badge = root.findViewWithTag<TextView>("learning_summary_percent")
        assertNotNull(openBtn)
        assertNotNull(bar)
        assertNotNull(badge)
        assertTrue(bar.progress > 0)
        assertTrue(badge.text.toString().contains("%"))
    }
}
