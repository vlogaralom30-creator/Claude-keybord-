package com.example

import com.banglakeys.app.EffectsConfig
import com.banglakeys.app.KeyEffects
import com.banglakeys.app.KeyStyle
import com.banglakeys.app.KeyboardTheme
import com.banglakeys.app.PerfRecorder
import com.banglakeys.app.Themes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyEffectsTest {

    @Test fun `progress is clamped and eases out`() {
        assertEquals(0f, KeyEffects.progress(100, 100, 150), 0.0001f)
        assertEquals(0.5f, KeyEffects.progress(175, 100, 150), 0.0001f)
        assertEquals(1f, KeyEffects.progress(999, 100, 150), 0.0001f)
        assertEquals(0f, KeyEffects.progress(50, 100, 150), 0.0001f)
        assertTrue(KeyEffects.easeOut(0.5f) > 0.5f)
        assertEquals(1f, KeyEffects.easeOut(1f), 0.0001f)
    }

    @Test fun `levels scale duration and alpha monotonically`() {
        assertTrue(KeyEffects.durationFor(1) < KeyEffects.durationFor(2))
        assertTrue(KeyEffects.durationFor(2) < KeyEffects.durationFor(3))
        assertTrue(KeyEffects.alphaFor(1) < KeyEffects.alphaFor(3))
        // Light effect দ্রুত শেষ হয় — typing-এ বাড়তি frame কম
        assertTrue(KeyEffects.durationFor(1) <= 160)
    }

    @Test fun `disabled level never animates and popup scale stays 1`() {
        val fx = KeyEffects(2f)
        fx.level = 0
        fx.onPress(0f, 0f, 10f, 10f, 5f, 5f, 1000)
        assertFalse(fx.active)
        assertEquals(1f, fx.popupScale(1010), 0f)
        assertFalse(fx.popupAnimating(1010))
    }

    @Test fun `popup scale grows from 88 percent to full and clear resets it`() {
        val fx = KeyEffects(2f)
        fx.level = 1
        fx.onPress(0f, 0f, 10f, 10f, 5f, 5f, 1000L)
        assertEquals(0.88f, fx.popupScale(1000L), 0.001f)
        assertEquals(1f, fx.popupScale(1000L + KeyEffects.POPUP_MS + 5), 0.001f)
        assertTrue(fx.popupAnimating(1010L))
        assertFalse(fx.popupAnimating(1000L + KeyEffects.POPUP_MS))

        fx.onPress(0f, 0f, 10f, 10f, 5f, 5f, 2000)
        assertTrue(fx.popupAnimating(2010))
        fx.clear()
        assertFalse(fx.popupAnimating(2010))
        assertEquals(1f, fx.popupScale(2010), 0f)
    }

    @Test fun `kindFor picks style default and respects theme override`() {
        assertEquals(KeyEffects.Kind.GLOW, KeyEffects.kindFor(Themes.byId("neon_cyber")!!))
        assertEquals(KeyEffects.Kind.FLASH, KeyEffects.kindFor(Themes.byId("pixel_arcade")!!))
        assertEquals(KeyEffects.Kind.FLASH, KeyEffects.kindFor(Themes.byId("grass_blocks")!!))
        assertEquals(KeyEffects.Kind.RIPPLE, KeyEffects.kindFor(Themes.lightDefault))

        val overrideTheme = KeyboardTheme(
            id = "custom_fx", name = "Custom", emoji = "✨", style = KeyStyle.FLAT, dark = true,
            bg = 0, key = 0, fn = 0, shadow = 0, text = 0, hint = 0, press = 0, accent = 0,
            effect = KeyEffects.Kind.GLOW,
        )
        assertEquals(KeyEffects.Kind.GLOW, KeyEffects.kindFor(overrideTheme))
    }

    @Test fun `percentile and snapshot math and clearPendingDown`() {
        val s = longArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 100)
        assertEquals(100L, PerfRecorder.percentile(s, 100))
        assertEquals(1L, PerfRecorder.percentile(s, 0))
        assertEquals(0L, PerfRecorder.percentile(LongArray(0), 95))

        PerfRecorder.reset()
        PerfRecorder.markDown()
        PerfRecorder.clearPendingDown()
        PerfRecorder.onDrawDone(System.nanoTime())
        val snap = PerfRecorder.snapshot()
        assertEquals(1, snap.samples)
        assertEquals(0, snap.latSamples)
        PerfRecorder.reset()
    }

    @Test fun `config level is clamped`() {
        assertEquals(0, EffectsConfig.clampLevel(-5))
        assertEquals(3, EffectsConfig.clampLevel(99))
        assertEquals(1, EffectsConfig.DEFAULT_LEVEL)
    }
}
