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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

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
        private const val CONFIRM_TIMEOUT_MS = 10_000L

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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private lateinit var executor: CommandExecutor
    private var awakeUntil = 0L
    private var paused = false
    private val restart = Runnable { listenOnce() }

    // Minimal T4 point 1 confirmation state
    private var pending: Command? = null
    private val confirmTimeoutRunnable = Runnable {
        if (pending != null) {
            AssistantLog.d("Confirmation timeout for $pending")
            clearPending()
            toast("⌛ সময় শেষ, বাতিল হয়েছে")
        }
    }

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
            AssistantLog.d("AssistantListenerService started")
            listenOnce()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        clearPending()
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        AssistantLog.d("AssistantListenerService destroyed")
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

    // ------------------------------------------------------------- confirmation (T4 minimal)

    private fun setPending(cmd: Command, question: String) {
        clearPending()
        pending = cmd
        handler.postDelayed(confirmTimeoutRunnable, CONFIRM_TIMEOUT_MS)
        AssistantLog.d("Pending confirm set: $cmd -> $question")
        toast(question)
    }

    private fun clearPending() {
        pending = null
        handler.removeCallbacks(confirmTimeoutRunnable)
    }

    private fun confirmationQuestion(cmd: Command): String? = when (cmd) {
        is Command.AppCall -> {
            if (AssistantPrefs.confirmBeforeAppCall(this)) {
                "${cmd.appName}-তে ${cmd.contact}-কে ${if (cmd.video) "video " else ""}call করব? হ্যাঁ/না"
            } else null
        }
        is Command.AppMessage -> {
            if (cmd.send && AssistantPrefs.confirmBeforeHumanSend(this)) {
                "${cmd.appName}-তে ${cmd.contact}-কে মেসেজ পাঠাব? হ্যাঁ/না"
            } else null
        }
        is Command.AppChat -> {
            if (cmd.send && AssistantPrefs.confirmBeforeAiChatSend(this)) {
                "${cmd.appName ?: "এই app"}-এ পাঠাব? হ্যাঁ/না"
            } else null
        }
        else -> null
    }

    private fun matchConfirmReply(candidates: List<String>): Boolean? {
        for (raw in candidates) {
            val (_, rest) = CommandParser.splitWake(raw)
            val norm = rest.lowercase().trim()
            val tokens = norm.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (norm in CommandParser.CONFIRM_YES_WORDS || tokens.any { it in CommandParser.CONFIRM_YES_WORDS }) {
                return true
            }
            if (norm in CommandParser.CONFIRM_NO_WORDS || tokens.any { it in CommandParser.CONFIRM_NO_WORDS }) {
                return false
            }
        }
        return null
    }

    // ------------------------------------------------------------- commands

    private fun handleResults(candidates: List<String>) {
        if (candidates.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        AssistantLog.d("Heard: $candidates (awake=${now < awakeUntil}, pending=${pending != null})")

        // 1. Check pending confirmation first
        val currentPending = pending
        if (currentPending != null) {
            // PanicStop overrides waiting confirmation
            for (text in candidates) {
                val (woke, rest) = CommandParser.splitWake(text)
                val targetText = if (woke) rest else text
                if (CommandParser.parse(targetText) == Command.PanicStop) {
                    dispatchCommand(Command.PanicStop)
                    return
                }
            }
            when (matchConfirmReply(candidates)) {
                true -> {
                    clearPending()
                    awakeUntil = now + AssistantConfig.AWAKE_WINDOW_MS
                    AssistantLog.d("Confirmation YES -> executing $currentPending")
                    dispatchCommand(currentPending)
                    return
                }
                false -> {
                    clearPending()
                    AssistantLog.d("Confirmation NO -> cancelled $currentPending")
                    toast("❌ বাতিল হয়েছে")
                    return
                }
                null -> {
                    AssistantLog.d("Waiting confirmation (ignored unrelated utterance)")
                    return
                }
            }
        }

        // 2. Normal wake / awake command parsing
        var unknown: String? = null
        for (text in candidates) {
            val (woke, rest) = CommandParser.splitWake(text)
            val awake = woke || now < awakeUntil
            if (!awake) continue

            if (woke && rest.isBlank()) {
                awakeUntil = now + AssistantConfig.AWAKE_WINDOW_MS
                AssistantLog.d("Woke up (no command yet)")
                toast("🎤 বলুন…")
                return
            }
            val cmd = CommandParser.parse(rest)
            AssistantLog.d("Parse '$rest' (woke=$woke) -> $cmd")
            if (cmd == null) {
                if (unknown == null) unknown = rest
                continue
            }
            awakeUntil = now + AssistantConfig.AWAKE_WINDOW_MS

            val question = confirmationQuestion(cmd)
            if (question != null) {
                setPending(cmd, question)
                return
            }

            dispatchCommand(cmd)
            return
        }
        if (unknown != null) toast("বুঝিনি: $unknown")
    }

    private fun dispatchCommand(cmd: Command) {
        when (cmd) {
            Command.Sleep -> {
                awakeUntil = 0L
                toast("😴 ঘুমালাম — \"Hey Bot\" বললে জাগবো")
            }
            Command.StopAssistant -> {
                toast("🤖 Assistant বন্ধ")
                stopSelf()
            }
            Command.PanicStop -> {
                clearPending()
                serviceScope.coroutineContext.cancelChildren()
                awakeUntil = 0L
                AssistantLog.d("PanicStop executed — cancelled pending and running flows")
                toast("🛑 সব কাজ বন্ধ — ঘুমালাম")
            }
            else -> {
                if (executor.isAsync(cmd)) {
                    serviceScope.launch {
                        val feedback = executor.executeAsync(cmd)
                        toast(feedback)
                    }
                } else {
                    toast(executor.execute(cmd))
                }
            }
        }
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
