package com.banglakeys.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * ব্যাকগ্রাউন্ড [SecretClipboardMonitorService] এবং [BanglaIME] / [SecretDecodeBannerView]-এর মধ্যে
 * রিয়েল-টাইম Decrypted Secret Bar ট্রিগার করার কেন্দ্রীয় স্টেট হোল্ডার।
 *
 * - ম্যাচ শনাক্ত হলে টাইমস্ট্যাম্পসহ ইভেন্ট প্রকাশ করে, যাতে কিবোর্ড খোলা থাকলে সাথে সাথে
 *   এবং কিবোর্ড পরমুহূর্তে খুললেও বাকি ১৫ সেকেন্ডের ভেতর ব্যানার দেখায়।
 * - মেয়াদ শেষ হলে বা ডিসমিস করলে মেমোরি থেকে প্লেইনটেক্সট ক্লিয়ার করে।
 */
object SecretBannerStateHolder {

    sealed class BannerEvent {
        object Hidden : BannerEvent()

        data class Decoded(
            val id: Long,
            val senderName: String,
            val plainText: String,
            val cipherText: String,
            val style: GlyphStyle,
            val expiresAtMs: Long,
        ) : BannerEvent()

        data class Unrecognized(
            val id: Long,
            val cipherText: String,
            val expiresAtMs: Long,
        ) : BannerEvent()
    }

    private val eventIdGen = AtomicLong(0L)
    private val _events = MutableStateFlow<BannerEvent>(BannerEvent.Hidden)
    val events: StateFlow<BannerEvent> = _events.asStateFlow()

    @Volatile
    private var sharedDetector: SecretClipboardDetector? = null

    fun detector(context: Context): SecretClipboardDetector {
        return sharedDetector ?: synchronized(this) {
            sharedDetector ?: SecretClipboardDetector(context.applicationContext).also {
                sharedDetector = it
            }
        }
    }

    fun publish(
        result: SecretClipboardDetector.DetectionResult,
        nowMs: Long = System.currentTimeMillis(),
    ): BannerEvent {
        val nextId = eventIdGen.incrementAndGet()
        val event = when (result) {
            is SecretClipboardDetector.DetectionResult.Decoded -> BannerEvent.Decoded(
                id = nextId,
                senderName = result.senderName,
                plainText = result.plainText,
                cipherText = result.cipherText,
                style = result.style,
                expiresAtMs = nowMs + SecretDecodeBannerView.DEFAULT_AUTO_HIDE_MS,
            )
            is SecretClipboardDetector.DetectionResult.Unrecognized -> BannerEvent.Unrecognized(
                id = nextId,
                cipherText = result.cipherText,
                expiresAtMs = nowMs + SecretDecodeBannerView.UNKNOWN_AUTO_HIDE_MS,
            )
        }
        _events.value = event
        return event
    }

    fun activeEvent(nowMs: Long = System.currentTimeMillis()): BannerEvent {
        val current = _events.value
        val expired = when (current) {
            is BannerEvent.Hidden -> false
            is BannerEvent.Decoded -> nowMs >= current.expiresAtMs
            is BannerEvent.Unrecognized -> nowMs >= current.expiresAtMs
        }
        if (expired) {
            _events.value = BannerEvent.Hidden
            return BannerEvent.Hidden
        }
        return current
    }

    fun dismiss() {
        _events.value = BannerEvent.Hidden
    }

    internal fun resetForTest() {
        _events.value = BannerEvent.Hidden
        sharedDetector = null
    }
}
