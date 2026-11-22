package com.banglakeys.app

import android.content.Context
import android.provider.ContactsContract

object ContactLookup {

    private fun expandAliases(spoken: String): List<String> {
        val q = spoken.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val names = LinkedHashSet<String>()
        names.add(q)
        for (g in AssistantConfig.CONTACT_GROUPS) {
            if (g.any { it.lowercase() == q }) names.addAll(g)
        }
        return names.toList()
    }

    /** নাম (বা তার alias গ্রুপ) দিয়ে প্রথম মিলে যাওয়া contact-এর নম্বর। */
    fun findNumber(ctx: Context, spoken: String): String? {
        if (!AssistantSetup.has(ctx, android.Manifest.permission.READ_CONTACTS)) return null
        val names = expandAliases(spoken)
        if (names.isEmpty()) return null

        for (name in names) {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        }
        return null
    }

    /** নাম (বা তার alias গ্রুপ) দিয়ে প্রথম মিলে যাওয়া contact-এর ইমেইল ঠিকানা। */
    fun findEmail(ctx: Context, spoken: String): String? {
        if (!AssistantSetup.has(ctx, android.Manifest.permission.READ_CONTACTS)) return null
        val names = expandAliases(spoken)
        if (names.isEmpty()) return null

        for (name in names) {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val addr = c.getString(0)?.trim()
                    if (!addr.isNullOrEmpty()) return addr
                }
            }
        }
        return null
    }
}
