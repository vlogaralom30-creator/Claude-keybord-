package com.banglakeys.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper

/** Command পেলে আসল কাজটা করে এবং ছোট একটা feedback লেখা ফেরত দেয়। */
class CommandExecutor(private val appCtx: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val svc: AssistantAccessibilityService? get() = AssistantAccessibilityService.instance

    private val needA11y = "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"

    private fun withSvc(block: (AssistantAccessibilityService) -> String): String =
        svc?.let(block) ?: needA11y

    fun execute(cmd: Command): String = when (cmd) {
        is Command.OpenApp -> openApp(cmd.name)
        is Command.Call -> call(cmd.target)
        is Command.YoutubeSearch -> youtube(cmd)
        is Command.Swipe -> withSvc { swipe(it, cmd.dir) }
        is Command.ClickText -> withSvc {
            if (it.clickByText(cmd.text)) "👆 ${cmd.text}" else "'${cmd.text}' screen-এ পাইনি"
        }
        is Command.TypeText -> withSvc {
            if (it.typeText(cmd.text)) "⌨️ ${cmd.text}" else "কোনো লেখার বক্সে cursor নেই"
        }
        Command.Enter -> withSvc { if (it.pressEnter()) "↵ Enter" else "Enter চাপা গেল না" }
        Command.Like -> withSvc { like(it) }
        Command.PlayPause -> withSvc { playPause(it) }
        Command.DoubleTap -> withSvc { it.doubleTapCenter(); "👆👆 double tap" }
        Command.Tap -> withSvc { it.tapCenter(); "👆 tap" }
        Command.Back -> withSvc { it.back(); "◀️ back" }
        Command.Home -> withSvc { it.home(); "🏠 home" }
        Command.Recents -> withSvc { it.recents(); "🗂 recents" }
        Command.Notifications -> withSvc { it.notifications(); "🔔 notifications" }
        Command.QuickSettings -> withSvc { it.quickSettings(); "⚙️ quick settings" }
        Command.Lock -> withSvc { if (it.lockScreen()) "🔒 lock" else "Lock Android 9+ লাগে" }
        Command.Screenshot -> withSvc { if (it.screenshot()) "📸 screenshot" else "Screenshot Android 9+ লাগে" }
        Command.VolumeUp -> volume(AudioManager.ADJUST_RAISE, "🔊 volume up")
        Command.VolumeDown -> volume(AudioManager.ADJUST_LOWER, "🔉 volume down")
        Command.Sleep, Command.StopAssistant -> "" // listener service নিজে সামলায়
    }

    // ------------------------------------------------------------ helpers

    private fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            (svc ?: appCtx).startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private fun openApp(name: String): String {
        val i = AppResolver.launchIntent(appCtx, name) ?: return "'$name' নামে app পাইনি"
        return if (launch(i)) "📱 $name" else "'$name' খুলতে পারলাম না (Display over other apps permission দিন)"
    }

    private fun swipe(s: AssistantAccessibilityService, dir: Dir): String {
        val real = if (AssistantConfig.UP_MEANS_FINGER_UP) dir else when (dir) {
            Dir.UP -> Dir.DOWN
            Dir.DOWN -> Dir.UP
            else -> dir
        }
        s.swipe(real)
        return when (dir) {
            Dir.UP -> "⬆️ up"
            Dir.DOWN -> "⬇️ down"
            Dir.LEFT -> "⬅️ left"
            Dir.RIGHT -> "➡️ right"
        }
    }

    private fun like(s: AssistantAccessibilityService): String {
        val pkg = s.foregroundPackage()
        if (pkg in AssistantConfig.SHORT_VIDEO_APPS) {
            s.doubleTapCenter()
            return "❤️ like"
        }
        if (s.clickByText("like")) return "👍 like"
        s.doubleTapCenter()
        return "❤️ like (double tap)"
    }

    private fun playPause(s: AssistantAccessibilityService): String {
        val pkg = s.foregroundPackage()
        if (pkg !in AssistantConfig.SHORT_VIDEO_APPS) {
            if (s.clickByText("pause", exact = true) || s.clickByText("play", exact = true)) return "⏯ pause/play"
        }
        s.tapCenter()
        return "⏯ pause/play"
    }

    private fun volume(direction: Int, msg: String): String {
        val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return msg
    }

    private fun call(target: String): String {
        val onlyNumber = target.none { it.isLetter() } && target.count { it.isDigit() } >= 5
        val number: String = if (onlyNumber) {
            target.filter { it.isDigit() || it == '+' }
        } else {
            if (!AssistantSetup.has(appCtx, android.Manifest.permission.READ_CONTACTS)) {
                return "⚠️ Contacts permission দিন"
            }
            ContactLookup.findNumber(appCtx, target) ?: return "'$target' নামে contact পাইনি"
        }
        val uri = Uri.parse("tel:" + Uri.encode(number))
        val canCall = AssistantSetup.has(appCtx, android.Manifest.permission.CALL_PHONE)
        val i = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
        return if (launch(i)) "📞 $target" else "Call করা গেল না"
    }

    private fun youtube(cmd: Command.YoutubeSearch): String {
        val q = cmd.query
        val view = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))
        ).setPackage(AssistantConfig.YOUTUBE_PKG)
        val ok = launch(view) || launch(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))
            )
        )
        if (!ok) return "YouTube খুলতে পারলাম না"
        // Accessibility চালু থাকলে result লোড হওয়ার পর ভিডিওতে click
        if (svc != null) handler.postDelayed({ pickVideo(cmd.mostViewed, 0) }, 4000)
        return "▶️ YouTube: $q" + if (cmd.mostViewed) " (most viewed)" else ""
    }

    private fun pickVideo(mostViewed: Boolean, attempt: Int) {
        val s = svc ?: return
        val node = if (mostViewed) s.findMostViewedVideo() else s.findFirstVideo()
        if (node != null) {
            s.clickNode(node)
            return
        }
        if (attempt < 4) {
            handler.postDelayed({ pickVideo(mostViewed, attempt + 1) }, 1500)
        } else {
            android.widget.Toast.makeText(appCtx, "ভিডিও খুঁজে পাইনি", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
