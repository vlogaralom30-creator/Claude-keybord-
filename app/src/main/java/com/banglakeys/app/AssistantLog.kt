package com.banglakeys.app

import android.util.Log
import com.example.BuildConfig

/**
 * Assistant-এর কেন্দ্রীয় লগার।
 * Logcat-এ দেখার জন্য: `adb logcat -s BKAssistant`
 *
 * release APK-তে ডিফল্টে লগ বন্ধ (আগে সবসময় চালু ছিল)। CI-র release APK-তে ডিবাগ করতে
 * হলে শুধু সেই সময়ের জন্য FORCE_LOG = true করো, কাজ শেষে আবার false।
 * মেসেজ/কথার আসল লেখা (Command-এর ভেতরের text) কখনো লগে যায় না — শুধু Command-এর ধরন।
 */
object AssistantLog {
    private const val FORCE_LOG = false

    // BuildConfig.DEBUG compile-time const নয় (নতুন AGP), তাই const val নয়
    val DEBUG: Boolean = FORCE_LOG || BuildConfig.DEBUG
    const val TAG = "BKAssistant"

    fun d(msg: String) {
        if (DEBUG) Log.d(TAG, msg)
    }

    fun w(msg: String, tr: Throwable? = null) {
        if (DEBUG) Log.w(TAG, msg, tr)
    }
}
