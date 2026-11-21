package com.banglakeys.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast

/**
 * সারাক্ষণ শোনা Foreground Service। "Hey Bot ..." শুনলে কমান্ড চালায়।
 * Wake word বা কমান্ডের পর AWAKE_WINDOW_MS পর্যন্ত Hey Bot ছাড়াই পরপর কমান্ড নেয়
 * (যেমন: "hey bot open tiktok" -> "up" -> "up" -> "like")।
 */
class AssistantListenerService : Service() {

    companion object {
        const val ACTION_STOP = "com.banglakeys.app.ASSISTANT_STOP"
        private const val CHANNEL_ID = "assistant_listen"
        private const val NOTIF_ID = 4201

        @Volatile
        var running: Boolean = false
            private set

        private var instance: AssistantListenerService? = null

        fun start(ctx: Context) {
            val i = Intent(ctx, AssistantListenerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AssistantListenerService::class.java))
        }

        /** Keyboard-এর নিজের voice typing চলার সময় true দিন, শেষ হলে false (mic conflict এড়াতে)। */
        fun setPaused(paused: Boolean) {
            instance?.pauseInternal(paused)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private lateinit var executor: CommandExecutor
    private var awakeUntil = 0L
    private var paused = false
    private val restart = Runnable { listenOnce() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (!AssistantSetup.hasMic(this)) {
            toast("মাইক্রোফোন permission দিন")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            executor = CommandExecutor(applicationContext)
            running = true
            instance = this
            listenOnce()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    // ------------------------------------------------------------ listening

    private fun scheduleNext(delayMs: Long) {
        handler.removeCallbacks(restart)
        handler.postDelayed(restart, delayMs)
    }

    private fun pauseInternal(p: Boolean) {
        paused = p
        if (p) {
            handler.removeCallbacks(restart)
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        } else if (running) {
            scheduleNext(400)
        }
    }

    private fun listenOnce() {
        if (!running || paused) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("এই ফোনে speech recognizer নেই")
            stopSelf()
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    toast("মাইক্রোফোন permission দিন")
                    stopSelf()
                    return
                }
                val delay = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> 3000L
                    else -> 250L // NO_MATCH, SPEECH_TIMEOUT: স্বাভাবিক, আবার শোনো
                }
                scheduleNext(delay)
            }

            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                handleResults(list)
                scheduleNext(250)
            }
        })

        val lang = if (AssistantSetup.isBangla(this)) "bn-BD" else "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        r.startListening(intent)
    }

    // ------------------------------------------------------------- commands

    private fun handleResults(candidates: List<String>) {
        if (candidates.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        var unknown: String? = null

        for (text in candidates) {
            val (woke, rest) = CommandParser.splitWake(text)
            val awake = woke || now < awakeUntil
            if (!awake) continue

            if (woke && rest.isBlank()) {
                awakeUntil = now + AssistantConfig.AWAKE_WINDOW_MS
                toast("🎤 বলুন…")
                return
            }
            val cmd = CommandParser.parse(rest)
            if (cmd == null) {
                if (unknown == null) unknown = rest
                continue
            }
            awakeUntil = now + AssistantConfig.AWAKE_WINDOW_MS
            when (cmd) {
                Command.Sleep -> {
                    awakeUntil = 0L
                    toast("😴 ঘুমালাম — \"Hey Bot\" বললে জাগবো")
                }
                Command.StopAssistant -> {
                    toast("🤖 Assistant বন্ধ")
                    stopSelf()
                }
                else -> toast(executor.execute(cmd))
            }
            return
        }
        if (unknown != null) toast("বুঝিনি: $unknown")
    }

    private fun toast(msg: String) {
        if (msg.isBlank()) return
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    // --------------------------------------------------------- notification

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Voice Assistant", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, AssistantListenerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 1, Intent(this, AssistantActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        @Suppress("DEPRECATION")
        val n = b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BanglaKeys Assistant চালু")
            .setContentText("\"Hey Bot\" বলে কমান্ড দিন")
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "বন্ধ করুন", stopPi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }
}
