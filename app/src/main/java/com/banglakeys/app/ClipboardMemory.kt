package com.banglakeys.app

import android.content.ClipData
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
    }

    /** IME-র onCreate() থেকে call করুন, clipboard listen শুরু করতে। */
    fun start() {
        if (manager != null) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            val clip: ClipData? = cm.primaryClip
            val text = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(context)?.toString()
            } else null
            if (!text.isNullOrBlank()) add(text)
        }
        manager = cm
        listener = l
        cm.addPrimaryClipChangedListener(l)
    }

    /** IME-র onDestroy() থেকে call করুন। */
    fun stop() {
        val cm = manager ?: return
        listener?.let { cm.removePrimaryClipChangedListener(it) }
        manager = null
        listener = null
    }

    fun clips(): List<String> = items.toList()

    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        items.remove(t)
        items.add(0, t)
        while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
        save()
    }

    fun remove(text: String) {
        if (items.remove(text)) save()
    }

    fun clear() {
        items.clear()
        save()
    }

    private fun save() {
        val arr = JSONArray()
        for (s in items) arr.put(s)
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun load() {
        val s = prefs.getString("items", null) ?: return
        try {
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) items.add(arr.getString(i))
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val MAX_ITEMS = 20
    }
}
