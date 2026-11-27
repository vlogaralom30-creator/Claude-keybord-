package com.banglakeys.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * নতুন ব্যবহারকারী কোনো ফিচার (Secret, Voice, Assistant, Translate, Theme) চাপলে
 * প্রয়োজনীয় সেটিংস বা পারমিশন না থাকলে কী করতে হবে তা স্পষ্টভাবে দেখানোর গাইড।
 */
object SetupGuidance {

    enum class GuideAction {
        OPEN_SECRET_SETTINGS,
        OPEN_ASSISTANT_SETUP,
        REQUEST_VOICE_PERMISSION,
        OPEN_APP_SETTINGS,
        OPEN_VOICE_SETTINGS,
        OPEN_WIFI_SETTINGS,
        OPEN_IME_SETTINGS,
        EXIT_SECRET_MODE,
        NONE,
    }

    data class SetupGuide(
        val id: String,
        val title: String,
        val steps: String,
        val actionLabel: String? = null,
        val action: GuideAction = GuideAction.NONE,
        val durationMs: Long = GUIDE_AUTO_HIDE_MS,
    )

    const val GUIDE_AUTO_HIDE_MS = 12_000L

    fun secretGuide(ctx: Context): SetupGuide? {
        if (SecretConfig.getContacts(ctx).isNotEmpty()) return null
        return SetupGuide(
            id = "secret_setup",
            title = "🔐 Secret Message সেটআপ বাকি",
            steps = "১. নিচের 'সেটআপ খুলুন' চেপে Contact নাম ও Passphrase তৈরি করুন।\n" +
                "২. বন্ধুকে কোড পাঠান, তারপর 🔐 চেপে গোপন মেসেজ লিখুন।",
            actionLabel = "⚙️ সেটআপ খুলুন",
            action = GuideAction.OPEN_SECRET_SETTINGS,
        )
    }

    fun assistantGuide(ctx: Context): SetupGuide? {
        if (AssistantSetup.isReady(ctx)) return null
        val missing = ArrayList<String>()
        if (!AssistantSetup.isAccessibilityOn(ctx)) {
            missing.add("Accessibility-তে EMO Robot চালু করুন")
        }
        if (!AssistantSetup.hasMic(ctx)) {
            missing.add("Microphone permission দিন")
        }
        val overlayHint = if (!AssistantSetup.canOverlay(ctx)) " (ভাসমান EMO বাবলের জন্য Overlay দিতে পারেন)" else ""
        return SetupGuide(
            id = "assistant_setup",
            title = "🤖 EMO সেটআপ বাকি",
            steps = "যা করতে হবে: ${missing.joinToString(" → ")}।$overlayHint নিচের বোতাম চেপে EMO সেটআপ করুন।",
            actionLabel = "⚙️ EMO সেটআপ খুলুন",
            action = GuideAction.OPEN_ASSISTANT_SETUP,
        )
    }

    fun voiceUnavailableGuide(): SetupGuide = SetupGuide(
        id = "voice_unavailable",
        title = "🎤 Voice Typing পাওয়া যাচ্ছে না",
        steps = "ফোনে Google app বা Speech Services চালু/আপডেট করুন, তারপর আবার 🎤 চাপুন।",
        actionLabel = "⚙️ Voice Settings",
        action = GuideAction.OPEN_VOICE_SETTINGS,
    )

    fun voicePermissionGuide(permanentlyDenied: Boolean): SetupGuide = if (permanentlyDenied) {
        SetupGuide(
            id = "voice_perm_blocked",
            title = "🎤 মাইক্রোফোন Permission বন্ধ আছে",
            steps = "ভয়েস টাইপিং চালাতে App Info → Permissions → Microphone → Allow করুন।",
            actionLabel = "⚙️ App Settings",
            action = GuideAction.OPEN_APP_SETTINGS,
        )
    } else {
        SetupGuide(
            id = "voice_perm_needed",
            title = "🎤 মাইক্রোফোন Permission দরকার",
            steps = "ভয়েস টাইপিং ব্যবহার করতে নিচের বোতামে চেপে Microphone অনুমতি দিন।",
            actionLabel = "🔓 অনুমতি দিন",
            action = GuideAction.REQUEST_VOICE_PERMISSION,
        )
    }

    fun voiceSecretConflictGuide(): SetupGuide = SetupGuide(
        id = "voice_secret_conflict",
        title = "🔒 Secret Mode চালু আছে",
        steps = "ভয়েস টাইপিং চালাতে আগে Secret Mode বন্ধ করুন।",
        actionLabel = "Secret বন্ধ",
        action = GuideAction.EXIT_SECRET_MODE,
    )

    fun voiceRuntimeErrorGuide(errorMessage: String): SetupGuide? = when {
        errorMessage.contains("ইন্টারনেট") -> SetupGuide(
            id = "voice_offline",
            title = "📶 ইন্টারনেট দরকার",
            steps = "Voice typing চালাতে Wi-Fi বা Mobile Data চালু করে আবার 🎤 চাপুন।",
            actionLabel = "📶 Net Settings",
            action = GuideAction.OPEN_WIFI_SETTINGS,
        )
        errorMessage.contains("ডাউনলোড") || errorMessage.contains("ভাষার") -> SetupGuide(
            id = "voice_lang_pack",
            title = "🌐 বাংলা Voice প্যাক দরকার",
            steps = "Voice Settings থেকে বাংলা ভাষা ডাউনলোড করুন অথবা ইন্টারনেট চালু রাখুন।",
            actionLabel = "⚙️ Voice Settings",
            action = GuideAction.OPEN_VOICE_SETTINGS,
        )
        errorMessage.contains("permission", ignoreCase = true) -> voicePermissionGuide(permanentlyDenied = true)
        else -> null
    }

    fun previewVoiceGuide(ctx: Context, imeActive: Boolean): SetupGuide? {
        if (!VoiceInput(ctx).isAvailable()) return voiceUnavailableGuide()
        val hasMic = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            val blocked = ctx.getSharedPreferences("bk", Context.MODE_PRIVATE)
                .getBoolean("voice_perm_blocked", false)
            return voicePermissionGuide(blocked)
        }
        if (!imeActive) {
            return SetupGuide(
                id = "voice_enable_ime",
                title = "⌨️ আগে সিস্টেম কিবোর্ড চালু করুন",
                steps = "১. নিচের 'Keyboard enable' ও ২. 'EMO Keys Select' করুন। তারপর যেকোনো অ্যাপে 🎤 চেপে কথা বলুন।",
                actionLabel = "⚙️ চালু করুন",
                action = GuideAction.OPEN_IME_SETTINGS,
            )
        }
        return SetupGuide(
            id = "voice_ready_tip",
            title = "🎤 Voice Typing প্রস্তুত",
            steps = "যেকোনো মেসেজ বা সার্চ বক্সে কিবোর্ড খুলে 🎤 চাপলেই কথা বাংলা/ইংরেজিতে লেখা হবে।",
            actionLabel = null,
            action = GuideAction.NONE,
            durationMs = 8_000L,
        )
    }

    fun translateEmptyGuide(): SetupGuide = SetupGuide(
        id = "translate_empty",
        title = "🌐 অনুবাদ ব্যবহারের নিয়ম",
        steps = "১. আগে বাংলা বা ইংরেজিতে কিছু লিখুন (অথবা লেখা সিলেক্ট করুন)।\n" +
            "২. এরপর আবার ⇄ চাপলেই সাথে সাথে অনুবাদ হবে।",
        actionLabel = null,
        action = GuideAction.NONE,
        durationMs = 10_000L,
    )

    fun translateFailGuide(fail: Translator.Outcome.Fail): SetupGuide = when (fail.reason) {
        Translator.Reason.OFFLINE -> SetupGuide(
            id = "translate_offline",
            title = "📶 অনুবাদের জন্য ইন্টারনেট নেই",
            steps = "Wi-Fi বা Mobile Data চালু করে আবার ⇄ বাটনে চাপুন।",
            actionLabel = "📶 Net Settings",
            action = GuideAction.OPEN_WIFI_SETTINGS,
        )
        else -> SetupGuide(
            id = "translate_error",
            title = "⚠️ অনুবাদ করা যায়নি",
            steps = "${fail.reason.messageBn}। লেখা একটু ছোট করে বা আবার চেষ্টা করুন।",
            actionLabel = null,
            action = GuideAction.NONE,
            durationMs = 9_000L,
        )
    }

    fun openAction(ctx: Context, action: GuideAction, fromService: Boolean = false): Boolean {
        val intent = when (action) {
            GuideAction.OPEN_SECRET_SETTINGS -> Intent(ctx, SecretComposeActivity::class.java)
            GuideAction.OPEN_ASSISTANT_SETUP -> Intent(ctx, AssistantActivity::class.java)
            GuideAction.REQUEST_VOICE_PERMISSION -> Intent(ctx, VoicePermissionActivity::class.java)
            GuideAction.OPEN_APP_SETTINGS -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${ctx.packageName}")
            )
            GuideAction.OPEN_VOICE_SETTINGS -> Intent("com.android.settings.VOICE_INPUT_SETTINGS")
            GuideAction.OPEN_WIFI_SETTINGS -> Intent(Settings.ACTION_WIFI_SETTINGS)
            GuideAction.OPEN_IME_SETTINGS -> Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            GuideAction.EXIT_SECRET_MODE, GuideAction.NONE -> return false
        }
        if (fromService) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                if (fromService) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                ctx.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
