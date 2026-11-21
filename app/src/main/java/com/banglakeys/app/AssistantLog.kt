package com.banglakeys.app

import android.util.Log

/**
 * Assistant-এর কেন্দ্রীয় লগার।
 * Logcat-এ দেখার জন্য: `adb logcat -s BKAssistant`
 */
object AssistantLog {
    const val DEBUG = true
    const val TAG = "BKAssistant"

    fun d(msg: String) {
        if (DEBUG) Log.d(TAG, msg)
    }

    fun w(msg: String, tr: Throwable? = null) {
        if (DEBUG) Log.w(TAG, msg, tr)
    }
}
