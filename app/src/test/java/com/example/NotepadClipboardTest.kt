package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.ClipboardMemory
import com.banglakeys.app.KeyboardView
import com.banglakeys.app.Layouts
import com.banglakeys.app.NotepadClipboardPanelView
import com.banglakeys.app.Notes
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
class NotepadClipboardTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("bk_notes", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("bk_clipboard", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `notes and clipboard memory support contains update and reload`() {
        val notes = Notes(context)
        notes.add("আমার বাসার ঠিকানা")
        assertTrue(notes.contains("আমার বাসার ঠিকানা"))
        assertTrue(notes.updateByText("আমার বাসার ঠিকানা", "ঢাকা বাংলাদেশ"))
        assertFalse(notes.contains("আমার বাসার ঠিকানা"))
        assertTrue(notes.contains("ঢাকা বাংলাদেশ"))
        assertTrue(notes.removeByText("ঢাকা বাংলাদেশ"))
        assertFalse(notes.contains("ঢাকা বাংলাদেশ"))

        val clipA = ClipboardMemory(context)
        val clipB = ClipboardMemory(context)
        clipA.add("First copied snippet")
        clipA.add("Second copied snippet")
        assertTrue(clipA.update("First copied snippet", "Updated copied snippet"))
        assertTrue(clipA.clips().contains("Updated copied snippet"))
        assertFalse(clipA.clips().contains("First copied snippet"))

        clipB.reload()
        assertEquals(clipA.clips(), clipB.clips())
    }

    @Test
    fun `panel keeps exact keyboard height without jump and dispatches pin edit remove actions`() {
        val host = com.banglakeys.app.KeyboardHostLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val kv = host.keyboardView
        kv.setLayout(Layouts.phonetic(numberRow = false, bnDigits = true), "বাংলা", 1f)
        val panel = host.panelView

        fun measureRoot(): Int {
            host.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.AT_MOST)
            )
            host.layout(0, 0, host.measuredWidth, host.measuredHeight)
            return host.measuredHeight
        }

        val keyboardHeight = measureRoot()
        assertTrue(keyboardHeight > 0)

        var backCount = 0
        var addNoteCount = 0
        val pasted = mutableListOf<String>()
        val pinnedRecent = mutableListOf<String>()
        val unpinnedNotes = mutableListOf<Pair<Int, String>>()
        val editedPinned = mutableListOf<Pair<Int, String>>()
        val editedRecent = mutableListOf<String>()
        val removedPinned = mutableListOf<Pair<Int, String>>()
        val removedRecent = mutableListOf<String>()
        var clearedRecent = 0

        panel.listener = object : NotepadClipboardPanelView.Listener {
            override fun onBackToKeyboard() { backCount++ }
            override fun onPasteItem(text: String) { pasted.add(text) }
            override fun onAddNewNote() { addNoteCount++ }
            override fun onPinRecent(text: String) { pinnedRecent.add(text) }
            override fun onUnpinNote(index: Int, text: String) { unpinnedNotes.add(index to text) }
            override fun onEditPinned(index: Int, text: String) { editedPinned.add(index to text) }
            override fun onEditRecent(text: String) { editedRecent.add(text) }
            override fun onRemovePinned(index: Int, text: String) { removedPinned.add(index to text) }
            override fun onRemoveRecent(text: String) { removedRecent.add(text) }
            override fun onClearRecent() { clearedRecent++ }
        }

        panel.setData(
            pinned = listOf("Pinned Note Alpha", "Pinned Note Beta", "Pinned Note Gamma"),
            recent = listOf("Recent Clip One", "Recent Clip Two")
        )
        host.showPanel(true)
        val panelHeight = measureRoot()

        // Keyboard height must remain locked identically when switching to clipboard panel
        assertEquals(keyboardHeight, panelHeight)
        assertEquals(keyboardHeight, panel.measuredHeight)

        // Header actions
        panel.findViewWithTag<View>("panel_back_btn").performClick()
        panel.findViewWithTag<View>("panel_add_note_btn").performClick()
        assertEquals(1, backCount)
        assertEquals(1, addNoteCount)

        // Toggle manage mode reveals Pin/Unpin/Edit/Remove controls
        val manageBtn = panel.findViewWithTag<View>("panel_manage_btn")
        assertNotNull(manageBtn)
        assertFalse(panel.isManageMode())
        manageBtn.performClick()
        assertTrue(panel.isManageMode())

        panel.findViewWithTag<View>("panel_unpin_0").performClick()
        panel.findViewWithTag<View>("panel_edit_pinned_0").performClick()
        panel.findViewWithTag<View>("panel_remove_pinned_1").performClick()
        panel.findViewWithTag<View>("panel_pin_0").performClick()
        panel.findViewWithTag<View>("panel_edit_recent_0").performClick()
        panel.findViewWithTag<View>("panel_remove_recent_1").performClick()
        panel.findViewWithTag<View>("panel_clear_recent_btn").performClick()

        assertEquals(listOf(0 to "Pinned Note Alpha"), unpinnedNotes)
        assertEquals(listOf(0 to "Pinned Note Alpha"), editedPinned)
        assertEquals(listOf(1 to "Pinned Note Beta"), removedPinned)
        assertEquals(listOf("Recent Clip One"), pinnedRecent)
        assertEquals(listOf("Recent Clip One"), editedRecent)
        assertEquals(listOf("Recent Clip Two"), removedRecent)
        assertEquals(1, clearedRecent)
    }

    @Test
    fun `keyboardView note edit strip draws and dispatches cancel and save`() {
        val kv = KeyboardView(context)
        kv.setLayout(Layouts.phonetic(numberRow = false, bnDigits = true), "বাংলা", 1f)
        kv.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST)
        )
        kv.layout(0, 0, kv.measuredWidth, kv.measuredHeight)

        var saved = 0
        var cancelled = 0
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
            override fun onNoteEditSave() { saved++ }
            override fun onNoteEditCancel() { cancelled++ }
        }

        kv.setNoteEditMode(true)
        kv.setComposing("✏️ নোট লিখছি")
        assertTrue(kv.isNoteEditMode())

        val cancelRect = kv.stripNoteCancelRect()
        val saveRect = kv.stripNoteSaveRect()
        assertFalse(cancelRect.isEmpty)
        assertFalse(saveRect.isEmpty)

        val bmp = Bitmap.createBitmap(kv.measuredWidth, kv.measuredHeight, Bitmap.Config.ARGB_8888)
        kv.draw(Canvas(bmp))

        fun tap(x: Float, y: Float) {
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, x, y, 0)
            kv.onTouchEvent(down)
            kv.onTouchEvent(up)
            down.recycle()
            up.recycle()
        }

        tap(cancelRect.centerX(), cancelRect.centerY())
        tap(saveRect.centerX(), saveRect.centerY())

        assertEquals(1, cancelled)
        assertEquals(1, saved)
    }
}
