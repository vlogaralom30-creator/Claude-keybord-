package com.example.ui.theme

import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import java.util.Collections
import java.util.WeakHashMap

/**
 * Re-entrancy ও stale-ActionMode মুক্ত TextToolbar।
 * Compose UI 1.7-এ AndroidTextToolbar.hide()-এ actionMode.finish() আগে এবং actionMode = null
 * পরে হওয়ায় যে `DecorView: Destroying unexpected ActionMode instance of TYPE_FLOATING`
 * ত্রুটি দেখা দেয়, তা সম্পূর্ণভাবে প্রতিরোধ করে।
 */
class SafeTextToolbar(private val view: View) : TextToolbar {

  override var status: TextToolbarStatus = TextToolbarStatus.Hidden
    private set

  private var actionMode: ActionMode? = null
  private val destroyedModes: MutableSet<ActionMode> =
    Collections.newSetFromMap(WeakHashMap())

  private var menuRect: Rect = Rect.Zero
  private var onCopy: (() -> Unit)? = null
  private var onPaste: (() -> Unit)? = null
  private var onCut: (() -> Unit)? = null
  private var onSelectAll: (() -> Unit)? = null

  internal val actionModeCallback: ActionMode.Callback2 =
    object : ActionMode.Callback2() {
      override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        if (mode == null || menu == null) return false
        populateMenu(menu)
        return true
      }

      override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        if (mode == null || menu == null) return false
        menu.clear()
        populateMenu(menu)
        return true
      }

      override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        val action =
          when (item?.itemId) {
            MENU_COPY -> onCopy
            MENU_PASTE -> onPaste
            MENU_CUT -> onCut
            MENU_SELECT_ALL -> onSelectAll
            else -> return false
          }
        action?.invoke()
        hide()
        return true
      }

      override fun onDestroyActionMode(mode: ActionMode?) {
        if (mode != null) {
          destroyedModes.add(mode)
          if (actionMode === mode) {
            actionMode = null
          }
        } else {
          actionMode = null
        }
        status = TextToolbarStatus.Hidden
      }

      override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: AndroidRect?) {
        outRect?.set(
          menuRect.left.toInt(),
          menuRect.top.toInt(),
          menuRect.right.toInt(),
          menuRect.bottom.toInt(),
        )
      }
    }

  private fun populateMenu(menu: Menu) {
    if (onCopy != null) {
      menu.add(Menu.NONE, MENU_COPY, 0, android.R.string.copy)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }
    if (onPaste != null) {
      menu.add(Menu.NONE, MENU_PASTE, 1, android.R.string.paste)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }
    if (onCut != null) {
      menu.add(Menu.NONE, MENU_CUT, 2, android.R.string.cut)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }
    if (onSelectAll != null) {
      menu.add(Menu.NONE, MENU_SELECT_ALL, 3, android.R.string.selectAll)
        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }
  }

  override fun showMenu(
    rect: Rect,
    onCopyRequested: (() -> Unit)?,
    onPasteRequested: (() -> Unit)?,
    onCutRequested: (() -> Unit)?,
    onSelectAllRequested: (() -> Unit)?,
  ) {
    menuRect = rect
    onCopy = onCopyRequested
    onPaste = onPasteRequested
    onCut = onCutRequested
    onSelectAll = onSelectAllRequested

    if (onCopyRequested == null &&
      onPasteRequested == null &&
      onCutRequested == null &&
      onSelectAllRequested == null
    ) {
      hide()
      return
    }

    if (!view.isAttachedToWindow) {
      status = TextToolbarStatus.Hidden
      return
    }

    val current = actionMode
    if (current != null && !destroyedModes.contains(current)) {
      status = TextToolbarStatus.Shown
      current.invalidate()
      return
    }

    actionMode = null
    val started = view.startActionMode(actionModeCallback, ActionMode.TYPE_FLOATING)
    if (started != null) {
      actionMode = started
      status = TextToolbarStatus.Shown
    } else {
      status = TextToolbarStatus.Hidden
    }
  }

  override fun hide() {
    status = TextToolbarStatus.Hidden
    val current = actionMode
    actionMode = null
    if (current != null && destroyedModes.add(current) && view.isAttachedToWindow) {
      current.finish()
    }
  }

  fun onViewDetached() {
    val current = actionMode
    actionMode = null
    status = TextToolbarStatus.Hidden
    if (current != null) {
      destroyedModes.add(current)
    }
  }

  private companion object {
    const val MENU_COPY = 0
    const val MENU_PASTE = 1
    const val MENU_CUT = 2
    const val MENU_SELECT_ALL = 3
  }
}

@Composable
fun ProvideSafeTextToolbar(content: @Composable () -> Unit) {
  val view = LocalView.current
  val toolbar = remember(view) { SafeTextToolbar(view) }
  DisposableEffect(view, toolbar) {
    val listener =
      object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}

        override fun onViewDetachedFromWindow(v: View) {
          toolbar.onViewDetached()
        }
      }
    view.addOnAttachStateChangeListener(listener)
    onDispose {
      view.removeOnAttachStateChangeListener(listener)
      toolbar.hide()
    }
  }
  CompositionLocalProvider(LocalTextToolbar provides toolbar, content = content)
}
