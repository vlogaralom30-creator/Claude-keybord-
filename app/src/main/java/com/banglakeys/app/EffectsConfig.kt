package com.banglakeys.app

import android.content.Context

/**
 * Key effects-এর সেটিং + kill switch।
 *
 * Rollback ১ (সবচেয়ে দ্রুত): নিচের [MASTER_ENABLED] = false করুন → effects কোথাও চলবে না, কোড বাকি সব আগের মতো।
 * Rollback ২: Settings-এ "Key effects" বন্ধ করুন।
 * Rollback ৩: docs/fx-backup/ এর .orig ফাইলগুলো ফিরিয়ে দিন (docs/FX-ROLLBACK.md দেখুন)।
 */
object EffectsConfig {
    /** false = পুরো feature বন্ধ (build-time kill switch) */
    const val MASTER_ENABLED = true

    /** 0 বন্ধ · 1 Light · 2 Normal · 3 Rich। Default Light — typing-এর উপর প্রভাব সবচেয়ে কম। */
    const val DEFAULT_LEVEL = 1

    private const val PREFS = "fx_prefs"
    private const val K_LEVEL = "level"
    private const val K_AUTO_OFF = "auto_off"
    private const val K_REASON = "auto_off_reason"

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun clampLevel(v: Int) = v.coerceIn(0, 3)

    /** বাস্তবে প্রযোজ্য level (guard বা kill switch চালু থাকলে 0)। */
    fun level(ctx: Context): Int {
        if (!MASTER_ENABLED) return 0
        val sp = p(ctx)
        if (sp.getBoolean(K_AUTO_OFF, false)) return 0
        return clampLevel(sp.getInt(K_LEVEL, DEFAULT_LEVEL))
    }

    /** ব্যবহারকারীর বেছে নেওয়া level (guard নির্বিশেষে)। */
    fun chosenLevel(ctx: Context): Int = clampLevel(p(ctx).getInt(K_LEVEL, DEFAULT_LEVEL))

    fun setLevel(ctx: Context, level: Int) {
        p(ctx).edit().putInt(K_LEVEL, clampLevel(level)).apply()
    }

    fun autoDisabled(ctx: Context) = p(ctx).getBoolean(K_AUTO_OFF, false)
    fun autoDisabledReason(ctx: Context): String = p(ctx).getString(K_REASON, "") ?: ""

    /** Performance guard ধরলে নিজে বন্ধ হয়; ব্যবহারকারী Settings থেকে আবার চালু করলে [resetGuard]। */
    fun autoDisable(ctx: Context, reason: String) {
        p(ctx).edit().putBoolean(K_AUTO_OFF, true).putString(K_REASON, reason).apply()
    }

    fun resetGuard(ctx: Context) {
        p(ctx).edit().putBoolean(K_AUTO_OFF, false).remove(K_REASON).apply()
    }
}
