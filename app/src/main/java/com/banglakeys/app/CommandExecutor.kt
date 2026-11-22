package com.banglakeys.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast

/** Command পেলে আসল কাজটা করে এবং ছোট একটা feedback লেখা ফেরত দেয়। */
class CommandExecutor(private val appCtx: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val flowRunner = FlowRunner(appCtx)
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
        AssistantLog.d("CommandExecutor.executeAsync: $cmd")
        return when (cmd) {
            is Command.AppChat -> {
                val flow = cmd.appName?.let { AppFlows.find(it) }
                if (cmd.appName != null && flow == null) {
                    "❌ '${cmd.appName}' flow পাওয়া যায়নি"
                } else {
                    flowRunner.runAppChat(flow, cmd.text, cmd.send, onProgress)
                }
            }
            is Command.AppCall -> {
                val flow = AppFlows.find(cmd.appName)
                    ?: return "❌ '${cmd.appName}' flow পাওয়া যায়নি"
                flowRunner.runAppCall(flow, cmd.contact, cmd.video, onProgress)
            }
            is Command.AppMessage -> {
                val flow = AppFlows.find(cmd.appName)
                    ?: return "❌ '${cmd.appName}' flow পাওয়া যায়নি"
                flowRunner.runAppMessage(flow, cmd.contact, cmd.text, cmd.send, onProgress)
            }
            Command.TakePhoto -> {
                onProgress("ক্যামেরা খোলা হচ্ছে…")
                val cam = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                if (!launch(cam)) {
                    return "❌ ক্যামেরা খোলা যায়নি"
                }
                flowRunner.tapShutterIfPresent()
            }
            else -> execute(cmd)
        }
    }

    fun execute(cmd: Command): String {
        AssistantLog.d("CommandExecutor.execute: $cmd")
        return when (cmd) {
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
            is Command.VolumeUp -> volume(AudioManager.ADJUST_RAISE, "🔊 volume up", cmd.steps)
            is Command.VolumeDown -> volume(AudioManager.ADJUST_LOWER, "🔉 volume down", cmd.steps)
            Command.Mute -> volume(AudioManager.ADJUST_MUTE, "🔇 mute", 1)
            Command.Unmute -> volume(AudioManager.ADJUST_UNMUTE, "🔈 unmute", 1)
            Command.CloseAllApps -> withSvc { closeAll(it) }
            Command.CloseApp -> withSvc { closeCurrent(it) }

            // Pack F — Storage (SAF)
            is Command.CreateFolder -> createFolderSaf(cmd)
            is Command.CreateFile -> createFileSaf(cmd)

            // Pack I — Everyday mobile commands
            Command.OpenCamera -> openCamera()
            Command.TakePhoto -> openCamera()
            is Command.SetAlarm -> setAlarm(cmd)
            is Command.SetTimer -> setTimer(cmd)
            is Command.Navigate -> navigate(cmd.place)
            is Command.WebSearch -> webSearch(cmd.query)
            is Command.PlaySong -> playSong(cmd.query)
            is Command.SaveNote -> saveNote(cmd.text)
            is Command.WriteEmail -> writeEmail(cmd)
            is Command.WriteSms -> writeSms(cmd)
            is Command.OpenSystemSettings -> openSystemSettings(cmd.target)

            Command.Sleep, Command.StopAssistant, Command.PanicStop -> "" // service সামলায়
            is Command.AppChat, is Command.AppCall, is Command.AppMessage -> "⏳ async flow"
        }
    }

    // ------------------------------------------------------------ helpers

    private fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            (svc ?: appCtx).startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            AssistantLog.w("ActivityNotFound: ${intent.action}", e)
            false
        } catch (e: SecurityException) {
            AssistantLog.w("SecurityException launching ${intent.action}", e)
            false
        }
    }

    private fun openApp(name: String): String {
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

    private fun volume(direction: Int, msg: String, steps: Int): String {
        val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        for (i in 0 until steps.coerceAtLeast(1)) {
            val flag = if (i == 0) AudioManager.FLAG_SHOW_UI else 0
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flag)
        }
        return msg
    }

    // ---------------------------------------------------- app বন্ধ করা

    /** "sob app close koro": Recents খুলে "Clear all / Close all" বাটন খুঁজে চাপে। */
    private fun closeAll(s: AssistantAccessibilityService): String {
        s.recents()
        handler.postDelayed({ clearRecents(0) }, 1000)
        return "🧹 সব app বন্ধ করছি…"
    }

    private fun clearRecents(attempt: Int) {
        val s = svc ?: return
        if (s.clickByAnyText(AssistantConfig.CLEAR_ALL_LABELS)) {
            toast("🧹 সব app বন্ধ")
            return
        }
        if (attempt >= 4) {
            toast("Recents-এ 'Clear all' বাটন পাইনি (AssistantConfig.CLEAR_ALL_LABELS এ আপনার ফোনের লেখা যোগ করুন)")
            return
        }
        s.swipe(if (attempt < 2) Dir.RIGHT else Dir.LEFT)
        handler.postDelayed({ clearRecents(attempt + 1) }, 800)
    }

    /** "ei app bondho koro": Recents খুলে চলতি app-এর কার্ড উপরে ঠেলে দেয়। */
    private fun closeCurrent(s: AssistantAccessibilityService): String {
        s.recents()
        handler.postDelayed({ svc?.dismissCurrentCard() }, 900)
        return "❌ এই app বন্ধ করছি…"
    }

    private fun toast(msg: String) {
        Toast.makeText(appCtx, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------- Phone Call & YouTube

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
            Toast.makeText(appCtx, "ভিডিও খুঁজে পাইনি", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------- Pack F (SAF Folder / File via DocumentsContract)

    private fun rootTreeDocUri(): Pair<Uri, Uri>? {
        val uriStr = AssistantPrefs.storageTreeUri(appCtx) ?: return null
        val treeUri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return null
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return treeUri to docUri
    }

    private fun findChildFolderDocUri(treeUri: Uri, parentDocUri: Uri, folderName: String): Uri? {
        val parentId = DocumentsContract.getDocumentId(parentDocUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        return runCatching {
            appCtx.contentResolver.query(childrenUri, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR &&
                        name.equals(folderName, ignoreCase = true)
                    ) {
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun createFolderSaf(cmd: Command.CreateFolder): String {
        val (treeUri, rootDocUri) = rootTreeDocUri()
            ?: return "⚠️ আগে Assistant সেটিংস থেকে Storage Folder বেছে নিন"
        return try {
            var targetParent = rootDocUri
            val parentName = cmd.parentFolder?.trim()
            if (!parentName.isNullOrEmpty()) {
                targetParent = findChildFolderDocUri(treeUri, rootDocUri, parentName)
                    ?: DocumentsContract.createDocument(
                        appCtx.contentResolver,
                        rootDocUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        parentName
                    )
                    ?: return "❌ প্যারেন্ট ফোল্ডার '$parentName' তৈরি যায়নি"
            }
            val created = DocumentsContract.createDocument(
                appCtx.contentResolver,
                targetParent,
                DocumentsContract.Document.MIME_TYPE_DIR,
                cmd.name
            )
            if (created != null) "📁 ফোল্ডার তৈরি: ${cmd.name}" else "❌ ফোল্ডার তৈরি যায়নি"
        } catch (e: Exception) {
            AssistantLog.w("createFolderSaf failed", e)
            "❌ ফোল্ডার তৈরি ব্যর্থ (Storage permission দিন)"
        }
    }

    private fun createFileSaf(cmd: Command.CreateFile): String {
        val (_, rootDocUri) = rootTreeDocUri()
            ?: return "⚠️ আগে Assistant সেটিংস থেকে Storage Folder বেছে নিন"
        val ext = cmd.ext.trim().lowercase().ifEmpty { "txt" }
        val mime = when (ext) {
            "csv" -> "text/csv"
            "md" -> "text/markdown"
            "json" -> "application/json"
            else -> "text/plain"
        }
        val fileName = "${cmd.name}.$ext"
        return try {
            val docUri = DocumentsContract.createDocument(
                appCtx.contentResolver,
                rootDocUri,
                mime,
                fileName
            ) ?: return "❌ ফাইল '$fileName' তৈরি যায়নি"

            val body = cmd.text
            if (!body.isNullOrEmpty()) {
                appCtx.contentResolver.openOutputStream(docUri)?.use { out ->
                    out.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            "📄 ফাইল তৈরি: $fileName"
        } catch (e: Exception) {
            AssistantLog.w("createFileSaf failed", e)
            "❌ ফাইল তৈরি ব্যর্থ (Storage permission দিন)"
        }
    }

    // ---------------------------------------------------- Pack I (Everyday mobile utilities)

    private fun openCamera(): String {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        return if (launch(intent)) "📷 ক্যামেরা" else "❌ ক্যামেরা খোলা যায়নি"
    }

    private fun setAlarm(cmd: Command.SetAlarm): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, cmd.hour24)
            putExtra(AlarmClock.EXTRA_MINUTES, cmd.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        val label = String.format("%02d:%02d", cmd.hour24, cmd.minute)
        return if (launch(i)) "⏰ Alarm সেট: $label" else "❌ Alarm সেট করা যায়নি"
    }

    private fun setTimer(cmd: Command.SetTimer): String {
        val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, cmd.seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        return if (launch(i)) "⏱ Timer: ${cmd.seconds} সেকেন্ড" else "❌ Timer সেট করা যায়নি"
    }

    private fun navigate(place: String): String {
        val uri = Uri.parse("google.navigation:q=" + Uri.encode(place))
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        if (launch(mapIntent)) return "🗺️ Navigation: $place"
        val fallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(place))
        )
        return if (launch(fallback)) "🗺️ Maps: $place" else "❌ Maps খোলা যায়নি"
    }

    private fun webSearch(query: String): String {
        val i = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        )
        return if (launch(i)) "🔍 Google: $query" else "❌ Browser খোলা যায়নি"
    }

    private fun playSong(query: String): String {
        val ytMusic = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(android.app.SearchManager.QUERY, query)
        }
        if (launch(ytMusic)) return "🎵 গান: $query"
        return youtube(Command.YoutubeSearch(query, mostViewed = false))
    }

    private fun saveNote(text: String): String {
        Notes(appCtx).add(text)
        return "📝 নোট সেভ: $text"
    }

    private fun writeEmail(cmd: Command.WriteEmail): String {
        val recipient = cmd.recipient.trim()
        val emailAddr = if (recipient.contains("@")) {
            recipient
        } else {
            ContactLookup.findEmail(appCtx, recipient) ?: recipient
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(emailAddr))).apply {
            if (cmd.subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, cmd.subject)
            if (cmd.body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, cmd.body)
        }
        return if (launch(i)) "✉️ ইমেইল ড্রাফট: $emailAddr" else "❌ ইমেইল অ্যাপ পাওয়া যায়নি"
    }

    private fun writeSms(cmd: Command.WriteSms): String {
        val target = cmd.recipient.trim()
        val onlyNumber = target.none { it.isLetter() } && target.count { it.isDigit() } >= 5
        val number = if (onlyNumber) {
            target.filter { it.isDigit() || it == '+' }
        } else {
            ContactLookup.findNumber(appCtx, target) ?: return "'$target' নামে contact পাইনি"
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).apply {
            if (cmd.text.isNotEmpty()) putExtra("sms_body", cmd.text)
        }
        return if (launch(i)) "💬 SMS ড্রাফট: $target" else "❌ SMS অ্যাপ পাওয়া যায়নি"
    }

    private fun openSystemSettings(target: SettingsTarget): String {
        val action = when (target) {
            SettingsTarget.WIFI -> Settings.ACTION_WIFI_SETTINGS
            SettingsTarget.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsTarget.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
            SettingsTarget.SOUND -> Settings.ACTION_SOUND_SETTINGS
        }
        return if (launch(Intent(action))) "⚙️ ${target.name} সেটিংস" else "❌ সেটিংস খোলা যায়নি"
    }
}
