package com.banglakeys.app

import android.content.Context
import android.content.SharedPreferences

enum class ListeningMode {
    CONTINUOUS,
    PUSH_TO_TALK
}

enum class OverlayEdge {
    LEFT,
    RIGHT
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
    private const val KEY_OVERLAY_EDGE = "overlay_edge"
    private const val KEY_OVERLAY_Y_RATIO = "overlay_y_ratio"
    private const val KEY_AD_SKIP_ENABLED = "ad_skip_enabled"
    private const val KEY_AD_SKIP_GAMES_ENABLED = "ad_skip_games_enabled"
    private const val KEY_AD_SKIP_COUNT = "ad_skip_count"
    private const val KEY_AD_SKIP_LAST = "ad_skip_last"
    private const val KEY_SCROLL_CONTENT_DIR = "scroll_by_content_direction"
    private const val KEY_EMO_BODY_SENSORS_CENTER = "emo_body_sensors_in_center"
    private const val KEY_EMO_VOICE_SFX_ENABLED = "emo_voice_sfx_enabled"
    private const val KEY_EMO_HAPTICS_ENABLED = "emo_haptics_enabled"
    private const val KEY_EMO_CAMERA_EYES_ENABLED = "emo_camera_eyes_enabled"
    private const val KEY_EMO_HD_EYES_ENABLED = "emo_hd_eyes_enabled"
    private const val KEY_EMO_BODY_SKIN = "emo_body_skin"
    private const val KEY_EMO_VISUAL_MODE = "emo_visual_mode"

    const val MIN_FLOW_TIMEOUT_SEC = 2
    const val MAX_FLOW_TIMEOUT_SEC = 15
    const val DEFAULT_FLOW_TIMEOUT_SEC = 6
    const val DEFAULT_OVERLAY_Y_RATIO = 0.34f
    const val MIN_OVERLAY_Y_RATIO = 0.12f
    const val MAX_OVERLAY_Y_RATIO = 0.78f

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(AssistantConfig.PREFS, Context.MODE_PRIVATE)

    fun scrollByContentDirection(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SCROLL_CONTENT_DIR, true)

    fun setScrollByContentDirection(ctx: Context, byContent: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SCROLL_CONTENT_DIR, byContent).apply()
    }

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

    fun overlayEdge(ctx: Context): OverlayEdge {
        val raw = prefs(ctx).getString(KEY_OVERLAY_EDGE, OverlayEdge.RIGHT.name)
        return OverlayEdge.values().firstOrNull { it.name == raw } ?: OverlayEdge.RIGHT
    }

    fun setOverlayEdge(ctx: Context, edge: OverlayEdge) {
        prefs(ctx).edit().putString(KEY_OVERLAY_EDGE, edge.name).apply()
    }

    fun overlayYRatio(ctx: Context): Float =
        prefs(ctx).getFloat(KEY_OVERLAY_Y_RATIO, DEFAULT_OVERLAY_Y_RATIO)
            .coerceIn(MIN_OVERLAY_Y_RATIO, MAX_OVERLAY_Y_RATIO)

    fun setOverlayYRatio(ctx: Context, ratio: Float) {
        prefs(ctx).edit()
            .putFloat(KEY_OVERLAY_Y_RATIO, ratio.coerceIn(MIN_OVERLAY_Y_RATIO, MAX_OVERLAY_Y_RATIO))
            .apply()
    }

    fun resetOverlayPosition(ctx: Context) {
        prefs(ctx).edit()
            .putString(KEY_OVERLAY_EDGE, OverlayEdge.RIGHT.name)
            .putFloat(KEY_OVERLAY_Y_RATIO, DEFAULT_OVERLAY_Y_RATIO)
            .apply()
    }

    fun adSkipEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AD_SKIP_ENABLED, true)

    fun setAdSkipEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AD_SKIP_ENABLED, enabled).apply()
    }

    fun adSkipGamesEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AD_SKIP_GAMES_ENABLED, true)

    fun setAdSkipGamesEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AD_SKIP_GAMES_ENABLED, enabled).apply()
    }

    fun adSkipCount(ctx: Context): Int =
        prefs(ctx).getInt(KEY_AD_SKIP_COUNT, 0).coerceAtLeast(0)

    fun lastAdSkipInfo(ctx: Context): String? =
        prefs(ctx).getString(KEY_AD_SKIP_LAST, null)?.takeIf { it.isNotBlank() }

    fun recordAdSkip(ctx: Context, label: String) {
        val next = adSkipCount(ctx) + 1
        prefs(ctx).edit()
            .putInt(KEY_AD_SKIP_COUNT, next)
            .putString(KEY_AD_SKIP_LAST, label.take(64))
            .apply()
    }

    fun emoBodySensorsInCenter(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_EMO_BODY_SENSORS_CENTER, false)

    fun setEmoBodySensorsInCenter(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_EMO_BODY_SENSORS_CENTER, enabled).apply()
    }

    fun emoVoiceSfxEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_EMO_VOICE_SFX_ENABLED, true)

    fun setEmoVoiceSfxEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_EMO_VOICE_SFX_ENABLED, enabled).apply()
    }

    fun emoHapticsEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_EMO_HAPTICS_ENABLED, true)

    fun setEmoHapticsEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_EMO_HAPTICS_ENABLED, enabled).apply()
    }

    fun emoCameraEyesEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_EMO_CAMERA_EYES_ENABLED, false)

    fun setEmoCameraEyesEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_EMO_CAMERA_EYES_ENABLED, enabled).apply()
    }

    fun emoHdEyesEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_EMO_HD_EYES_ENABLED, true)

    fun setEmoHdEyesEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_EMO_HD_EYES_ENABLED, enabled).apply()
    }

    fun emoBodySkin(ctx: Context): com.banglakeys.app.robot.RobotBodySkin {
        val raw = prefs(ctx).getString(KEY_EMO_BODY_SKIN, com.banglakeys.app.robot.RobotBodySkin.LAVENDER_3D.name)
        return com.banglakeys.app.robot.RobotBodySkin.entries.firstOrNull { it.name == raw }
            ?: com.banglakeys.app.robot.RobotBodySkin.LAVENDER_3D
    }

    fun setEmoBodySkin(ctx: Context, skin: com.banglakeys.app.robot.RobotBodySkin) {
        prefs(ctx).edit().putString(KEY_EMO_BODY_SKIN, skin.name).apply()
    }

    fun emoVisualMode(ctx: Context): com.banglakeys.app.robot.EmoVisualMode {
        val raw = prefs(ctx).getString(KEY_EMO_VISUAL_MODE, com.banglakeys.app.robot.EmoVisualMode.AUTO_UNIFIED.name)
        return com.banglakeys.app.robot.EmoVisualMode.entries.firstOrNull { it.name == raw }
            ?: com.banglakeys.app.robot.EmoVisualMode.AUTO_UNIFIED
    }

    fun setEmoVisualMode(ctx: Context, mode: com.banglakeys.app.robot.EmoVisualMode) {
        prefs(ctx).edit().putString(KEY_EMO_VISUAL_MODE, mode.name).apply()
    }
}
