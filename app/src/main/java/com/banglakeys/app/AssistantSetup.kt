package com.banglakeys.app

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Permission / সেটিং চেক করার ছোট helper-গুলো। */
object AssistantSetup {

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(AssistantConfig.PREFS, Context.MODE_PRIVATE)

    fun isBangla(ctx: Context): Boolean = prefs(ctx).getBoolean(AssistantConfig.KEY_BANGLA, false)

    fun setBangla(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(AssistantConfig.KEY_BANGLA, value).apply()
    }

    fun isAccessibilityOn(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(ctx, AssistantAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun has(ctx: Context, permission: String): Boolean =
        ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun hasMic(ctx: Context): Boolean = has(ctx, android.Manifest.permission.RECORD_AUDIO)

    fun canOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** Assistant চালানোর ন্যূনতম শর্ত: Accessibility ON + Mic permission। */
    fun isReady(ctx: Context): Boolean = isAccessibilityOn(ctx) && hasMic(ctx)

    fun runtimePermissions(): Array<String> {
        val list = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= 33) list.add("android.permission.POST_NOTIFICATIONS")
        return list.toTypedArray()
    }
}
