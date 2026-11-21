package com.banglakeys.app

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone

object ContactLookup {

    /** নাম (বা তার alias গ্রুপ) দিয়ে প্রথম মিলে যাওয়া contact-এর নম্বর। */
    fun findNumber(ctx: Context, spoken: String): String? {
        if (!AssistantSetup.has(ctx, android.Manifest.permission.READ_CONTACTS)) return null
        val q = spoken.trim().lowercase()
        if (q.isEmpty()) return null

        val names = LinkedHashSet<String>()
        names.add(q)
        for (g in AssistantConfig.CONTACT_GROUPS) {
            if (g.any { it.lowercase() == q }) names.addAll(g)
        }

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
}
