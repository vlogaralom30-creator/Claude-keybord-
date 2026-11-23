package com.banglakeys.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Voice typing: ফোনের built-in speech recognizer দিয়ে কথা বলে বাংলা/English লেখা।
 * RECORD_AUDIO permission লাগবে, এবং recognizer সাধারণত internet ব্যবহার করে।
 *
 * v2 ফিক্স (মাইক নিজে নিজে বন্ধ হয়ে যাওয়া / কিছু না শোনা):
 *  - Continuous: একবার 🎤 চাপলে বারবার নিজে থেকে নতুন session শুরু করে; শুধু আপনি আবার 🎤
 *    চাপলে, অনেকক্ষণ চুপ থাকলে বা বড় error হলে বন্ধ হয়।
 *  - প্রতিটা session শেষে recognizer destroy হয় (আগে leak হয়ে BUSY/CLIENT error দিত)।
 *  - BUSY/CLIENT error-এ নিজে retry করে; bn-BD না পেলে bn-IN, শেষে en-US fallback।
 *  - stop() করার পর callback না এলে watchdog জোর করে পরিষ্কার করে — [listening] আটকে থাকে না।
 *  - [onVoiceEnd] সব পথে (error সহ) ঠিক একবার call হয়, তাই assistant আর "paused" থেকে যায় না।
 */
class VoiceInput(private val context: Context) {

    interface Listener {
        fun onVoicePartial(text: String)
        fun onVoiceResult(text: String)
        fun onVoiceError(message: String)
        fun onVoiceEnd()

        /** Recognizer সত্যিই শোনার জন্য তৈরি — এখন বললে ধরবে। */
        fun onVoiceReady() {}
    }

    var listener: Listener? = null

    /** true = আমরা চাই মাইক চালু থাকুক (session-এর মাঝের ছোট বিরতিতেও true)। */
    var listening = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var bangla = true
    private var langIndex = 0
    private var emptyStreak = 0
    private var retryCount = 0
    private var sessionGotText = false

    private val restart = Runnable { beginSession() }
    private val stopWatchdog = Runnable { finish() }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** [bangla] অনুযায়ী bn-BD বা en-US locale দিয়ে শোনা শুরু করে। */
    fun start(bangla: Boolean) {
        if (listening) return
        if (!isAvailable()) {
            listener?.onVoiceError("এই ফোনে voice typing পাওয়া যাচ্ছে না (Google app/Speech Services চালু আছে?)")
            listener?.onVoiceEnd()
            return
        }
        this.bangla = bangla
        langIndex = 0
        emptyStreak = 0
        retryCount = 0
        listening = true
        // আগের (stop() করা) session-এর বাকি কাজ বাতিল — নইলে watchdog নতুন session মেরে ফেলত
        handler.removeCallbacks(stopWatchdog)
        releaseRecognizer()
        // assistant service-এর recognizer ছাড়ার জন্য একটু সময় দিই
        handler.removeCallbacks(restart)
        handler.postDelayed(restart, 250)
    }

    /** ব্যবহারকারী নিজে বন্ধ করল: বর্তমান কথা শেষ করে result দিয়ে থামে। */
    fun stop() {
        if (!listening) return
        handler.removeCallbacks(restart)
        val r = recognizer
        if (r == null) {
            finish()
            return
        }
        // এরপর আর নতুন session শুরু হবে না
        listening = false
        try {
            r.stopListening()
        } catch (_: Exception) {
            finish()
            return
        }
        handler.removeCallbacks(stopWatchdog)
        handler.postDelayed(stopWatchdog, 2500)
    }

    /** IME-র onDestroy() থেকে call করুন। */
    fun destroy() {
        val wasActive = listening || recognizer != null
        listening = false
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        if (wasActive) listener?.onVoiceEnd()
    }

    // ----------------------------------------------------------------- internals

    private fun languageTag(): String {
        val list = if (bangla) listOf("bn-BD", "bn-IN", "en-US") else listOf("en-US")
        return list[langIndex.coerceAtMost(list.size - 1)]
    }

    private fun hasMoreLanguages(): Boolean = bangla && langIndex < 2

    private fun releaseRecognizer() {
        val r = recognizer ?: return
        recognizer = null
        try {
            r.setRecognitionListener(null)
            r.cancel()
        } catch (_: Exception) {
        }
        try {
            r.destroy()
        } catch (_: Exception) {
        }
    }

    private fun finish() {
        handler.removeCallbacks(restart)
        handler.removeCallbacks(stopWatchdog)
        val wasActive = listening || recognizer != null
        listening = false
        releaseRecognizer()
        if (wasActive) listener?.onVoiceEnd()
    }

    private fun scheduleRestart(delayMs: Long) {
        handler.removeCallbacks(restart)
        if (!listening) {
            finish()
            return
        }
        handler.postDelayed(restart, delayMs)
    }

    private fun beginSession() {
        if (!listening) return
        releaseRecognizer()
        sessionGotText = false
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            null
        }
        if (r == null) {
            listener?.onVoiceError("Speech recognizer চালু করা যায়নি")
            finish()
            return
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                retryCount = 0
                listener?.onVoiceReady()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                handleError(error)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    sessionGotText = true
                    emptyStreak = 0
                    listener?.onVoiceResult(text)
                } else {
                    emptyStreak++
                }
                nextOrFinish()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    emptyStreak = 0
                    listener?.onVoicePartial(text)
                }
            }
        })

        val tag = languageTag()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // একটু থামলেই কেটে না দিতে (সব recognizer মানে না, কিন্তু Google-এরটা মানে)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L)
        }
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            listener?.onVoiceError("মাইক চালু করা যায়নি")
            finish()
        }
    }

    /** একটা session শেষ হলো: user এখনো চাইলে নতুন session, নইলে পুরো শেষ। */
    private fun nextOrFinish() {
        if (!listening) {
            finish()
            return
        }
        // অনেকক্ষণ কিছু বলা হয়নি (~৩ খালি session) — বন্ধ করি, ব্যাটারি/মাইক বাঁচাতে
        if (emptyStreak >= 3) {
            listener?.onVoiceError("অনেকক্ষণ কিছু শোনা যায়নি, মাইক বন্ধ করা হলো")
            finish()
            return
        }
        scheduleRestart(300)
    }

    private fun handleError(error: Int) {
        // stop() চাপার পর আসা error: শান্তভাবে শেষ
        if (!listening) {
            finish()
            return
        }
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                emptyStreak++
                nextOrFinish()
            }

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> {
                if (retryCount < 3) {
                    retryCount++
                    releaseRecognizer()
                    scheduleRestart(500L * retryCount)
                } else {
                    listener?.onVoiceError("মাইক এখন অন্য কাজে ব্যস্ত — একটু পরে আবার চাপুন")
                    finish()
                }
            }

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                listener?.onVoiceError("মাইক্রোফোন permission দিন")
                finish()
            }

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER -> {
                listener?.onVoiceError("ইন্টারনেট লাগবে (Google voice service)")
                finish()
            }

            // 12 = ERROR_LANGUAGE_NOT_SUPPORTED, 13 = ERROR_LANGUAGE_UNAVAILABLE (API 31+)
            12, 13 -> {
                if (hasMoreLanguages()) {
                    langIndex++
                    releaseRecognizer()
                    scheduleRestart(200)
                } else {
                    listener?.onVoiceError("এই ভাষার voice মডেল ফোনে নেই — Google Speech Services-এ ভাষা ডাউনলোড করুন")
                    finish()
                }
            }

            else -> {
                if (retryCount < 2) {
                    retryCount++
                    releaseRecognizer()
                    scheduleRestart(600)
                } else {
                    listener?.onVoiceError("Voice typing কাজ করলো না, আবার চেষ্টা করুন")
                    finish()
                }
            }
        }
    }
}
