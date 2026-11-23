package com.example

import android.app.Activity
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbarStatus
import com.example.ui.theme.SafeTextToolbar
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafeTextToolbarTest {

    private class FakeActionMode(
        private val onFinishCallback: () -> Unit = {}
    ) : ActionMode() {
        var finishCalls = 0
        var invalidateCalls = 0
        override fun setTitle(title: CharSequence?) {}
        override fun setTitle(resId: Int) {}
        override fun setSubtitle(subtitle: CharSequence?) {}
        override fun setSubtitle(resId: Int) {}
        override fun setCustomView(view: View?) {}
        override fun invalidate() { invalidateCalls++ }
        override fun finish() {
            finishCalls++
            onFinishCallback()
        }
        override fun getMenu(): Menu? = null
        override fun getTitle(): CharSequence? = null
        override fun getSubtitle(): CharSequence? = null
        override fun getCustomView(): View? = null
        override fun getMenuInflater(): MenuInflater? = null
    }

    private class FakeAttachedView(
        context: Context,
        private val modeFactory: (SafeTextToolbar) -> FakeActionMode
    ) : View(context) {
        var attached = true
        lateinit var toolbar: SafeTextToolbar
        var startCalls = 0

        override fun isAttachedToWindow(): Boolean = attached

        override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode {
            startCalls++
            return modeFactory(toolbar)
        }
    }

    @Test
    fun `hide never invokes ActionMode finish twice even with re-entrancy`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        lateinit var mode: FakeActionMode
        val view = FakeAttachedView(activity) { tb ->
            mode = FakeActionMode {
                // Simulate re-entrant hide() while finish() is unwinding onDestroyActionMode
                tb.actionModeCallback.onDestroyActionMode(mode)
                tb.hide()
            }
            mode
        }
        activity.setContentView(FrameLayout(activity).apply { addView(view) })
        val toolbar = SafeTextToolbar(view)
        view.toolbar = toolbar

        toolbar.showMenu(
            rect = Rect(0f, 0f, 100f, 50f),
            onCopyRequested = {},
            onPasteRequested = {},
            onCutRequested = null,
            onSelectAllRequested = null,
        )
        assertEquals(TextToolbarStatus.Shown, toolbar.status)
        assertEquals(1, view.startCalls)

        // Multiple hide() calls must only invoke finish() a single time
        toolbar.hide()
        toolbar.hide()
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        assertEquals(1, mode.finishCalls)
    }

    @Test
    fun `hide after DecorView already destroyed actionMode skips finish`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        lateinit var mode: FakeActionMode
        val view = FakeAttachedView(activity) {
            mode = FakeActionMode()
            mode
        }
        activity.setContentView(FrameLayout(activity).apply { addView(view) })
        val toolbar = SafeTextToolbar(view)
        view.toolbar = toolbar

        toolbar.showMenu(
            rect = Rect(10f, 20f, 110f, 70f),
            onCopyRequested = {},
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = null,
        )

        // Simulate framework destroying the floating action mode before Compose calls hide()
        toolbar.actionModeCallback.onDestroyActionMode(mode)
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)

        toolbar.hide()
        assertEquals(0, mode.finishCalls)
    }

    @Test
    fun `detached view suppresses finish and startActionMode`() {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().get()
        lateinit var mode: FakeActionMode
        val view = FakeAttachedView(activity) {
            mode = FakeActionMode()
            mode
        }
        activity.setContentView(FrameLayout(activity).apply { addView(view) })
        val toolbar = SafeTextToolbar(view)
        view.toolbar = toolbar

        toolbar.showMenu(
            rect = Rect(0f, 0f, 80f, 40f),
            onCopyRequested = null,
            onPasteRequested = {},
            onCutRequested = null,
            onSelectAllRequested = null,
        )
        view.attached = false
        toolbar.onViewDetached()
        toolbar.hide()
        assertEquals(0, mode.finishCalls)

        // Showing while detached should not start a floating ActionMode
        toolbar.showMenu(
            rect = Rect(0f, 0f, 80f, 40f),
            onCopyRequested = {},
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = null,
        )
        assertEquals(1, view.startCalls)
    }
}
