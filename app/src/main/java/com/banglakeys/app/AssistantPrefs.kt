package com.banglakeys.app

import android.content.Context
import android.content.SharedPreferences

enum class ListeningMode {
    CONTINUOUS,
    PUSH_TO_TALK
}

/**
 * Assistant runtime preferences (backed by AssistantConfig.PREFS).
 */
object AssistantPrefs {

    private const val KEY_STORAGE_TREE_URI = "storage_tree_uri"
    private const val KEY_CONFIRM_APP_CALL = "confirm_before_app_call"
    private const val KEY_CONFIRM_HUMAN_SEND = "confirm_before_human_send"
    private const val KEY_CONFIRM_AI_CHAT_SEND = "confirm_before_ai_chat_send"
    private const val KEY_APP_FLOW_TIMEOUT_SEC = "app_flow_timeout_sec"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
    private const val KEY_LISTENING_MODE = "listening_mode"
    private const val KEY_OVERLAY_HUD_ENABLED = "overlay_hud_enabled"

    const val MIN_FLOW_TIMEOUT_SEC = 2
    const val MAX_FLOW_TIMEOUT_SEC = 15
    const val DEFAULT_FLOW_TIMEOUT_SEC = 6

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(AssistantConfig.PREFS, Context.MODE_PRIVATE)

    fun storageTreeUri(ctx: Context): String? =
        prefs(ctx).getString(KEY_STORAGE_TREE_URI, null)?.takeIf { it.isNotBlank() }

    fun setStorageTreeUri(ctx: Context, uri: String?) {
        prefs(ctx).edit().apply {
            if (uri.isNullOrBlank()) remove(KEY_STORAGE_TREE_URI)
            else putString(KEY_STORAGE_TREE_URI, uri)
        }.apply()
    }

    fun confirmBeforeAppCall(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CONFIRM_APP_CALL, true)

    fun setConfirmBeforeAppCall(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CONFIRM_APP_CALL, value).apply()
    }

    fun confirmBeforeHumanSend(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CONFIRM_HUMAN_SEND, true)

    fun setConfirmBeforeHumanSend(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CONFIRM_HUMAN_SEND, value).apply()
    }

    fun confirmBeforeAiChatSend(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CONFIRM_AI_CHAT_SEND, false)

    fun setConfirmBeforeAiChatSend(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CONFIRM_AI_CHAT_SEND, value).apply()
    }

    fun appFlowTimeoutSec(ctx: Context): Int =
        prefs(ctx).getInt(KEY_APP_FLOW_TIMEOUT_SEC, DEFAULT_FLOW_TIMEOUT_SEC)
            .coerceIn(MIN_FLOW_TIMEOUT_SEC, MAX_FLOW_TIMEOUT_SEC)

    fun setAppFlowTimeoutSec(ctx: Context, sec: Int) {
        prefs(ctx).edit()
            .putInt(KEY_APP_FLOW_TIMEOUT_SEC, sec.coerceIn(MIN_FLOW_TIMEOUT_SEC, MAX_FLOW_TIMEOUT_SEC))
            .apply()
    }

    fun appFlowTimeoutMs(ctx: Context): Long = appFlowTimeoutSec(ctx) * 1000L

    fun blockedPackages(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()

    fun setBlockedPackages(ctx: Context, pkgs: Set<String>) {
        prefs(ctx).edit().putStringSet(KEY_BLOCKED_PACKAGES, pkgs.toSet()).apply()
    }

    fun listeningMode(ctx: Context): ListeningMode {
        val raw = prefs(ctx).getString(KEY_LISTENING_MODE, ListeningMode.CONTINUOUS.name)
        return ListeningMode.values().firstOrNull { it.name == raw } ?: ListeningMode.CONTINUOUS
    }

    fun setListeningMode(ctx: Context, mode: ListeningMode) {
        prefs(ctx).edit().putString(KEY_LISTENING_MODE, mode.name).apply()
    }

    fun overlayHudEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_OVERLAY_HUD_ENABLED, true)

    fun setOverlayHudEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_OVERLAY_HUD_ENABLED, enabled).apply()
    }
}
