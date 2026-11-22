package com.banglakeys.app

import android.content.Context
import com.example.BuildConfig

/**
 * Gemini API key রান-টাইমে (app-এর settings screen থেকে অথবা AI Studio Secrets /
 * BuildConfig.GEMINI_API_KEY থেকে) সেভ/পড়ার জন্য।
 *
 * ⚠️ key কখনো source code-এ hardcode করবেন না — GitHub-এ commit হয়ে গেলে সেটা
 * সাথে সাথে leak হয়ে যায়। ইউজার নিজের key সেট করলে তা ফোনের local
 * SharedPreferences-এ থাকে, নাহলে BuildConfig.GEMINI_API_KEY (Secrets panel থেকে
 * inject হওয়া) ব্যবহার করে।
 */
object GeminiConfig {
    private const val KEY = "gemini_api_key"
    private const val ENV_PLACEHOLDER = "MY_GEMINI_API_KEY"

    const val MODEL = "gemini-3.8-flash"

    enum class KeySource {
        USER_SETTINGS,
        BUILD_CONFIG,
        NONE
    }

    fun getUserKey(ctx: Context): String =
        ctx.getSharedPreferences(AssistantConfig.PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "")
            ?.trim()
            ?: ""

    fun getEnvKey(): String {
        val raw = runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("").trim()
        return if (raw.isBlank() || raw == ENV_PLACEHOLDER) "" else raw
    }

    fun getKey(ctx: Context): String {
        val user = getUserKey(ctx)
        if (user.isNotBlank()) return user
        return getEnvKey()
    }

    fun keySource(ctx: Context): KeySource = when {
        getUserKey(ctx).isNotBlank() -> KeySource.USER_SETTINGS
        getEnvKey().isNotBlank() -> KeySource.BUILD_CONFIG
        else -> KeySource.NONE
    }

    fun setKey(ctx: Context, key: String) {
        val clean = key.trim()
        ctx.getSharedPreferences(AssistantConfig.PREFS, Context.MODE_PRIVATE)
            .edit().apply {
                if (clean.isEmpty()) remove(KEY) else putString(KEY, clean)
            }.apply()
    }

    fun hasKey(ctx: Context): Boolean = getKey(ctx).isNotBlank()

    /** Google AI Studio Gemini API key সাধারণত "AIza" দিয়ে শুরু হয়। */
    fun looksLikeStandardGeminiKey(key: String): Boolean =
        key.trim().startsWith("AIza") && key.trim().length >= 20
}
