package com.banglakeys.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * গোপন passphrase (per-contact), glyph style, draft এবং recent secret message cache
 * ফোনে local-এ **AES-256-GCM** (`LocalAesVault`) দিয়ে encrypt করে সেভ রাখে।
 *
 * কোনো গোপন passphrase বা secret message content কখনো plaintext হিসেবে
 * SharedPreferences-এ রাখা হয় না।
 */
object SecretConfig {
    private const val PREFS = "secret_prefs"
    private const val KEY_CONTACTS_ENC = "contacts_aes"
    private const val KEY_ACTIVE_ENC = "active_contact_aes"
    private const val KEY_CACHE_ENC = "secret_cache_aes"
    private const val KEY_DRAFT_ENC = "secret_draft_aes"

    // Legacy plaintext keys (একবার পড়েই AES-এ migrate করে মুছে ফেলা হয়)
    private const val LEGACY_KEY_CONTACTS = "contacts_json"
    private const val LEGACY_KEY_ACTIVE = "active_contact"
    private const val LEGACY_KEY_PASSPHRASE = "passphrase"

    private const val KEY_STYLE = "style"
    private const val KEY_AUTODETECT = "autodetect"
    private const val DEFAULT_CONTACT = "ডিফল্ট"
    private const val MAX_CACHE_ITEMS = 25

    data class SecretCacheEntry(
        val id: String,
        val contactName: String,
        val plainText: String,
        val glyphText: String,
        val styleName: String,
        val isOutgoing: Boolean,
        val timestampMs: Long,
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------- contacts (AES-encrypted at rest)

    fun getContacts(ctx: Context): Map<String, String> {
        val p = prefs(ctx)
        val map = LinkedHashMap<String, String>()

        val enc = p.getString(KEY_CONTACTS_ENC, null)
        if (!enc.isNullOrBlank()) {
            val decrypted = LocalAesVault.decrypt(ctx, enc)
            if (!decrypted.isNullOrBlank()) {
                parseContactsJson(decrypted, map)
            }
            return map
        }

        // Legacy plaintext migration -> AES-256-GCM
        val legacyJson = p.getString(LEGACY_KEY_CONTACTS, null)
        if (!legacyJson.isNullOrBlank()) {
            parseContactsJson(legacyJson, map)
        } else {
            val oldPass = p.getString(LEGACY_KEY_PASSPHRASE, null)
            if (!oldPass.isNullOrBlank()) {
                map[DEFAULT_CONTACT] = oldPass
            }
        }
        if (map.isNotEmpty() || legacyJson != null || p.contains(LEGACY_KEY_PASSPHRASE)) {
            saveContactsEncrypted(ctx, map)
        }
        return map
    }

    private fun parseContactsJson(raw: String, out: MutableMap<String, String>) {
        try {
            val j = JSONObject(raw)
            val keys = j.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = j.getString(k)
            }
        } catch (_: Exception) {
        }
    }

    fun setContact(ctx: Context, name: String, passphrase: String) {
        if (name.isBlank() || passphrase.isBlank()) return
        val map = LinkedHashMap(getContacts(ctx))
        map[name.trim()] = passphrase
        saveContactsEncrypted(ctx, map)
    }

    fun removeContact(ctx: Context, name: String) {
        val map = LinkedHashMap(getContacts(ctx))
        map.remove(name)
        saveContactsEncrypted(ctx, map)
        if (getActiveContact(ctx) == name) {
            prefs(ctx).edit().remove(KEY_ACTIVE_ENC).remove(LEGACY_KEY_ACTIVE).apply()
        }
    }

    private fun saveContactsEncrypted(ctx: Context, map: Map<String, String>) {
        val j = JSONObject()
        for ((k, v) in map) j.put(k, v)
        val encrypted = LocalAesVault.encrypt(ctx, j.toString())
        prefs(ctx).edit()
            .putString(KEY_CONTACTS_ENC, encrypted)
            .remove(LEGACY_KEY_CONTACTS)
            .remove(LEGACY_KEY_PASSPHRASE)
            .apply()
    }

    /** কিবোর্ডের 🔐 mode-এ যার জন্য encrypt হবে (null হলে প্রথম contact)। */
    fun getActiveContact(ctx: Context): String? {
        val p = prefs(ctx)
        val enc = p.getString(KEY_ACTIVE_ENC, null)
        if (!enc.isNullOrBlank()) {
            return LocalAesVault.decrypt(ctx, enc)?.takeIf { it.isNotBlank() }
        }
        val legacy = p.getString(LEGACY_KEY_ACTIVE, null)?.takeIf { it.isNotBlank() }
        if (legacy != null) {
            setActiveContact(ctx, legacy)
        }
        return legacy
    }

    fun setActiveContact(ctx: Context, name: String) {
        val enc = LocalAesVault.encrypt(ctx, name)
        prefs(ctx).edit()
            .putString(KEY_ACTIVE_ENC, enc)
            .remove(LEGACY_KEY_ACTIVE)
            .apply()
    }

    fun allPassphrases(ctx: Context): List<String> = getContacts(ctx).values.toList()

    fun hasAnyPassphrase(ctx: Context): Boolean = getContacts(ctx).isNotEmpty()

    // ---------------------------------------------------------- encrypted message cache & draft

    /**
     * Secret message-এর লেখা বা ডিক্রিপ্ট করা মেসেজ লোকাল cache-এ রাখার **আগেই**
     * AES-256-GCM দিয়ে এনক্রিপ্ট করে সংরক্ষণ করে।
     */
    fun saveSecretCacheEntry(
        ctx: Context,
        contactName: String,
        plainText: String,
        glyphText: String,
        style: GlyphStyle,
        isOutgoing: Boolean,
    ) {
        if (plainText.isBlank() || glyphText.isBlank()) return
        val list = getSecretCacheEntries(ctx).toMutableList()
        // একই glyph ডুপ্লিকেট এড়িয়ে নতুনটি শুরুতে বসাই
        list.removeAll { it.glyphText == glyphText && it.isOutgoing == isOutgoing }
        list.add(
            0,
            SecretCacheEntry(
                id = "${System.currentTimeMillis()}_${list.size}",
                contactName = contactName.ifBlank { DEFAULT_CONTACT },
                plainText = plainText,
                glyphText = glyphText,
                styleName = style.label,
                isOutgoing = isOutgoing,
                timestampMs = System.currentTimeMillis(),
            )
        )
        val trimmed = list.take(MAX_CACHE_ITEMS)
        writeSecretCacheEncrypted(ctx, trimmed)
    }

    fun getSecretCacheEntries(ctx: Context): List<SecretCacheEntry> {
        val enc = prefs(ctx).getString(KEY_CACHE_ENC, null) ?: return emptyList()
        val raw = LocalAesVault.decrypt(ctx, enc) ?: return emptyList()
        val out = ArrayList<SecretCacheEntry>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    SecretCacheEntry(
                        id = o.optString("id", i.toString()),
                        contactName = o.optString("c", DEFAULT_CONTACT),
                        plainText = o.optString("p", ""),
                        glyphText = o.optString("g", ""),
                        styleName = o.optString("s", GlyphStyle.RUNIC.label),
                        isOutgoing = o.optBoolean("out", true),
                        timestampMs = o.optLong("ts", 0L),
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun removeSecretCacheEntry(ctx: Context, id: String) {
        val updated = getSecretCacheEntries(ctx).filterNot { it.id == id }
        writeSecretCacheEncrypted(ctx, updated)
    }

    fun clearSecretCache(ctx: Context) {
        prefs(ctx).edit().remove(KEY_CACHE_ENC).apply()
    }

    private fun writeSecretCacheEncrypted(ctx: Context, entries: List<SecretCacheEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("c", e.contactName)
                    put("p", e.plainText)
                    put("g", e.glyphText)
                    put("s", e.styleName)
                    put("out", e.isOutgoing)
                    put("ts", e.timestampMs)
                }
            )
        }
        val encrypted = LocalAesVault.encrypt(ctx, arr.toString())
        prefs(ctx).edit().putString(KEY_CACHE_ENC, encrypted).apply()
    }

    fun saveDraft(ctx: Context, draft: String) {
        if (draft.isBlank()) {
            clearDraft(ctx)
            return
        }
        val enc = LocalAesVault.encrypt(ctx, draft)
        prefs(ctx).edit().putString(KEY_DRAFT_ENC, enc).apply()
    }

    fun getDraft(ctx: Context): String {
        val enc = prefs(ctx).getString(KEY_DRAFT_ENC, null) ?: return ""
        return LocalAesVault.decrypt(ctx, enc).orEmpty()
    }

    fun clearDraft(ctx: Context) {
        prefs(ctx).edit().remove(KEY_DRAFT_ENC).apply()
    }

    /** Storage verification: সেভ করা ডেটা AES-256-GCM ভল্টে সুরক্ষিত আছে কি না। */
    fun isStorageAesSecured(ctx: Context): Boolean {
        val p = prefs(ctx)
        val hasPlainLegacy = p.contains(LEGACY_KEY_CONTACTS) ||
            p.contains(LEGACY_KEY_PASSPHRASE) ||
            p.contains(LEGACY_KEY_ACTIVE)
        if (hasPlainLegacy) return false
        val encContacts = p.getString(KEY_CONTACTS_ENC, null)
        val encCache = p.getString(KEY_CACHE_ENC, null)
        val contactsOk = encContacts == null || LocalAesVault.isEncryptedPayload(encContacts)
        val cacheOk = encCache == null || LocalAesVault.isEncryptedPayload(encCache)
        return contactsOk && cacheOk
    }

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

    // -------------------------------------------------------- auto-detect & clipboard monitor service

    /** কিবোর্ড-এর বাইরে screen scan করে auto-decode bubble দেখানো চালু/বন্ধ (ডিফল্ট: বন্ধ)। */
    fun autoDetectEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTODETECT, false)

    fun setAutoDetect(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AUTODETECT, enabled).apply()
    }

    /** ব্যাকগ্রাউন্ড Clipboard Monitor Service চালু/বন্ধ (ডিফল্ট: চালু)। */
    fun clipboardMonitorEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CLIPBOARD_SERVICE, true)

    fun setClipboardMonitorEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CLIPBOARD_SERVICE, enabled).apply()
    }

    private const val KEY_CLIPBOARD_SERVICE = "clipboard_monitor_service"
}
