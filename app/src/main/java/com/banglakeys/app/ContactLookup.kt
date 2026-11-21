package com.banglakeys.app

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone

object ContactLookup {

    private fun expandAliases(spoken: String): LinkedHashSet<String> {
        val q = spoken.trim().lowercase()
        val names = LinkedHashSet<String>()
        if (q.isEmpty()) return names
        names.add(q)
        for (g in AssistantConfig.CONTACT_GROUPS) {
            if (g.any { it.lowercase() == q }) names.addAll(g)
        }
        return names
    }

    /** নাম (বা তার alias গ্রুপ) দিয়ে প্রথম মিলে যাওয়া contact-এর নম্বর। */
    fun findNumber(ctx: Context, spoken: String): String? {
        if (!AssistantSetup.has(ctx, android.Manifest.permission.READ_CONTACTS)) return null
        val names = expandAliases(spoken)
        if (names.isEmpty()) return null

        for (name in names) {
            ctx.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Phone.NUMBER),
                "${Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        }
        return null
    }

    /** নাম (বা তার alias গ্রুপ) দিয়ে প্রথম মিলে যাওয়া contact-এর ইমেইল। */
    fun findEmail(ctx: Context, spoken: String): String? {
        if (!AssistantSetup.has(ctx, android.Manifest.permission.READ_CONTACTS)) return null
        val names = expandAliases(spoken)
        if (names.isEmpty()) return null

        for (name in names) {
            ctx.contentResolver.query(
                Email.CONTENT_URI,
                arrayOf(Email.ADDRESS),
                "${Email.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )?.use { c ->
                if (c.moveToFirst()) return c.getString(0)
            }
        }
        return null
    }
}
