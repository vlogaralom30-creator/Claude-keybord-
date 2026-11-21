package com.banglakeys.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Voice typing: ফোনের built-in speech recognizer দিয়ে কথা বলে বাংলা/English লেখা।
 * RECORD_AUDIO permission লাগবে, এবং recognizer সাধারণত internet ব্যবহার করে
 * (ফোনের ডিফল্ট recognizer, কোনো external library/API key লাগে না)।
 */
class VoiceInput(private val context: Context) {

    interface Listener {
        fun onVoicePartial(text: String)
        fun onVoiceResult(text: String)
        fun onVoiceError(message: String)
        fun onVoiceEnd()
    }

    var listener: Listener? = null
    var listening = false
        private set

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** [bangla] অনুযায়ী bn-BD বা en-US locale দিয়ে শোনা শুরু করে। */
    fun start(bangla: Boolean) {
        if (listening) return
        if (!isAvailable()) {
            listener?.onVoiceError("এই ফোনে voice typing পাওয়া যাচ্ছে না")
            return
        }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                listening = false
                listener?.onVoiceError(errorText(error))
                listener?.onVoiceEnd()
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) listener?.onVoiceResult(text)
                listener?.onVoiceEnd()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) listener?.onVoicePartial(text)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (bangla) "bn-BD" else "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listening = true
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
    }

    /** IME-র onDestroy() থেকে call করুন। */
    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "কিছু বোঝা যায়নি, আবার বলুন"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কোনো শব্দ পাওয়া যায়নি"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ইন্টারনেট লাগবে"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "মাইক্রোফোন permission দিন"
        else -> "Voice typing কাজ করলো না, আবার চেষ্টা করুন"
    }
}
