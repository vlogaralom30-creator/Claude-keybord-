package com.banglakeys.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.File
import kotlin.math.max

/**
 * Keyboard theme system।
 *
 * নতুন theme যোগ করতে: নিচে [Themes.all]-এর list-এ একটা নতুন KeyboardTheme(...) লাইন যোগ করলেই হবে।
 * আর কোনো file বদলাতে হবে না — picker আর keyboard নিজে থেকে নতুন theme দেখাবে।
 */
enum class KeyStyle {
    /** সাধারণ rounded key (optional border/shadow) */
    FLAT,
    /** Retro pixel/arcade — মোটা outline + bevel */
    PIXEL,
    /** Block style — উপরে ঘাসের স্তর, নিচে মাটি (procedural, কোনো image লাগে না) */
    BLOCK,
    /** Neon outline glow */
    NEON,
    /** Semi-transparent glass key */
    GLASS,
    /** Key-র ভেতরে উপর→নিচ gradient */
    GRADIENT,
    /** প্রতিটা key-তে ছোট ছবির tile (keyTiles) */
    TILE,
}

internal fun mixColor(a: Int, b: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val x = (a shr shift) and 0xFF
        val y = (b shr shift) and 0xFF
        return (x + (y - x) * tt).toInt().coerceIn(0, 255)
    }
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

internal fun withAlpha(color: Int, alpha: Int): Int =
    (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

private fun c(v: Long): Int = v.toInt()

class KeyboardTheme(
    val id: String,
    val name: String,
    val emoji: String,
    val style: KeyStyle,
    val dark: Boolean,
    val bg: Int,
    val key: Int,
    val fn: Int,
    val shadow: Int,
    val text: Int,
    val hint: Int,
    val press: Int,
    val accent: Int,
    val popup: Int = key,
    val onAccent: Int = c(0xFFFFFFFF),
    // optional: background gradient (দুটোই 0 না হলে চালু)
    val bgTop: Int = 0,
    val bgBottom: Int = 0,
    // GRADIENT style: key gradient | BLOCK style: keyTop = ঘাসের রঙ
    val keyTop: Int = 0,
    val keyBottom: Int = 0,
    val border: Int = 0,
    val borderDp: Float = 0f,
    val radiusDp: Float = 6f,
    val shadowDp: Float = 1.3f,
    val monoFont: Boolean = false,
    val usesImage: Boolean = false,
    // built-in ছবি (ThemeAssets-এর নাম) — background আর key tile
    val bgAsset: String? = null,
    val bgDimPct: Int = 0,
    val keyTiles: List<String> = emptyList(),
    val tileScrim: Int = 0,
    // suggestion strip (না দিলে auto হিসাব হবে)
    val pill: Int = key,
    val pillStroke: Int = mixColor(key, text, 0.18f),
    val badgeBg: Int = mixColor(bg, accent, 0.25f),
    val badgeStroke: Int = accent,
    val badgeText: Int = mixColor(accent, text, 0.40f),
    val actionBg: Int = fn,
)

object Themes {
    const val AUTO_ID = "auto"
    const val CUSTOM_ID = "custom_image"

    val lightDefault = KeyboardTheme(
        id = "auto_light", name = "System", emoji = "🌗", style = KeyStyle.FLAT, dark = false,
        bg = c(0xFFE6E9EC), key = c(0xFFFFFFFF), fn = c(0xFFC9D0D6), shadow = c(0xFFA9B1B8),
        text = c(0xFF1F2328), hint = c(0xFF7A838B), press = c(0xFFB4BDC5), accent = c(0xFF00897B),
        popup = c(0xFFFFFFFF),
        pillStroke = c(0xFFD0D7DE), badgeBg = c(0xFFE0F2F1), badgeStroke = c(0xFF4DB6AC),
        badgeText = c(0xFF00695C), actionBg = c(0xFFD8DFE6),
    )

    val darkDefault = KeyboardTheme(
        id = "auto_dark", name = "System", emoji = "🌗", style = KeyStyle.FLAT, dark = true,
        bg = c(0xFF1C1C1E), key = c(0xFF3A3A3C), fn = c(0xFF2C2C2E), shadow = c(0xFF0E0E0F),
        text = c(0xFFF2F2F7), hint = c(0xFF9A9AA0), press = c(0xFF636366), accent = c(0xFF26A69A),
        popup = c(0xFF4A4A4D),
        pillStroke = c(0xFF525256), badgeBg = c(0xFF163C38), badgeStroke = c(0xFF26A69A),
        badgeText = c(0xFF80CBC4), actionBg = c(0xFF2C2C2E),
    )

    /** Picker-এ যে ক্রমে দেখাবে */
    val all: List<KeyboardTheme> = listOf(
        // ---------- System ----------
        KeyboardTheme(
            id = AUTO_ID, name = "System", emoji = "🌗", style = KeyStyle.FLAT, dark = false,
            bg = lightDefault.bg, key = lightDefault.key, fn = lightDefault.fn,
            shadow = lightDefault.shadow, text = lightDefault.text, hint = lightDefault.hint,
            press = lightDefault.press, accent = lightDefault.accent,
        ),

        // ---------- Pixel Arcade (retro gaming keyboard) ----------
        KeyboardTheme(
            id = "pixel_arcade", name = "Pixel Arcade", emoji = "🕹️", style = KeyStyle.PIXEL, dark = true,
            bg = c(0xFF4A505E), key = c(0xFF8D9CC6), fn = c(0xFF6B7AA6), shadow = c(0xFF0D0F24),
            text = c(0xFF1B1F4B), hint = c(0xFF2E3566), press = c(0xFFB4C0E4), accent = c(0xFFE5484D),
            popup = c(0xFF8D9CC6), border = c(0xFF12142E), borderDp = 2.2f, radiusDp = 2.5f,
            shadowDp = 2.2f, monoFont = true,
            pill = c(0xFF8D9CC6), pillStroke = c(0xFF12142E), badgeBg = c(0xFFFFD54F),
            badgeStroke = c(0xFF12142E), badgeText = c(0xFF3B2A00), actionBg = c(0xFF6B7AA6),
        ),

        // ---------- Grass Blocks (block-game style) ----------
        KeyboardTheme(
            id = "grass_blocks", name = "Grass Blocks", emoji = "🟫", style = KeyStyle.BLOCK, dark = true,
            bg = c(0xFF3A281D), key = c(0xFFB8652F), fn = c(0xFF8A8A8A), shadow = c(0xFF22150E),
            text = c(0xFFFFFFFF), hint = c(0xFFF1D3B3), press = c(0xFF8E4A20), accent = c(0xFF5DA130),
            popup = c(0xFFB8652F), keyTop = c(0xFF6FAE3B), border = c(0xFF4A2A14), borderDp = 1.2f,
            radiusDp = 5f, shadowDp = 1.6f,
            pill = c(0xFFB8652F), pillStroke = c(0xFF4A2A14), badgeBg = c(0xFF3F6B22),
            badgeStroke = c(0xFF6FAE3B), badgeText = c(0xFFD9F5BF), actionBg = c(0xFF6E6E6E),
        ),

        // ---------- Graphite (flat dark gray, no shadow) ----------
        KeyboardTheme(
            id = "graphite", name = "Graphite", emoji = "⬛", style = KeyStyle.FLAT, dark = true,
            bg = c(0xFF161616), key = c(0xFF5F5F5F), fn = c(0xFF262626), shadow = c(0xFF000000),
            text = c(0xFFEDEDED), hint = c(0xFFB5B5B5), press = c(0xFF7C7C7C), accent = c(0xFF4F9DFF),
            popup = c(0xFF6E6E6E), radiusDp = 8f, shadowDp = 0f,
            pill = c(0xFF3A3A3A), pillStroke = c(0xFF4A4A4A), actionBg = c(0xFF262626),
        ),

        // ---------- AMOLED Black ----------
        KeyboardTheme(
            id = "amoled", name = "AMOLED", emoji = "🌑", style = KeyStyle.FLAT, dark = true,
            bg = c(0xFF000000), key = c(0xFF121212), fn = c(0xFF0A0A0A), shadow = c(0xFF000000),
            text = c(0xFFFFFFFF), hint = c(0xFF8C8C8C), press = c(0xFF2C2C2C), accent = c(0xFF00E5A0),
            popup = c(0xFF1E1E1E), border = c(0xFF2A2A2A), borderDp = 0.8f, radiusDp = 8f, shadowDp = 0f,
            onAccent = c(0xFF00110B),
        ),

        // ---------- Neon Cyber ----------
        KeyboardTheme(
            id = "neon_cyber", name = "Neon Cyber", emoji = "⚡", style = KeyStyle.NEON, dark = true,
            bg = c(0xFF090916), key = c(0xFF12122A), fn = c(0xFF0E0E20), shadow = c(0xFF000000),
            text = c(0xFFE6F7FF), hint = c(0xFF7FA8C9), press = c(0xFF2A2A55), accent = c(0xFFFF2BD6),
            popup = c(0xFF1A1A3A), border = c(0xFF00E5FF), borderDp = 1.3f, radiusDp = 8f, shadowDp = 0f,
            pillStroke = c(0xFF00B8D4), badgeBg = c(0xFF2A0F3A), badgeStroke = c(0xFFFF2BD6),
            badgeText = c(0xFFFFB3F0),
        ),

        // ---------- Sticker Night (white sticker keys, dark bg) ----------
        KeyboardTheme(
            id = "sticker_night", name = "Sticker Night", emoji = "🎀", style = KeyStyle.FLAT, dark = true,
            bg = c(0xFF1A1D26), key = c(0xFFFFFFFF), fn = c(0xFFE3E4EE), shadow = c(0xFF0A0B10),
            text = c(0xFF22242C), hint = c(0xFF6B6F80), press = c(0xFFFFD6E0), accent = c(0xFFFF6B8A),
            popup = c(0xFFFFFFFF), border = c(0xFF0E0F14), borderDp = 1.5f, radiusDp = 12f, shadowDp = 0f,
            pill = c(0xFFFFFFFF), pillStroke = c(0xFF0E0F14), badgeBg = c(0xFF3A2030),
            badgeStroke = c(0xFFFF6B8A), badgeText = c(0xFFFFC2D0), actionBg = c(0xFFE3E4EE),
        ),

        // ---------- Sakura ----------
        KeyboardTheme(
            id = "sakura", name = "Sakura", emoji = "🌸", style = KeyStyle.FLAT, dark = false,
            bg = c(0xFFFFE9F0), key = c(0xFFFFFFFF), fn = c(0xFFF8C8D8), shadow = c(0xFFE5A9BC),
            text = c(0xFF5B2A3C), hint = c(0xFFB07C8D), press = c(0xFFF3B4C9), accent = c(0xFFEC6A9A),
            popup = c(0xFFFFFFFF), radiusDp = 10f, shadowDp = 1.4f,
            pillStroke = c(0xFFF3C2D2), badgeBg = c(0xFFFFD6E4), badgeStroke = c(0xFFEC6A9A),
            badgeText = c(0xFF9C2F5A),
        ),

        // ---------- Warm Clay (cream + terracotta) ----------
        KeyboardTheme(
            id = "warm_clay", name = "Warm Clay", emoji = "🍂", style = KeyStyle.FLAT, dark = false,
            bg = c(0xFFF0EEE6), key = c(0xFFFAF9F5), fn = c(0xFFE3DFD2), shadow = c(0xFFCFC9B8),
            text = c(0xFF3D3929), hint = c(0xFF8A8570), press = c(0xFFDDD8C6), accent = c(0xFFD97757),
            popup = c(0xFFFAF9F5), radiusDp = 9f, shadowDp = 1.3f,
            pillStroke = c(0xFFDDD8C6), badgeBg = c(0xFFF7E1D8), badgeStroke = c(0xFFD97757),
            badgeText = c(0xFF9A4A2E),
        ),

        // ---------- Mint Dark ----------
        KeyboardTheme(
            id = "mint_dark", name = "Mint Dark", emoji = "🍃", style = KeyStyle.FLAT, dark = true,
            bg = c(0xFF212121), key = c(0xFF2F2F2F), fn = c(0xFF262626), shadow = c(0xFF151515),
            text = c(0xFFECECEC), hint = c(0xFF9A9A9A), press = c(0xFF444444), accent = c(0xFF10A37F),
            popup = c(0xFF3A3A3A), radiusDp = 8f, shadowDp = 0.9f,
            pillStroke = c(0xFF454545), badgeBg = c(0xFF10382E), badgeStroke = c(0xFF10A37F),
            badgeText = c(0xFF86E3C9),
        ),

        // ---------- Aurora Light (soft gradient keys) ----------
        KeyboardTheme(
            id = "aurora_light", name = "Aurora", emoji = "💠", style = KeyStyle.GRADIENT, dark = false,
            bg = c(0xFFEEF2FF), key = c(0xFFFFFFFF), keyTop = c(0xFFFFFFFF), keyBottom = c(0xFFE6ECFF),
            fn = c(0xFFD8E1FF), shadow = c(0xFFC2CDEE), text = c(0xFF1F2A44), hint = c(0xFF7480A3),
            press = c(0xFFC7D4FF), accent = c(0xFF4C7DFF), popup = c(0xFFFFFFFF),
            radiusDp = 12f, shadowDp = 1.4f,
            pillStroke = c(0xFFD3DCF7), badgeBg = c(0xFFDCE6FF), badgeStroke = c(0xFF4C7DFF),
            badgeText = c(0xFF2A4BB8),
        ),

        // ---------- Glass Frost ----------
        KeyboardTheme(
            id = "glass_frost", name = "Glass Frost", emoji = "🧊", style = KeyStyle.GLASS, dark = false,
            bg = c(0xFFC9D5E6), bgTop = c(0xFFE0E9F5), bgBottom = c(0xFFB5C4DB),
            key = c(0x99FFFFFF), fn = c(0x66FFFFFF), shadow = c(0x22000000),
            text = c(0xFF1F2A3C), hint = c(0xFF5B6B84), press = c(0xCCFFFFFF), accent = c(0xFF5B8DEF),
            popup = c(0xFFFFFFFF), border = c(0xAAFFFFFF), borderDp = 0.9f, radiusDp = 11f, shadowDp = 0f,
            pill = c(0x99FFFFFF), pillStroke = c(0xAAFFFFFF), badgeBg = c(0xAAD9E6FF),
            badgeStroke = c(0xFF5B8DEF), badgeText = c(0xFF2A4BB8), actionBg = c(0x66FFFFFF),
        ),

        // ---------- Ocean Glass ----------
        KeyboardTheme(
            id = "ocean_glass", name = "Ocean", emoji = "🌊", style = KeyStyle.GLASS, dark = true,
            bg = c(0xFF0A2540), bgTop = c(0xFF1565A8), bgBottom = c(0xFF081C33),
            key = c(0x33FFFFFF), fn = c(0x1FFFFFFF), shadow = c(0x33000000),
            text = c(0xFFFFFFFF), hint = c(0xFFB3D4F0), press = c(0x66FFFFFF), accent = c(0xFF4FC3F7),
            popup = c(0xFF2A6FAE), border = c(0x44FFFFFF), borderDp = 0.8f, radiusDp = 11f, shadowDp = 0f,
            onAccent = c(0xFF04243A),
            pill = c(0x33FFFFFF), pillStroke = c(0x44FFFFFF), badgeBg = c(0x554FC3F7),
            badgeStroke = c(0xFF4FC3F7), badgeText = c(0xFFE1F5FE), actionBg = c(0x1FFFFFFF),
        ),

        // ---------- Sunset Glass ----------
        KeyboardTheme(
            id = "sunset_glass", name = "Sunset", emoji = "🌇", style = KeyStyle.GLASS, dark = true,
            bg = c(0xFF4A2A6E), bgTop = c(0xFFFF7E5F), bgBottom = c(0xFF5B2A86),
            key = c(0x33FFFFFF), fn = c(0x22FFFFFF), shadow = c(0x33000000),
            text = c(0xFFFFFFFF), hint = c(0xFFFFE0D2), press = c(0x66FFFFFF), accent = c(0xFFFFC857),
            popup = c(0xFFB5527A), border = c(0x44FFFFFF), borderDp = 0.8f, radiusDp = 11f, shadowDp = 0f,
            onAccent = c(0xFF3A2200),
            pill = c(0x33FFFFFF), pillStroke = c(0x44FFFFFF), badgeBg = c(0x55FFC857),
            badgeStroke = c(0xFFFFC857), badgeText = c(0xFFFFF3D0), actionBg = c(0x22FFFFFF),
        ),

        // ---------- Forest Night ----------
        KeyboardTheme(
            id = "forest_night", name = "Forest", emoji = "🌲", style = KeyStyle.FLAT, dark = true,
            bg = c(0xFF10201A), key = c(0xFF1E3A2D), fn = c(0xFF16291F), shadow = c(0xFF08120D),
            text = c(0xFFE4F4EA), hint = c(0xFF86AE98), press = c(0xFF2F5A45), accent = c(0xFF66BB6A),
            popup = c(0xFF2A4C3B), radiusDp = 8f, shadowDp = 1.2f, onAccent = c(0xFF06210C),
            pillStroke = c(0xFF2F5A45), badgeBg = c(0xFF1B4A2A), badgeStroke = c(0xFF66BB6A),
            badgeText = c(0xFFC8E6C9),
        ),

        // ---------- Teal Noir (কালো key, teal wave background) ----------
        KeyboardTheme(
            id = "teal_noir", name = "Teal Noir", emoji = "🌊", style = KeyStyle.FLAT, dark = true,
            bg = c(0xFFE8ECEF), bgAsset = "teal_bg",
            key = c(0xFF000000), fn = c(0xFF37474F), shadow = c(0x00000000),
            text = c(0xFFFFFFFF), hint = c(0xFFB0BEC5), press = c(0xFF455A64), accent = c(0xFF1E73E8),
            popup = c(0xFF111111), border = c(0xFF2A2A2A), borderDp = 0.8f, radiusDp = 9f, shadowDp = 0f,
            pill = c(0xFF000000), pillStroke = c(0xFF37474F), badgeBg = c(0xFF0D2B45),
            badgeStroke = c(0xFF1E73E8), badgeText = c(0xFF90CAF9), actionBg = c(0xFF37474F),
        ),

        // ---------- Manga Ink (সাদা-কালো manga tile key) ----------
        KeyboardTheme(
            id = "manga_ink", name = "Manga Ink", emoji = "✒️", style = KeyStyle.TILE, dark = false,
            bg = c(0xFFFAFAFA), bgAsset = "manga_bg",
            keyTiles = listOf("manga_t1", "manga_t2", "manga_t3", "manga_t4", "manga_t5", "manga_t6", "manga_t7", "manga_t8"),
            tileScrim = c(0x73FFFFFF),
            key = c(0xFFFFFFFF), fn = c(0xFFE0E0E0), shadow = c(0xFF111111),
            text = c(0xFF111111), hint = c(0xFF555555), press = c(0xFFBDBDBD), accent = c(0xFFD32F2F),
            popup = c(0xFFFFFFFF), border = c(0xFF111111), borderDp = 1.4f, radiusDp = 8f, shadowDp = 1.8f,
            pill = c(0xFFFFFFFF), pillStroke = c(0xFF111111), badgeBg = c(0xFFFFEBEE),
            badgeStroke = c(0xFFD32F2F), badgeText = c(0xFFB71C1C), actionBg = c(0xFFE0E0E0),
        ),

        // ---------- Violet Eye (কালি-তুলির background, বেগুনি চোখ) ----------
        KeyboardTheme(
            id = "violet_ink", name = "Violet Eye", emoji = "👁️", style = KeyStyle.GLASS, dark = false,
            bg = c(0xFFEDEDED), bgAsset = "ink_eye_bg",
            key = c(0xCCFFFFFF), fn = c(0xB3D8D4E8), shadow = c(0x00000000),
            text = c(0xFF15151A), hint = c(0xFF5B5470), press = c(0xFFD1C4E9), accent = c(0xFF5E35B1),
            popup = c(0xFFFFFFFF), border = c(0x55000000), borderDp = 0.8f, radiusDp = 10f, shadowDp = 0f,
            pill = c(0xCCFFFFFF), pillStroke = c(0x55000000), badgeBg = c(0xFFEDE7F6),
            badgeStroke = c(0xFF5E35B1), badgeText = c(0xFF4527A0), actionBg = c(0xB3D8D4E8),
        ),

        // ---------- নিজের ছবি ----------
        KeyboardTheme(
            id = CUSTOM_ID, name = "My Photo", emoji = "🖼️", style = KeyStyle.GLASS, dark = true,
            bg = c(0xFF202028), key = c(0x80000000), fn = c(0x99000000), shadow = c(0x33000000),
            text = c(0xFFFFFFFF), hint = c(0xFFE0E0E0), press = c(0xB3FFFFFF), accent = c(0xFF26A69A),
            popup = c(0xFF303038), border = c(0x33FFFFFF), borderDp = 0.8f, radiusDp = 10f, shadowDp = 0f,
            usesImage = true,
            pill = c(0x80000000), pillStroke = c(0x33FFFFFF), badgeBg = c(0xAA163C38),
            badgeStroke = c(0xFF26A69A), badgeText = c(0xFF80CBC4), actionBg = c(0x99000000),
        ),
    )

    fun byId(id: String?): KeyboardTheme? = all.firstOrNull { it.id == id }
}

/** Theme-এর সব preference ও custom ছবির সংরক্ষণ। "bk" prefs-এ থাকে (keyboard আর app একই prefs পড়ে)। */
object ThemeStore {
    private const val PREFS = "bk"
    private const val KEY_THEME = "theme"
    private const val KEY_IMG_ALPHA = "theme_img_alpha"
    private const val KEY_IMG_DIM = "theme_img_dim"
    private const val CUSTOM_FILE = "kb_theme_bg.jpg"

    const val MIN_ALPHA = 15
    const val MAX_ALPHA = 90

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selectedId(ctx: Context): String = prefs(ctx).getString(KEY_THEME, Themes.AUTO_ID) ?: Themes.AUTO_ID

    fun setSelectedId(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_THEME, id).apply()
    }

    /** Key-র opacity (%) — শুধু My Photo theme-এ কাজ করে */
    fun imageAlpha(ctx: Context): Int =
        prefs(ctx).getInt(KEY_IMG_ALPHA, 45).coerceIn(MIN_ALPHA, MAX_ALPHA)

    fun setImageAlpha(ctx: Context, v: Int) {
        prefs(ctx).edit().putInt(KEY_IMG_ALPHA, v.coerceIn(MIN_ALPHA, MAX_ALPHA)).apply()
    }

    /** ছবির উপর অন্ধকার আবরণ (%) — লেখা পড়তে সুবিধা */
    fun imageDim(ctx: Context): Int = prefs(ctx).getInt(KEY_IMG_DIM, 25).coerceIn(0, 70)

    fun setImageDim(ctx: Context, v: Int) {
        prefs(ctx).edit().putInt(KEY_IMG_DIM, v.coerceIn(0, 70)).apply()
    }

    /** বর্তমান theme। "System" হলে ফোনের dark/light অনুযায়ী default। */
    fun resolve(ctx: Context): KeyboardTheme {
        val id = selectedId(ctx)
        if (id == Themes.AUTO_ID) {
            val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return if (night) Themes.darkDefault else Themes.lightDefault
        }
        val t = Themes.byId(id) ?: return Themes.lightDefault
        // ছবি না থাকলে My Photo theme-এর বদলে default
        if (t.usesImage && !hasCustomImage(ctx)) return Themes.darkDefault
        return t
    }

    private val assetCache = HashMap<String, Bitmap>()

    /** ThemeAssets-এর base64 ছবি decode করে (একবারই, তারপর cache থেকে)। */
    fun assetBitmap(name: String): Bitmap? = synchronized(assetCache) {
        assetCache[name] ?: try {
            val b64 = ThemeAssets.get(name) ?: return null
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { assetCache[name] = it }
        } catch (e: Exception) {
            null
        }
    }

    private fun customFile(ctx: Context) = File(ctx.filesDir, CUSTOM_FILE)

    fun hasCustomImage(ctx: Context): Boolean = customFile(ctx).exists()

    fun customImageStamp(ctx: Context): Long = customFile(ctx).let { if (it.exists()) it.lastModified() else 0L }

    /** Gallery থেকে নেওয়া ছবি ছোট করে app-এর নিজস্ব folder-এ রাখে (max ১২৮০px)। */
    fun saveCustomImage(ctx: Context, uri: Uri): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return false

            val longest = max(raw.width, raw.height)
            val bmp = if (longest > 1280) {
                val s = 1280f / longest
                Bitmap.createScaledBitmap(raw, (raw.width * s).toInt().coerceAtLeast(1), (raw.height * s).toInt().coerceAtLeast(1), true)
                    .also { if (it !== raw) raw.recycle() }
            } else raw

            customFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearCustomImage(ctx: Context) {
        customFile(ctx).delete()
    }

    fun loadCustomImage(ctx: Context): Bitmap? {
        val f = customFile(ctx)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: Exception) {
            null
        }
    }
}
