package com.banglakeys.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * ব্যবহারকারীর নিজের সেভ করা note/snippet (যেমন ঠিকানা, common reply, স্বাক্ষর) রাখে।
 * NotepadActivity থেকে add/update/remove করা হয়, keyboard-এর notepad panel থেকে
 * শুধু পড়া ও tap করে বসানো হয়।
 */
class Notes(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bk_notes", Context.MODE_PRIVATE)

    private val items: MutableList<String> = ArrayList()

    init {
        load()
    }

    fun all(): List<String> = items.toList()

    fun add(text: String) {
        val t = text.trim()
        if (t.isEmpty() || items.contains(t)) return
        items.add(0, t)
        save()
    }

    fun contains(text: String): Boolean = items.contains(text.trim())

    fun update(index: Int, text: String) {
        if (index !in items.indices) return
        val t = text.trim()
        if (t.isEmpty()) return
        items[index] = t
        save()
    }

    fun updateByText(oldText: String, newText: String): Boolean {
        val idx = items.indexOf(oldText)
        if (idx < 0) return false
        val t = newText.trim()
        if (t.isEmpty()) return false
        items[idx] = t
        save()
        return true
    }

    fun remove(index: Int) {
        if (index !in items.indices) return
        items.removeAt(index)
        save()
    }

    fun removeByText(text: String): Boolean {
        val removed = items.remove(text)
        if (removed) save()
        return removed
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
}
