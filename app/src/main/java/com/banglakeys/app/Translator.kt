package com.banglakeys.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.Executors
import javax.net.ssl.SSLException

/**
 * বাংলা <-> English (এবং auto-detect) অনুবাদ — API key লাগে না, শুধু INTERNET permission।
 *
 * v3.4 (deep fix):
 *  • চারটা provider ক্রমে: Google(googleapis) → Google(translate.google.com) → MyMemory → Lingva।
 *    একটা বন্ধ/ব্লক হলে পরেরটা; মোট সময় ~১২ সেকেন্ডে সীমাবদ্ধ।
 *  • কোনো অবস্থাতেই callback হারায় না (Throwable ধরা) — আগে exception-এ অনুবাদ "আটকে" যেত।
 *  • ব্যর্থ হলে কোন provider কেন ব্যর্থ (dns / 429 / http403 / timeout / ssl / parse) তার কোড [Outcome.Fail.detail]-এ।
 *  • [selfTest]: প্রতিটা provider আলাদা করে পরীক্ষা (Settings-এর "অনুবাদ টেস্ট" বোতাম)।
 *  • বাংলা লেখা URL-এ ৯ গুণ লম্বা হয় বলে বাংলায় ছোট টুকরো (২০০ অক্ষর)।
 */
object Translator {

    enum class Reason(val messageBn: String) {
        EMPTY("অনুবাদ করার মতো লেখা নেই"),
        OFFLINE("ইন্টারনেট সংযোগ নেই"),
        QUOTA("আজকের ফ্রি অনুবাদ সীমা শেষ — পরে চেষ্টা করুন"),
        TIMEOUT("সার্ভার সাড়া দিচ্ছে না — আবার চেষ্টা করুন"),
        SERVER("অনুবাদ সার্ভারে সমস্যা — আবার চেষ্টা করুন"),
    }

    sealed class Outcome {
        data class Ok(val text: String, val provider: String, val from: String, val to: String) : Outcome()
        /** [detail] যেমন "google:dns, mymemory:429" — সমস্যা খুঁজতে কাজে লাগে। */
        data class Fail(val reason: Reason, val detail: String = "") : Outcome()
    }

    data class Probe(val provider: String, val ok: Boolean, val ms: Long, val detail: String) {
        fun line(): String = (if (ok) "✅ " else "❌ ") + provider + " · ${ms}ms · " + detail
    }

    private class ProviderError(val reason: Reason, val code: String) : Exception(code)

    private class Provider(val name: String, val call: (String, String, String) -> String)

    private const val CHUNK_MAX = 400
    private const val CHUNK_MAX_BN = 200
    private const val CONNECT_MS = 4000
    private const val READ_MS = 5000
    private const val BUDGET_MS = 12_000L
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2)
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>?) = size > 60
    }

    private val providers = listOf(
        Provider("google") { t, f, to -> viaGoogle("translate.googleapis.com", t, f, to) },
        Provider("google2") { t, f, to -> viaGoogle("translate.google.com", t, f, to) },
        Provider("mymemory", ::viaMyMemory),
        Provider("lingva", ::viaLingva),
    )

    // ------------------------------------------------------------------ public API

    fun containsBengali(s: String): Boolean = s.any { it.code in 0x0980..0x09FF }

    /** লেখায় বাংলা থাকলে bn→en, নইলে →bn। */
    fun autoDirection(text: String): Pair<String, String> =
        if (containsBengali(text)) "bn" to "en" else "en" to "bn"

    /** main thread-এ callback; কোনো অবস্থাতেই একবারের বেশি/কম নয়। */
    fun translateEx(text: String, from: String, to: String, onDone: (Outcome) -> Unit) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            onDone(Outcome.Fail(Reason.EMPTY)); return
        }
        val key = "$from|$to|$clean"
        synchronized(cache) { cache[key] }?.let {
            onDone(Outcome.Ok(it, "cache", from, to)); return
        }
        pool.execute {
            val out = try {
                runChain(clean, from, to)
            } catch (t: Throwable) {
                Outcome.Fail(Reason.SERVER, "crash:${t.javaClass.simpleName}")
            }
            if (out is Outcome.Ok) synchronized(cache) { cache[key] = out.text }
            main.post { onDone(out) }
        }
    }

    /** পুরনো API (সামঞ্জস্যের জন্য): ব্যর্থ হলে null। */
    fun translate(text: String, from: String, to: String, onResult: (String?) -> Unit) {
        translateEx(text, from, to) { onResult((it as? Outcome.Ok)?.text) }
    }

    /** প্রতিটা provider আলাদা করে একটা ছোট বাক্যে পরীক্ষা। */
    fun selfTest(onDone: (List<Probe>) -> Unit) {
        pool.execute {
            val out = ArrayList<Probe>()
            for (p in providers) {
                val t0 = SystemClock.uptimeMillis()
                val probe = try {
                    val r = p.call("আমি ভালো আছি", "bn", "en")
                    Probe(p.name, r.isNotBlank(), SystemClock.uptimeMillis() - t0, r.take(40))
                } catch (e: ProviderError) {
                    Probe(p.name, false, SystemClock.uptimeMillis() - t0, e.code)
                } catch (t: Throwable) {
                    Probe(p.name, false, SystemClock.uptimeMillis() - t0, "crash:${t.javaClass.simpleName}")
                }
                out.add(probe)
            }
            main.post { onDone(out) }
        }
    }

    // ------------------------------------------------------------------ core

    private fun runChain(text: String, from: String, to: String): Outcome {
        val chunks = splitChunks(text, if (containsBengali(text)) CHUNK_MAX_BN else CHUNK_MAX)
        val started = SystemClock.uptimeMillis()
        var lastReason = Reason.SERVER
        val details = ArrayList<String>()
        for (p in providers) {
            if (SystemClock.uptimeMillis() - started > BUDGET_MS) {
                details.add("budget")
                break
            }
            try {
                val joined = chunks.joinToString(" ") { p.call(it, from, to) }.trim()
                if (joined.isNotEmpty()) return Outcome.Ok(joined, p.name, from, to)
                details.add("${p.name}:empty")
            } catch (e: ProviderError) {
                lastReason = e.reason
                details.add("${p.name}:${e.code}")
                if (e.reason == Reason.OFFLINE) break // একই নেটওয়ার্ক — বৃথা চেষ্টা নয়
            } catch (t: Throwable) {
                details.add("${p.name}:${t.javaClass.simpleName}")
            }
        }
        return Outcome.Fail(lastReason, details.joinToString(" "))
    }

    /** বাক্যের শেষে (. । ! ? নতুন লাইন) ভেঙে ≤ [max] অক্ষরের টুকরো। */
    fun splitChunks(text: String, max: Int = CHUNK_MAX): List<String> {
        val limit = max.coerceAtLeast(1)
        if (text.length <= limit) return listOf(text)
        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotBlank()) out.add(cur.toString().trim()); cur.setLength(0) }
        val sentence = StringBuilder()
        fun addSentence() {
            var s = sentence.toString(); sentence.setLength(0)
            while (s.length > limit) { // বাক্যই বেশি লম্বা → শব্দের সীমায় কাটি
                var cut = s.lastIndexOf(' ', limit)
                if (cut <= 0) cut = limit
                if (cur.length + cut > limit) flush()
                cur.append(s, 0, cut).append(' ')
                flush()
                s = s.substring(cut).trimStart()
            }
            if (cur.length + s.length > limit) flush()
            cur.append(s)
        }
        for (c in text) {
            sentence.append(c)
            if (c == '.' || c == '।' || c == '!' || c == '?' || c == '\n') addSentence()
        }
        if (sentence.isNotEmpty()) addSentence()
        flush()
        return out
    }

    // ------------------------------------------------------------------ http

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(url: String): Pair<Int, String> {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_MS
                readTimeout = READ_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json, text/plain, */*")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return code to body
        } catch (_: UnknownHostException) {
            throw ProviderError(Reason.OFFLINE, "dns")
        } catch (_: ConnectException) {
            throw ProviderError(Reason.OFFLINE, "connect")
        } catch (_: NoRouteToHostException) {
            throw ProviderError(Reason.OFFLINE, "noroute")
        } catch (_: SocketTimeoutException) {
            throw ProviderError(Reason.TIMEOUT, "timeout")
        } catch (_: SSLException) {
            throw ProviderError(Reason.SERVER, "ssl")
        } catch (_: IOException) {
            throw ProviderError(Reason.SERVER, "io")
        } finally {
            conn?.disconnect()
        }
    }

    private fun checkHttp(code: Int) {
        if (code == 429) throw ProviderError(Reason.QUOTA, "429")
        if (code !in 200..299) throw ProviderError(Reason.SERVER, "http$code")
    }

    // ------------------------------------------------------------------ providers

    private fun viaGoogle(host: String, text: String, from: String, to: String): String {
        val sl = if (from.isBlank()) "auto" else from
        val (code, body) = httpGet(
            "https://$host/translate_a/single?client=gtx&sl=${enc(sl)}&tl=${enc(to)}&dt=t&q=${enc(text)}"
        )
        checkHttp(code)
        return parseGoogle(body) ?: throw ProviderError(Reason.SERVER, "parse")
    }

    /** `[[["অনুবাদ","original",...],...],null,"en"]` থেকে অনুবাদ জোড়া লাগায়। */
    fun parseGoogle(body: String): String? = try {
        val segs = JSONArray(body).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until segs.length()) {
            val seg = segs.optJSONArray(i) ?: continue
            if (!seg.isNull(0)) sb.append(seg.getString(0))
        }
        sb.toString().takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun viaMyMemory(text: String, from: String, to: String): String {
        val sl = if (from.isBlank() || from == "auto") autoDirection(text).first else from
        val (code, body) = httpGet(
            "https://api.mymemory.translated.net/get?q=${enc(text)}&langpair=${enc("$sl|$to")}"
        )
        if (code == 429) throw ProviderError(Reason.QUOTA, "429")
        return parseMyMemory(body)
    }

    /** MyMemory HTTP 200-এও warning পাঠায় — সেটা অনুবাদ নয়, তাই যাচাই। */
    fun parseMyMemory(body: String): String {
        try {
            val j = JSONObject(body)
            val status = j.optString("responseStatus", "200").toIntOrNull() ?: 200
            if (status == 429) throw ProviderError(Reason.QUOTA, "429")
            val t = j.optJSONObject("responseData")?.optString("translatedText", "").orEmpty()
            if (t.contains("MYMEMORY WARNING", ignoreCase = true)) throw ProviderError(Reason.QUOTA, "quota")
            if (status != 200 || t.isBlank() || t.contains("QUERY LENGTH LIMIT", ignoreCase = true)) {
                throw ProviderError(Reason.SERVER, "mm$status")
            }
            return t
        } catch (e: ProviderError) {
            throw e
        } catch (_: Exception) {
            throw ProviderError(Reason.SERVER, "parse")
        }
    }

    private fun viaLingva(text: String, from: String, to: String): String {
        val sl = if (from.isBlank()) "auto" else from
        val path = enc(text).replace("+", "%20")
        val (code, body) = httpGet("https://lingva.ml/api/v1/$sl/$to/$path")
        checkHttp(code)
        return parseLingva(body) ?: throw ProviderError(Reason.SERVER, "parse")
    }

    fun parseLingva(body: String): String? = try {
        JSONObject(body).optString("translation", "").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}
