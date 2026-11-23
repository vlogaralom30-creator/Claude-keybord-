package com.example

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.EmojiCategory
import com.banglakeys.app.EmojiPanelView
import com.banglakeys.app.EmojiRepository
import com.banglakeys.app.KeyboardHostLayout
import com.banglakeys.app.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmojiPanelTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("bk_emoji", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `emoji repository persists recent emojis with dedup cap and skin tone preferences`() {
        val repo = EmojiRepository(context)
        assertFalse(repo.hasRecents())

        for (cat in EmojiCategory.values()) {
            if (cat != EmojiCategory.RECENT) {
                assertTrue("Category ${cat.id} should not be empty", repo.emojisFor(cat).isNotEmpty())
            }
        }

        // Record recents, verify dedup & FIFO most-recent-first & 40 cap
        for (i in 1..45) {
            repo.recordRecent("E$i")
        }
        assertEquals(40, repo.recents().size)
        assertEquals("E45", repo.recents().first())

        repo.recordRecent("E10")
        assertEquals(40, repo.recents().size)
        assertEquals("E10", repo.recents().first())

        val reloaded = EmojiRepository(context)
        assertEquals(repo.recents(), reloaded.recents())

        // Skin tone support & persistence
        assertTrue(reloaded.supportsSkinTone("👍"))
        assertTrue(reloaded.supportsSkinTone("✌️"))
        assertFalse(reloaded.supportsSkinTone("😀"))

        val variants = reloaded.skinToneVariants("👍")
        assertEquals(listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), variants)
        reloaded.setPreferredSkinTone("👍", "👍🏽")

        val third = EmojiRepository(context)
        assertEquals("👍🏽", third.preferredEmoji("👍"))
        assertTrue(third.emojisFor(EmojiCategory.PEOPLE).contains("👍🏽"))
        assertEquals("✌️", EmojiRepository.stripToneModifiers("✌🏽"))

        third.clearRecents()
        assertFalse(third.hasRecents())
    }

    @Test
    fun `deleteLastGrapheme removes full multi codepoint emojis without leaving surrogates`() {
        assertEquals("Hi ", EmojiRepository.deleteLastGrapheme("Hi 😀"))
        assertEquals("Ok", EmojiRepository.deleteLastGrapheme("Ok👍🏽"))
        assertEquals("Flag:", EmojiRepository.deleteLastGrapheme("Flag:🏳️‍🌈"))
        assertEquals("বাং", EmojiRepository.deleteLastGrapheme("বাংলা"))
        assertEquals("", EmojiRepository.deleteLastGrapheme(""))
    }

    @Test
    fun `host keeps exact locked keyboard height when switching to emoji panel and dispatches actions`() {
        val host = KeyboardHostLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val kv = host.keyboardView
        kv.setLayout(Layouts.phonetic(numberRow = false, bnDigits = true), "বাংলা", 1f)

        fun measureHost(): Int {
            host.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.AT_MOST)
            )
            host.layout(0, 0, host.measuredWidth, host.measuredHeight)
            return host.measuredHeight
        }

        val keyboardHeight = measureHost()
        assertTrue(keyboardHeight > 0)

        var emojiToggleFromKey = 0
        kv.listener = object : com.banglakeys.app.KeyboardView.Listener {
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
            override fun onEmojiToggle() { emojiToggleFromKey++ }
        }

        // Verify tapping bottom-row EMOJI key dispatches onEmojiToggle()
        val now = SystemClock.uptimeMillis()
        // Row 4 is near bottom of KeyboardView
        val bottomY = kv.measuredHeight - 20f
        val emojiKeyX = kv.measuredWidth * 0.27f
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, emojiKeyX, bottomY, 0)
        val up = MotionEvent.obtain(now, now + 25, MotionEvent.ACTION_UP, emojiKeyX, bottomY, 0)
        kv.onTouchEvent(down)
        kv.onTouchEvent(up)
        down.recycle()
        up.recycle()
        assertEquals(1, emojiToggleFromKey)

        // Switch to HostMode.EMOJI and verify height lock
        val emojiPanel = host.emojiPanelView
        var backClicks = 0
        var backspaceClicks = 0
        val selected = mutableListOf<String>()
        emojiPanel.listener = object : EmojiPanelView.Listener {
            override fun onBackToKeyboard() { backClicks++ }
            override fun onEmojiSelected(emoji: String) { selected.add(emoji) }
            override fun onBackspace() { backspaceClicks++ }
        }

        emojiPanel.openPanel()
        host.setMode(KeyboardHostLayout.HostMode.EMOJI)
        val emojiHeight = measureHost()
        assertEquals(keyboardHeight, emojiHeight)
        assertEquals(keyboardHeight, emojiPanel.measuredHeight)

        // Empty recent defaults to Smileys
        assertEquals(EmojiCategory.SMILEYS, emojiPanel.currentCategory())

        // Switch to People tab, long-press first cell (👋), pick tone index 3 (medium 👋🏽)
        val peopleTab = emojiPanel.findViewWithTag<View>("emoji_tab_${EmojiCategory.PEOPLE.id}")
        assertNotNull(peopleTab)
        peopleTab.performClick()
        assertEquals(EmojiCategory.PEOPLE, emojiPanel.currentCategory())

        val firstPeopleCell = emojiPanel.findViewWithTag<TextView>("emoji_cell_people_0")
        assertNotNull(firstPeopleCell)
        assertEquals("👋", firstPeopleCell.text.toString())

        assertFalse(emojiPanel.isTonePopupVisible())
        firstPeopleCell.performLongClick()
        assertTrue(emojiPanel.isTonePopupVisible())

        val tone3 = emojiPanel.findViewWithTag<TextView>("emoji_tone_3")
        assertNotNull(tone3)
        tone3.performClick()
        assertFalse(emojiPanel.isTonePopupVisible())
        assertEquals(listOf("👋🏽"), selected)
        assertEquals("👋🏽", emojiPanel.repo.preferredEmoji("👋"))

        // Recent tab now opens by default on reopen and shows 👋🏽 first
        emojiPanel.openPanel()
        assertEquals(EmojiCategory.RECENT, emojiPanel.currentCategory())
        val firstRecent = emojiPanel.findViewWithTag<TextView>("emoji_cell_recent_0")
        assertNotNull(firstRecent)
        assertEquals("👋🏽", firstRecent.text.toString())

        // Clear recent button clears recent history
        val clearBtn = emojiPanel.findViewWithTag<View>("emoji_clear_recent_btn")
        assertNotNull(clearBtn)
        clearBtn.performClick()
        assertFalse(emojiPanel.repo.hasRecents())

        // Backspace and ABC back buttons
        emojiPanel.findViewWithTag<View>("emoji_backspace_btn").performClick()
        emojiPanel.findViewWithTag<View>("emoji_back_btn").performClick()
        assertEquals(1, backspaceClicks)
        assertEquals(1, backClicks)
    }
}
