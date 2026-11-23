package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.banglakeys.app.KeyStyle
import com.banglakeys.app.KeyboardView
import com.banglakeys.app.Layouts
import com.banglakeys.app.ThemeStore
import com.banglakeys.app.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    assertTrue(Themes.all.size >= 10)
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
}
