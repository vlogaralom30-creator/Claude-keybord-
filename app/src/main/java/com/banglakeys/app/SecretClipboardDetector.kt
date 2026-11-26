package com.banglakeys.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Clipboard-এ কপি করা Secret Cipher (Runic / Dots / Shapes) স্বয়ংক্রিয়ভাবে শনাক্ত করে
 * ব্যাকগ্রাউন্ড থ্রেডে AES-vault contact passphrase দিয়ে ডিক্রিপ্ট করে।
 *
 * - PBKDF2-HMAC-SHA256 (100k iterations) কখনো UI থ্রেডে চলে না।
 * - একই কপি বারবার ডিক্রিপ্ট না করতে deduplicate করে।
 * - ব্যবহারকারীর নিজের পাঠানো/এনক্রিপ্ট করা cipher ignore করে।
 */
class SecretClipboardDetector(
    private val context: Context,
    private val workerExecutor: Executor = defaultExecutor,
    private val mainExecutor: Executor = Executor { runnable ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            Handler(Looper.getMainLooper()).post(runnable)
        }
    },
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {

    sealed class DetectionResult {
        data class Decoded(
            val senderName: String,
            val plainText: String,
            val cipherText: String,
            val style: GlyphStyle,
        ) : DetectionResult()

        data class Unrecognized(
            val cipherText: String,
        ) : DetectionResult()
    }

    @Volatile
    private var lastProcessedCipher: String? = null

    @Volatile
    private var ignoredCipher: String? = null

    @Volatile
    private var ignoredUntilMs: Long = 0L

    /** নিজের সদ্য পাঠানো/কপি করা cipher-কে incoming হিসেবে শনাক্ত হওয়া থেকে আটকায়। */
    fun ignoreOutgoingCipher(cipherText: String) {
        val clean = cipherText.trim()
        if (clean.isEmpty()) return
        ignoredCipher = clean
        ignoredUntilMs = clockMs() + IGNORE_WINDOW_MS
        lastProcessedCipher = clean
    }

    /** ম্যানুয়ালি একই লেখা আবার ডিকোড করতে চাইলে dedup রিসেট করে। */
    fun resetDeduplication() {
        lastProcessedCipher = null
    }

    fun inspect(rawClipboardText: String?, force: Boolean = false, onResult: (DetectionResult) -> Unit) {
        val whole = rawClipboardText?.trim().orEmpty()
        if (whole.isEmpty()) return

        // আশেপাশের নাম/সময় বাদ দিয়ে শুধু glyph অংশ (dedup ও ignore-ও এই অংশ ধরেই চলে)
        val candidate = GlyphAlphabet.extractGlyphRun(whole) ?: return

        val now = clockMs()
        if (!force) {
            val ignored = ignoredCipher
            if (ignored != null && now <= ignoredUntilMs && ignored == candidate) {
                return
            }
            if (candidate == lastProcessedCipher) {
                return
            }
        }

        lastProcessedCipher = candidate
        val style = GlyphAlphabet.detectStyle(candidate) ?: GlyphStyle.RUNIC

        workerExecutor.execute {
            // Keystore decrypt main thread-এ নয়, worker-এ
            val contacts = SecretConfig.getContacts(context)
            val bytes = GlyphAlphabet.decodeAny(candidate)
            val match = if (bytes != null && contacts.isNotEmpty()) {
                SecretCipher.decryptWithMatchingContact(bytes, contacts)
            } else {
                null
            }

            if (match != null) {
                SecretConfig.saveSecretCacheEntry(
                    ctx = context,
                    contactName = match.first,
                    plainText = match.second,
                    glyphText = candidate,
                    style = style,
                    isOutgoing = false,
                )
            }

            val result = if (match != null) {
                DetectionResult.Decoded(
                    senderName = match.first,
                    plainText = match.second,
                    cipherText = candidate,
                    style = style,
                )
            } else {
                DetectionResult.Unrecognized(cipherText = candidate)
            }

            mainExecutor.execute {
                onResult(result)
            }
        }
    }

    companion object {
        private const val IGNORE_WINDOW_MS = 10_000L
        private val defaultExecutor: Executor = Executors.newSingleThreadExecutor()
    }
}
