package com.banglakeys.app

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * "Memory" feature: system clipboard-এ যা কিছু copy হয়, তার সাম্প্রতিক history (সর্বোচ্চ
 * ২০টা) রাখে এবং persist করে, যাতে keyboard বন্ধ হয়ে আবার খুললেও আগের copy করা text
 * গুলো পাওয়া যায়। NotepadActivity থেকে দেখা/পুনরায় copy/delete করা যায়।
 */
class ClipboardMemory(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bk_clipboard", Context.MODE_PRIVATE)

    private val items: MutableList<String> = ArrayList()
    private var manager: ClipboardManager? = null
    private var listener: ClipboardManager.OnPrimaryClipChangedListener? = null

    init {
        load()
        pollSystemClipboard()
    }

    fun reload() {
        items.clear()
        load()
        pollSystemClipboard()
    }

    fun pollSystemClipboard() {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val sensitive = clip.description?.extras
                    ?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
                if (!sensitive) {
                    val text = clip.getItemAt(0).coerceToText(context)?.toString()
                    if (!text.isNullOrBlank() && !shouldIgnore(text)) {
                        if (!items.contains(text.trim())) {
                            add(text)
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    /** IME-র onCreate() থেকে call করুন, clipboard listen শুরু করতে। */
    fun start() {
        if (manager != null) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val clip: ClipData? = cm.primaryClip
            // password manager "sensitive" দিয়ে মার্ক করলে history-তে রাখি না (Android 13+ extra)
            val sensitive = clip?.description?.extras
                ?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
            if (sensitive) return@OnPrimaryClipChangedListener
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(context)?.toString()
            } else null
            if (!text.isNullOrBlank() && !shouldIgnore(text)) add(text)
        }
        manager = cm
        listener = l
        cm.addPrimaryClipChangedListener(l)
        pollSystemClipboard()
    }

    /** IME-র onDestroy() থেকে call করুন। */
    fun stop() {
        val cm = manager ?: return
        listener?.let { cm.removePrimaryClipChangedListener(it) }
        manager = null
        listener = null
    }

    fun clips(): List<String> {
        pollSystemClipboard()
        return items.toList()
    }

    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        items.remove(t)
        items.add(0, t)
        while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
        save()
    }

    fun update(oldText: String, newText: String): Boolean {
        val idx = items.indexOf(oldText)
        val t = newText.trim()
        if (idx < 0 || t.isEmpty()) return false
        items.removeAt(idx)
        val existing = items.indexOf(t)
        if (existing >= 0) items.removeAt(existing)
        items.add(idx.coerceAtMost(items.size), t)
        save()
        return true
    }

    fun remove(text: String) {
        if (items.remove(text)) save()
    }

    fun clear() {
        items.clear()
        save()
    }

    /** history এখন LocalAesVault (AES-256-GCM) দিয়ে encrypt করে রাখা হয়; plaintext আর লেখা হয় না। */
    private fun save() {
        val arr = JSONArray()
        for (s in items) arr.put(s)
        try {
            val enc = LocalAesVault.encrypt(context, arr.toString())
            prefs.edit().putString(KEY_ENC, enc).remove(KEY_LEGACY).apply()
        } catch (_: Throwable) {
            // encrypt ব্যর্থ হলে plaintext-এ fallback নয় — শুধু এই session-এর memory-তে থাকবে
        }
    }

    private fun load() {
        val enc = prefs.getString(KEY_ENC, null)
        val legacy = prefs.getString(KEY_LEGACY, null)
        val json = if (enc != null) LocalAesVault.decrypt(context, enc) else legacy
        if (json != null) {
            try {
                val arr = JSONArray(json)
                val tmp = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) tmp.add(arr.getString(i))
                items.addAll(tmp)
            } catch (_: Exception) {
            }
        }
        // পুরনো plaintext history থাকলে এখনই encrypt করে plaintext মুছে ফেলি (migration)
        if (legacy != null) save()
    }

    companion object {
        private const val MAX_ITEMS = 20
        private const val KEY_ENC = "items_enc"
        private const val KEY_LEGACY = "items"

        @Volatile
        private var suppressedText: String? = null

        @Volatile
        private var suppressedUntilMs: Long = 0L

        /** Assistant clipboard-paste fallback-এর সময় clipboard history-তে সেভ না করতে mark করে। */
        fun ignoreNext(text: String) {
            suppressedText = text.trim()
            suppressedUntilMs = System.currentTimeMillis() + 5_000L
        }

        private fun shouldIgnore(text: String): Boolean {
            val exp = suppressedText ?: return false
            if (System.currentTimeMillis() > suppressedUntilMs) {
                suppressedText = null
                return false
            }
            if (text.trim() == exp) {
                suppressedText = null
                return true
            }
            return false
        }
    }
}
