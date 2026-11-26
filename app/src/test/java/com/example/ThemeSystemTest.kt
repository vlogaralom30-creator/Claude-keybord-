package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.KeyStyle
import com.banglakeys.app.KeyboardHostLayout
import com.banglakeys.app.KeyboardView
import com.banglakeys.app.Layouts
import com.banglakeys.app.ThemeAssets
import com.banglakeys.app.ThemeAutoSwitcher
import com.banglakeys.app.ThemeStore
import com.banglakeys.app.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeSystemTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("bk", Context.MODE_PRIVATE).edit().clear().commit()
    ThemeStore.clearCustomImage(context)
  }

  @Test
  fun `themes list includes all key styles and resolves selected theme`() {
    assertTrue(Themes.all.size >= 18)
    assertEquals(Themes.AUTO_ID, ThemeStore.selectedId(context))
    assertNotNull(ThemeStore.resolve(context))

    ThemeStore.setSelectedId(context, "pixel_arcade")
    val resolved = ThemeStore.resolve(context)
    assertEquals("pixel_arcade", resolved.id)
    assertEquals(KeyStyle.PIXEL, resolved.style)

    // Custom image theme falls back cleanly when no photo is stored yet
    ThemeStore.setSelectedId(context, Themes.CUSTOM_ID)
    assertEquals(Themes.darkDefault.id, ThemeStore.resolve(context).id)
  }

  @Test
  fun `built in image themes decode assets and render cleanly across host views`() {
    val assetThemes = listOf("teal_noir", "manga_ink", "violet_ink")
    val host = KeyboardHostLayout(context)
    val kv = host.keyboardView
    kv.setLayout(Layouts.phonetic(numberRow = false, bnDigits = true), "বাংলা", 1f)
    host.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.AT_MOST)
    )
    host.layout(0, 0, host.measuredWidth, host.measuredHeight)
    val bmp = Bitmap.createBitmap(
      kv.measuredWidth.coerceAtLeast(1),
      kv.measuredHeight.coerceAtLeast(1),
      Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bmp)

    for (id in assetThemes) {
      val theme = Themes.byId(id)
      assertNotNull("Missing theme $id", theme)
      assertNotNull("Missing bgAsset for $id", theme!!.bgAsset?.let { ThemeAssets.get(it) })
      for (tile in theme.keyTiles) {
        assertNotNull("Missing tile asset $tile for $id", ThemeAssets.get(tile))
      }

      ThemeStore.setSelectedId(context, id)
      assertEquals(id, ThemeStore.resolve(context).id)
      kv.reloadTheme()
      host.panelView.reloadTheme()
      host.emojiPanelView.reloadTheme()
      kv.draw(canvas)
    }

    assertEquals(KeyStyle.TILE, Themes.byId("manga_ink")?.style)
  }

  @Test
  fun `theme image alpha and dim clamp to valid ranges and KeyboardView reloads`() {
    ThemeStore.setImageAlpha(context, 5)
    assertEquals(ThemeStore.MIN_ALPHA, ThemeStore.imageAlpha(context))
    ThemeStore.setImageAlpha(context, 200)
    assertEquals(ThemeStore.MAX_ALPHA, ThemeStore.imageAlpha(context))

    ThemeStore.setImageDim(context, 95)
    assertEquals(70, ThemeStore.imageDim(context))

    ThemeStore.setSelectedId(context, "neon_cyber")
    val kv = KeyboardView(context)
    kv.setLayout(Layouts.phonetic(numberRow = false, bnDigits = true), "বাংলা", 1f)
    kv.reloadTheme()
    kv.setSecretMode(true, "বন্ধু")
    assertTrue(kv.isSecretMode())
  }

  @Test
  fun `auto switcher rotates themes by time of day and advances across slots`() {
    ThemeAutoSwitcher.setMode(context, ThemeAutoSwitcher.Mode.TIME_OF_DAY)
    val morningMs = Calendar.getInstance().apply {
      set(2026, Calendar.MARCH, 10, 9, 0, 0)
    }.timeInMillis
    val nightMs = Calendar.getInstance().apply {
      set(2026, Calendar.MARCH, 10, 23, 0, 0)
    }.timeInMillis
    val nextMorningMs = Calendar.getInstance().apply {
      set(2026, Calendar.MARCH, 11, 9, 0, 0)
    }.timeInMillis

    val morningTheme1 = Themes.byId(ThemeAutoSwitcher.resolveThemeId(context, morningMs))!!
    assertFalse(morningTheme1.dark)

    val nightTheme = Themes.byId(ThemeAutoSwitcher.resolveThemeId(context, nightMs))!!
    assertTrue(nightTheme.dark)

    // Returning to morning on a new day rotates to the next daytime theme
    val morningTheme2 = Themes.byId(ThemeAutoSwitcher.resolveThemeId(context, nextMorningMs))!!
    assertFalse(morningTheme2.dark)
    assertNotEquals(morningTheme1.id, morningTheme2.id)

    // Manual rotateNow immediately picks another theme in the active slot
    val rotated = ThemeAutoSwitcher.rotateNow(context, nextMorningMs)
    assertNotEquals(morningTheme2.id, rotated.id)
  }

  @Test
  fun `auto switcher rotates by session and keystroke usage frequency within custom pool`() {
    ThemeStore.setSelectedId(context, "pixel_arcade")
    ThemeAutoSwitcher.setPoolIds(context, listOf("sakura", "neon_cyber", "manga_ink"))
    ThemeAutoSwitcher.setMode(context, ThemeAutoSwitcher.Mode.USAGE_FREQUENCY)
    ThemeAutoSwitcher.setCadence(context, ThemeAutoSwitcher.UsageCadence.OPENS_5)

    assertEquals("sakura", ThemeStore.resolve(context).id)

    // 4 opens should not rotate yet
    repeat(4) {
      assertFalse(ThemeAutoSwitcher.onKeyboardOpen(context))
      assertEquals("sakura", ThemeStore.resolve(context).id)
    }
    assertEquals(4, ThemeAutoSwitcher.usageProgress(context))

    // 5th open triggers rotation to neon_cyber and resets progress
    assertTrue(ThemeAutoSwitcher.onKeyboardOpen(context))
    assertEquals(0, ThemeAutoSwitcher.usageProgress(context))
    assertEquals("neon_cyber", ThemeStore.resolve(context).id)
    assertEquals("sakura", ThemeAutoSwitcher.mostUsedTheme(context)?.first?.id)

    // Keystroke cadence rotates after 100 keys
    ThemeAutoSwitcher.setCadence(context, ThemeAutoSwitcher.UsageCadence.KEYS_100)
    repeat(99) {
      assertFalse(ThemeAutoSwitcher.onKeyTyped(context))
    }
    assertTrue(ThemeAutoSwitcher.onKeyTyped(context))
    assertEquals("manga_ink", ThemeStore.resolve(context).id)

    // Pool keeps at least 1 theme when toggling
    ThemeAutoSwitcher.toggleThemeInPool(context, "sakura")
    ThemeAutoSwitcher.toggleThemeInPool(context, "neon_cyber")
    val lastPool = ThemeAutoSwitcher.toggleThemeInPool(context, "manga_ink")
    assertEquals(listOf("manga_ink"), lastPool)

    // Turning auto switcher OFF restores manual selection
    ThemeAutoSwitcher.setMode(context, ThemeAutoSwitcher.Mode.OFF)
    assertEquals("pixel_arcade", ThemeStore.resolve(context).id)
  }
}
