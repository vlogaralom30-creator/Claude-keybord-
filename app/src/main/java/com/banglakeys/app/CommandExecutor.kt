package com.banglakeys.app

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings

/** Command পেলে আসল কাজটা করে এবং ছোট একটা feedback লেখা ফেরত দেয়। */
class CommandExecutor(private val appCtx: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val svc: AssistantAccessibilityService? get() = AssistantAccessibilityService.instance

    private val needA11y = "⚠️ Accessibility চালু করুন (BanglaKeys Assistant অ্যাপ খুলুন)"

    private fun withSvc(block: (AssistantAccessibilityService) -> String): String =
        svc?.let(block) ?: needA11y

    fun isAsync(cmd: Command): Boolean = when (cmd) {
        is Command.AppChat,
        is Command.AppCall,
        is Command.AppMessage,
        Command.TakePhoto -> true
        else -> false
    }

    suspend fun executeAsync(
        cmd: Command,
        onProgress: (String) -> Unit = {}
    ): String {
        val result = when (cmd) {
            is Command.AppChat -> {
                val flow = cmd.appName?.let { AppFlows.find(it) }
                if (cmd.appName != null && flow == null) {
                    "❌ '${cmd.appName}' অ্যাপটি AppFlows-এ নেই"
                } else {
                    FlowRunner(appCtx).runAppChat(flow, cmd.text, cmd.send, onProgress)
                }
            }
            is Command.AppCall -> {
                val flow = AppFlows.find(cmd.appName)
                    ?: return "❌ '${cmd.appName}' অ্যাপটি AppFlows-এ নেই"
                FlowRunner(appCtx).runAppCall(flow, cmd.contact, cmd.video, onProgress)
            }
            is Command.AppMessage -> {
                val flow = AppFlows.find(cmd.appName)
                    ?: return "❌ '${cmd.appName}' অ্যাপটি AppFlows-এ নেই"
                FlowRunner(appCtx).runAppMessage(flow, cmd.contact, cmd.text, cmd.send, onProgress)
            }
            Command.TakePhoto -> {
                onProgress("ক্যামেরা খোলা হচ্ছে...")
                if (!openCameraIntent()) {
                    "❌ ক্যামেরা অ্যাপ খোলা যায়নি"
                } else {
                    FlowRunner(appCtx).tapShutterIfPresent()
                }
            }
            else -> execute(cmd)
        }
        AssistantLog.d("ExecutorAsync($cmd) -> $result")
        return result
    }

    fun execute(cmd: Command): String {
        val result = when (cmd) {
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
            Command.Sleep, Command.StopAssistant, Command.PanicStop -> "" // listener service সামলায়

            // Pack F — SAF Files & Folders
            is Command.CreateFolder -> createFolderSaf(cmd.name, cmd.parentFolder)
            is Command.CreateFile -> createFileSaf(cmd.name, cmd.ext, cmd.content)

            // Pack G & H synchronous fallback (normally dispatched via executeAsync)
            is Command.AppChat,
            is Command.AppCall,
            is Command.AppMessage -> "⏳ কাজ চলছে…"

            // Pack I — Everyday App Commands
            Command.OpenCamera -> if (openCameraIntent()) "📷 ক্যামেরা খোলা হয়েছে" else "❌ ক্যামেরা অ্যাপ খোলা যায়নি"
            Command.TakePhoto -> if (openCameraIntent()) "📷 ক্যামেরা খোলা হয়েছে" else "❌ ক্যামেরা অ্যাপ খোলা যায়নি"
            is Command.SetAlarm -> setAlarm(cmd)
            is Command.SetTimer -> setTimer(cmd)
            is Command.Navigate -> navigate(cmd.place)
            is Command.WebSearch -> webSearch(cmd.query)
            is Command.PlaySong -> playSong(cmd.query)
            is Command.SaveNote -> saveNote(cmd.text)
            is Command.WriteEmail -> writeEmail(cmd)
            is Command.WriteSms -> writeSms(cmd)
            is Command.OpenSystemSettings -> openSettings(cmd.target)
        }
        AssistantLog.d("Executor($cmd) -> $result")
        return result
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
        } catch (e: Exception) {
            AssistantLog.w("launch failed", e)
            false
        }
    }

    private fun openApp(name: String): String {
        // স্পষ্ট না হলে আন্দাজ না করে জিজ্ঞাসা করা
        AppResolver.ambiguousPrompt(name)?.let { return it }
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

    // ------------------------------------------------------------ Pack F (SAF DocumentsContract)

    private data class DocEntry(val docId: String, val name: String, val mime: String)

    private fun sanitizeFileName(raw: String): String {
        val stripped = raw
            .replace(Regex("[/\\\\:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (stripped.length > 80) stripped.take(80).trim() else stripped
    }

    private fun listChildren(treeUri: Uri, parentDocId: String): List<DocEntry> {
        val out = ArrayList<DocEntry>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        try {
            appCtx.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: ""
                    val mime = c.getString(2) ?: ""
                    out.add(DocEntry(id, name, mime))
                }
            }
        } catch (e: Exception) {
            AssistantLog.w("listChildren failed", e)
        }
        return out
    }

    private fun uniqueName(existingNames: Set<String>, base: String, ext: String?): String {
        val existingLower = existingNames.map { it.lowercase() }.toSet()
        if (ext == null) {
            var candidate = base
            var idx = 2
            while (candidate.lowercase() in existingLower && idx < 500) {
                candidate = "$base ($idx)"
                idx++
            }
            return candidate
        }
        val cleanExt = ext.lowercase()
        val stem = if (base.lowercase().endsWith(".$cleanExt")) {
            base.dropLast(cleanExt.length + 1).trim()
        } else base
        val safeStem = if (stem.isEmpty()) "file" else stem
        var candidate = "$safeStem.$cleanExt"
        var idx = 2
        while (candidate.lowercase() in existingLower && idx < 500) {
            candidate = "$safeStem ($idx).$cleanExt"
            idx++
        }
        return candidate
    }

    private fun requireStorageTreeUri(): Uri? {
        if (!AssistantSetup.hasStorageTree(appCtx)) {
            launch(Intent(appCtx, AssistantActivity::class.java))
            return null
        }
        return AssistantPrefs.storageTreeUri(appCtx)?.let { Uri.parse(it) }
    }

    private fun createFolderSaf(rawName: String, rawParent: String?): String {
        val treeUri = requireStorageTreeUri()
            ?: return "⚠️ আগে Assistant storage folder বেছে নিন"
        val cleanName = sanitizeFileName(rawName)
        if (cleanName.isBlank()) return "❌ ফোল্ডারের নাম সঠিক নয়"

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        var targetDocId = rootDocId
        if (!rawParent.isNullOrBlank()) {
            val cleanParent = sanitizeFileName(rawParent)
            val rootChildren = listChildren(treeUri, rootDocId)
            val hit = rootChildren.firstOrNull {
                it.mime == DocumentsContract.Document.MIME_TYPE_DIR &&
                    it.name.equals(cleanParent, ignoreCase = true)
            } ?: return "❌ '$cleanParent' ফোল্ডারটি storage folder-এ পাইনি"
            targetDocId = hit.docId
        }

        val children = listChildren(treeUri, targetDocId)
        val displayName = uniqueName(children.map { it.name }.toSet(), cleanName, null)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetDocId)
        return try {
            val created = DocumentsContract.createDocument(
                appCtx.contentResolver,
                parentUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                displayName
            )
            if (created != null) "📁 $displayName তৈরি হয়েছে"
            else "❌ ফোল্ডার তৈরি করা যায়নি"
        } catch (e: Exception) {
            AssistantLog.w("createFolderSaf failed", e)
            "❌ ফোল্ডার তৈরি করা যায়নি"
        }
    }

    private fun createFileSaf(rawName: String, ext: String, content: String?): String {
        val treeUri = requireStorageTreeUri()
            ?: return "⚠️ আগে Assistant storage folder বেছে নিন"
        val cleanName = sanitizeFileName(rawName)
        if (cleanName.isBlank()) return "❌ ফাইলের নাম সঠিক নয়"

        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = listChildren(treeUri, rootDocId)
        val displayName = uniqueName(children.map { it.name }.toSet(), cleanName, ext)
        val mime = when (ext.lowercase()) {
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "json" -> "application/json"
            else -> "text/plain"
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        return try {
            val created = DocumentsContract.createDocument(
                appCtx.contentResolver,
                parentUri,
                mime,
                displayName
            ) ?: return "❌ ফাইল তৈরি করা যায়নি"

            if (!content.isNullOrEmpty()) {
                appCtx.contentResolver.openOutputStream(created)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                }
            }
            "📄 $displayName তৈরি হয়েছে"
        } catch (e: Exception) {
            AssistantLog.w("createFileSaf failed", e)
            "❌ ফাইল তৈরি করা যায়নি"
        }
    }

    // ------------------------------------------------------------ Pack I (Intents + Notes.kt)

    private fun openCameraIntent(): Boolean {
        return launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE)) ||
            launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) ||
            AppResolver.launchIntent(appCtx, "camera")?.let { launch(it) } ?: false
    }

    private fun setAlarm(cmd: Command.SetAlarm): String {
        if (!AssistantSetup.hasSetAlarm(appCtx)) {
            return "⚠️ SET_ALARM permission নেই"
        }
        val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, cmd.hour24)
            putExtra(AlarmClock.EXTRA_MINUTES, cmd.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (!cmd.label.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, cmd.label)
        }
        val timeText = String.format("%02d:%02d", cmd.hour24, cmd.minute)
        return if (launch(i)) "⏰ $timeText-এ alarm সেট হয়েছে" else "❌ Alarm অ্যাপ পাইনি"
    }

    private fun setTimer(cmd: Command.SetTimer): String {
        if (!AssistantSetup.hasSetAlarm(appCtx)) {
            return "⚠️ SET_ALARM permission নেই"
        }
        val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, cmd.seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (!cmd.label.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, cmd.label)
        }
        return if (launch(i)) "⏱ ${cmd.seconds} সেকেন্ডের timer সেট হয়েছে" else "❌ Timer অ্যাপ পাইনি"
    }

    private fun navigate(place: String): String {
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(place))
        val mapsIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        val genericIntent = Intent(Intent.ACTION_VIEW, uri)
        return if (launch(mapsIntent) || launch(genericIntent)) {
            "🗺️ $place-এর দিকনির্দেশনা খোলা হয়েছে"
        } else {
            "❌ ম্যাপ অ্যাপ পাইনি"
        }
    }

    private fun webSearch(query: String): String {
        val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
        }
        val browserFallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        )
        return if (launch(webSearchIntent) || launch(browserFallback)) {
            "🔍 Google-এ খোঁজা হচ্ছে: $query"
        } else {
            "❌ সার্চ বা ব্রাউজার অ্যাপ পাইনি"
        }
    }

    private fun playSong(query: String): String {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        }
        return if (launch(i)) "🎵 চালানো হচ্ছে: $query" else "❌ মিউজিক অ্যাপ পাইনি"
    }

    private fun saveNote(text: String): String {
        Notes(appCtx).add(text)
        return "📝 নোট সেভ হয়েছে: $text"
    }

    private fun writeEmail(cmd: Command.WriteEmail): String {
        val address = when {
            cmd.recipient.contains("@") -> cmd.recipient
            else -> ContactLookup.findEmail(appCtx, cmd.recipient) ?: ""
        }
        val uri = Uri.parse("mailto:" + Uri.encode(address))
        val i = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (cmd.subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, cmd.subject)
            if (cmd.body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, cmd.body)
        }
        return if (launch(i)) {
            "✉️ ইমেইল লেখা হয়েছে — নিজে দেখে Send চাপুন"
        } else {
            "❌ ইমেইল অ্যাপ পাইনি"
        }
    }

    private fun writeSms(cmd: Command.WriteSms): String {
        val onlyDigits = cmd.recipient.none { it.isLetter() } && cmd.recipient.count { it.isDigit() } >= 3
        val number = when {
            onlyDigits -> cmd.recipient.filter { it.isDigit() || it == '+' }
            else -> ContactLookup.findNumber(appCtx, cmd.recipient) ?: ""
        }
        val uri = Uri.parse("smsto:" + Uri.encode(number))
        val i = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (cmd.text.isNotBlank()) putExtra("sms_body", cmd.text)
        }
        return if (launch(i)) {
            "💬 SMS লেখা হয়েছে — নিজে দেখে Send চাপুন"
        } else {
            "❌ SMS অ্যাপ পাইনি"
        }
    }

    private fun openSettings(target: SettingsTarget): String {
        val ok = when (target) {
            SettingsTarget.WIFI -> {
                if (Build.VERSION.SDK_INT >= 29) {
                    launch(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) || launch(Intent(Settings.ACTION_WIFI_SETTINGS))
                } else {
                    launch(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            }
            SettingsTarget.BLUETOOTH -> launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            SettingsTarget.DISPLAY -> launch(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            SettingsTarget.SOUND -> launch(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
        val label = when (target) {
            SettingsTarget.WIFI -> "Wi-Fi"
            SettingsTarget.BLUETOOTH -> "Bluetooth"
            SettingsTarget.DISPLAY -> "Display"
            SettingsTarget.SOUND -> "Sound"
        }
        return if (ok) "⚙️ $label settings খোলা হয়েছে" else "❌ $label settings খোলা যায়নি"
    }
}
