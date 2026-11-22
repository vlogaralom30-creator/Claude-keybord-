package com.banglakeys.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * স্থানীয় rule-based + fuzzy `SmartCommandResolver` যা বুঝতে পারে না,
 * সেটা Gemini দিয়ে বোঝার চেষ্টা করে। Structured JSON output (`responseSchema`)
 * বাধ্য করা হয়েছে যাতে সব Pack (F/G/H/I সহ) নির্ভুলভাবে map হয়।
 */
object GeminiCommandInterpreter {

    private const val ENDPOINT_FMT =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"

    private val COMMAND_TYPES = listOf(
        "open_app", "call", "youtube_search", "swipe", "click_text", "type_text",
        "play_pause", "like", "double_tap", "tap", "enter", "back", "home",
        "recents", "notifications", "quick_settings", "lock", "screenshot",
        "volume_up", "volume_down", "sleep", "stop_assistant", "panic_stop",
        "create_folder", "create_file", "app_chat", "app_call", "app_message",
        "open_camera", "take_photo", "set_alarm", "set_timer", "navigate",
        "web_search", "play_song", "save_note", "write_email", "write_sms",
        "open_system_settings", "unknown"
    )

    private fun systemPrompt(): String = """
        তুমি একটা Android ভয়েস-কমান্ড অ্যাসিস্ট্যান্টের জন্য ব্যবহারকারীর কথা বিশ্লেষণ করছো।
        ব্যবহারকারী বাংলা/Banglish/English যেকোনো মিশ্রণে কথা বলতে পারে।
        তোমার একমাত্র কাজ: বলা কথাটা নিচের তালিকার কোন command type বোঝাচ্ছে তা বের করা।
        নিজে থেকে নতুন কোনো action বানিও না, আর তালিকার বাইরের কিছু মনে হলে "unknown" দাও।

        Command type-গুলো ও কখন কোনটা:
        - open_app: কোনো অ্যাপ খুলতে বললে -> "name" (অ্যাপের নাম হুবহু, যেমন "tiktok"; ambiguous যেমন "reel"/"video" হলে হুবহু সেটাই দাও)।
        - call: সাধারণ ফোন কল -> "target" (নাম/নম্বর)।
        - youtube_search: ইউটিউবে খোঁজা/চালানো -> "query", "most_viewed" (true/false)।
        - swipe: স্ক্রল/পরের-আগের -> "direction" (up/down/left/right)।
        - click_text: স্ক্রিনের নির্দিষ্ট লেখা/বাটন চাপতে -> "text"।
        - type_text: কোনো লেখা টাইপ করতে -> "text"।
        - play_pause, like, double_tap, tap, enter, back, home, recents, notifications,
          quick_settings, lock, screenshot, volume_up, volume_down, sleep, stop_assistant,
          panic_stop ("সব বন্ধ"/"stop everything"), open_camera, take_photo: অতিরিক্ত ফিল্ড লাগবে না।
        - create_folder: ফোল্ডার তৈরি -> "name", এবং ভেতরে হলে "parent_folder"।
        - create_file: ফাইল তৈরি -> "name", "ext" (যেমন txt/csv/md/json), লেখা থাকলে "text"।
        - app_chat: Claude/ChatGPT/Gemini বা বর্তমান অ্যাপে লেখা ও (ইচ্ছায়) সেন্ড -> "app_name" (optional), "text", "send" (true/false)।
        - app_call: imo/WhatsApp/Messenger/Telegram কল -> "app_name", "contact", "video" (true/false)।
        - app_message: imo/WhatsApp/Messenger/Telegram মেসেজ -> "app_name", "contact", "text", "send" (true/false)।
        - set_alarm: অ্যালার্ম সেট -> "hour24" (0-23), "minute" (0-59)।
        - set_timer: টাইমার সেট -> "seconds" (মোট সেকেন্ড)।
        - navigate: ম্যাপে দিকনির্দেশনা/রুট -> "place"।
        - web_search: গুগল/ওয়েব সার্চ -> "query"।
        - play_song: গান বাজাতে -> "query"।
        - save_note: নোট সেভ -> "text"।
        - write_email: ইমেইল ড্রাফট -> "recipient", "subject", "body"।
        - write_sms: SMS ড্রাফট -> "recipient", "text"।
        - open_system_settings: সিস্টেম সেটিংস -> "settings_target" (wifi/bluetooth/display/sound)।
        - না মিললে "unknown" দাও।

        শুধু JSON output দাও, অন্য কোনো ব্যাখ্যা/লেখা দিও না।
    """.trimIndent()

    private fun schema(): JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("type", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(COMMAND_TYPES))
            })
            put("name", JSONObject().put("type", "STRING"))
            put("target", JSONObject().put("type", "STRING"))
            put("query", JSONObject().put("type", "STRING"))
            put("most_viewed", JSONObject().put("type", "BOOLEAN"))
            put("direction", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(listOf("up", "down", "left", "right")))
            })
            put("text", JSONObject().put("type", "STRING"))
            put("parent_folder", JSONObject().put("type", "STRING"))
            put("ext", JSONObject().put("type", "STRING"))
            put("app_name", JSONObject().put("type", "STRING"))
            put("contact", JSONObject().put("type", "STRING"))
            put("video", JSONObject().put("type", "BOOLEAN"))
            put("send", JSONObject().put("type", "BOOLEAN"))
            put("hour24", JSONObject().put("type", "INTEGER"))
            put("minute", JSONObject().put("type", "INTEGER"))
            put("seconds", JSONObject().put("type", "INTEGER"))
            put("place", JSONObject().put("type", "STRING"))
            put("recipient", JSONObject().put("type", "STRING"))
            put("subject", JSONObject().put("type", "STRING"))
            put("body", JSONObject().put("type", "STRING"))
            put("settings_target", JSONObject().apply {
                put("type", "STRING")
                put("enum", JSONArray(listOf("wifi", "bluetooth", "display", "sound")))
            })
        })
        put("required", JSONArray(listOf("type")))
    }

    /** [spokenText]-কে Gemini-কে পাঠায় (background thread); ফলাফল main thread-এ [onResult]। */
    fun interpret(ctx: Context, spokenText: String, onResult: (Command?) -> Unit) {
        val key = GeminiConfig.getKey(ctx)
        if (key.isBlank() || spokenText.isBlank()) {
            postMain { onResult(null) }
            return
        }
        Thread {
            val cmd = try {
                call(key, spokenText)
            } catch (e: Exception) {
                AssistantLog.w("Gemini interpret failed", e)
                null
            }
            postMain { onResult(cmd) }
        }.start()
    }

    private fun postMain(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    private fun call(key: String, spokenText: String): Command? {
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemPrompt())))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", spokenText)))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("responseSchema", schema())
            })
        }

        val url = URL(String.format(ENDPOINT_FMT, GeminiConfig.MODEL, key))
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            AssistantLog.w("Gemini HTTP $code: ${err?.take(180)}")
            conn.disconnect()
            return null
        }
        val raw = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val text = JSONObject(raw)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text")

        return parseJsonResponse(text)
    }

    fun parseJsonResponse(jsonText: String): Command? = runCatching {
        toCommand(JSONObject(jsonText))
    }.getOrNull()

    private fun optNonEmpty(j: JSONObject, key: String): String? =
        if (j.has(key) && !j.isNull(key)) j.optString(key).trim().takeIf { it.isNotEmpty() } else null

    private fun toCommand(j: JSONObject): Command? = when (j.optString("type", "unknown")) {
        "open_app" -> optNonEmpty(j, "name")?.let { Command.OpenApp(it) }
        "call" -> optNonEmpty(j, "target")?.let { Command.Call(it) }
        "youtube_search" -> optNonEmpty(j, "query")
            ?.let { Command.YoutubeSearch(it, j.optBoolean("most_viewed", false)) }
        "swipe" -> when (j.optString("direction")) {
            "up" -> Command.Swipe(Dir.UP)
            "down" -> Command.Swipe(Dir.DOWN)
            "left" -> Command.Swipe(Dir.LEFT)
            "right" -> Command.Swipe(Dir.RIGHT)
            else -> null
        }
        "click_text" -> optNonEmpty(j, "text")?.let { Command.ClickText(it) }
        "type_text" -> optNonEmpty(j, "text")?.let { Command.TypeText(it) }
        "play_pause" -> Command.PlayPause
        "like" -> Command.Like
        "double_tap" -> Command.DoubleTap
        "tap" -> Command.Tap
        "enter" -> Command.Enter
        "back" -> Command.Back
        "home" -> Command.Home
        "recents" -> Command.Recents
        "notifications" -> Command.Notifications
        "quick_settings" -> Command.QuickSettings
        "lock" -> Command.Lock
        "screenshot" -> Command.Screenshot
        "volume_up" -> Command.VolumeUp
        "volume_down" -> Command.VolumeDown
        "sleep" -> Command.Sleep
        "stop_assistant" -> Command.StopAssistant
        "panic_stop" -> Command.PanicStop
        "create_folder" -> optNonEmpty(j, "name")?.let {
            Command.CreateFolder(it, optNonEmpty(j, "parent_folder"))
        }
        "create_file" -> optNonEmpty(j, "name")?.let {
            val ext = optNonEmpty(j, "ext")?.lowercase() ?: "txt"
            Command.CreateFile(it, ext, optNonEmpty(j, "text"))
        }
        "app_chat" -> optNonEmpty(j, "text")?.let {
            Command.AppChat(optNonEmpty(j, "app_name"), it, j.optBoolean("send", false))
        }
        "app_call" -> {
            val app = optNonEmpty(j, "app_name")
            val contact = optNonEmpty(j, "contact")
            if (app != null && contact != null) {
                Command.AppCall(app, contact, j.optBoolean("video", false))
            } else null
        }
        "app_message" -> {
            val app = optNonEmpty(j, "app_name")
            val contact = optNonEmpty(j, "contact")
            val text = optNonEmpty(j, "text")
            if (app != null && contact != null && text != null) {
                Command.AppMessage(app, contact, text, j.optBoolean("send", true))
            } else null
        }
        "open_camera" -> Command.OpenCamera
        "take_photo" -> Command.TakePhoto
        "set_alarm" -> if (j.has("hour24")) {
            val h = j.optInt("hour24", -1)
            val m = j.optInt("minute", 0).coerceIn(0, 59)
            if (h in 0..23) Command.SetAlarm(h, m) else null
        } else null
        "set_timer" -> {
            val secs = j.optInt("seconds", 0)
            if (secs > 0) Command.SetTimer(secs) else null
        }
        "navigate" -> optNonEmpty(j, "place")?.let { Command.Navigate(it) }
        "web_search" -> optNonEmpty(j, "query")?.let { Command.WebSearch(it) }
        "play_song" -> optNonEmpty(j, "query")?.let { Command.PlaySong(it) }
        "save_note" -> optNonEmpty(j, "text")?.let { Command.SaveNote(it) }
        "write_email" -> optNonEmpty(j, "recipient")?.let {
            Command.WriteEmail(it, optNonEmpty(j, "subject") ?: "", optNonEmpty(j, "body") ?: "")
        }
        "write_sms" -> optNonEmpty(j, "recipient")?.let {
            Command.WriteSms(it, optNonEmpty(j, "text") ?: "")
        }
        "open_system_settings" -> when (j.optString("settings_target").lowercase()) {
            "wifi" -> Command.OpenSystemSettings(SettingsTarget.WIFI)
            "bluetooth" -> Command.OpenSystemSettings(SettingsTarget.BLUETOOTH)
            "display" -> Command.OpenSystemSettings(SettingsTarget.DISPLAY)
            "sound" -> Command.OpenSystemSettings(SettingsTarget.SOUND)
            else -> null
        }
        else -> null
    }
}
