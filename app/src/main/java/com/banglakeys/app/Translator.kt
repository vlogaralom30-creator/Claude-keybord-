package com.banglakeys.app

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * ছোট text (শব্দ/বাক্য) বাংলা <-> English translate করে, MyMemory-র ফ্রি API দিয়ে
 * (কোনো API key লাগে না)। এর জন্য ফোনের internet দরকার এবং AndroidManifest.xml-এ
 * INTERNET permission যোগ করতে হবে (SETUP.md দেখুন)। কোনো external library লাগে না,
 * শুধু Android-এর built-in java.net ও org.json ব্যবহার করা হয়েছে।
 */
object Translator {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * [from]/[to] হবে "bn" বা "en"। ফলাফল main thread-এ [onResult] দিয়ে ফেরত আসবে;
     * ব্যর্থ হলে null আসবে (নেটওয়ার্ক না থাকলে বা সার্ভার সাড়া না দিলে)।
     */
    fun translate(text: String, from: String, to: String, onResult: (String?) -> Unit) {
        if (text.isBlank()) {
            onResult(null)
            return
        }
        Thread {
            val result = try {
                val q = URLEncoder.encode(text, "UTF-8")
                val url = URL("https://api.mymemory.translated.net/get?q=$q&langpair=$from|$to")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                JSONObject(body).getJSONObject("responseData").getString("translatedText")
            } catch (_: Exception) {
                null
            }
            handler.post { onResult(result) }
        }.start()
    }
}
