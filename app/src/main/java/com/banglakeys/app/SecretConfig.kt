package com.banglakeys.app

import android.content.Context
import org.json.JSONObject

/**
 * গোপন passphrase-গুলো (per-contact) আর বর্তমান glyph style ফোনে local-এ সেভ রাখে।
 *
 * ⚠️ কোনো passphrase কখনো code-এ hardcode বা GitHub-এ commit করবেন না। প্রতিটা
 * passphrase শুধু আপনি আর সেই নির্দিষ্ট মানুষ মুখে মুখে/সামনাসামনি ঠিক করবেন।
 */
object SecretConfig {
    private const val PREFS = "secret_prefs"
    private const val KEY_CONTACTS = "contacts_json" // {"নাম": "passphrase", ...}
    private const val KEY_STYLE = "style"
    private const val KEY_AUTODETECT = "autodetect"
    private const val DEFAULT_CONTACT = "ডিফল্ট"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------- contacts

    fun getContacts(ctx: Context): Map<String, String> {
        val raw = prefs(ctx).getString(KEY_CONTACTS, null)
        val map = LinkedHashMap<String, String>()
        if (raw != null) {
            try {
                val j = JSONObject(raw)
                val keys = j.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = j.getString(k)
                }
            } catch (_: Exception) {
            }
        } else {
            // পুরনো single-passphrase ভার্সন থেকে migrate (থাকলে)
            val old = prefs(ctx).getString("passphrase", null)
            if (!old.isNullOrBlank()) map[DEFAULT_CONTACT] = old
        }
        return map
    }

    fun setContact(ctx: Context, name: String, passphrase: String) {
        if (name.isBlank() || passphrase.isBlank()) return
        val map = LinkedHashMap(getContacts(ctx))
        map[name.trim()] = passphrase
        save(ctx, map)
    }

    fun removeContact(ctx: Context, name: String) {
        val map = LinkedHashMap(getContacts(ctx))
        map.remove(name)
        save(ctx, map)
    }

    private fun save(ctx: Context, map: Map<String, String>) {
        val j = JSONObject()
        for ((k, v) in map) j.put(k, v)
        prefs(ctx).edit().putString(KEY_CONTACTS, j.toString()).apply()
    }

    fun allPassphrases(ctx: Context): List<String> = getContacts(ctx).values.toList()

    fun hasAnyPassphrase(ctx: Context): Boolean = getContacts(ctx).isNotEmpty()

    // ------------------------------------------------------------- style

    fun getStyle(ctx: Context): GlyphStyle {
        val name = prefs(ctx).getString(KEY_STYLE, null) ?: return GlyphStyle.RUNIC
        return try {
            GlyphStyle.valueOf(name)
        } catch (_: Exception) {
            GlyphStyle.RUNIC
        }
    }

    fun setStyle(ctx: Context, style: GlyphStyle) {
        prefs(ctx).edit().putString(KEY_STYLE, style.name).apply()
    }

    // -------------------------------------------------------- auto-detect

    /** কিবোর্ড-এর বাইরে screen scan করে auto-decode bubble দেখানো চালু/বন্ধ (ডিফল্ট: বন্ধ)। */
    fun autoDetectEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTODETECT, false)

    fun setAutoDetect(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUTODETECT, enabled).apply()
    }
}
