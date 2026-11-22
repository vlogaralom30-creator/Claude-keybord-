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
 * আপগ্রেডেড 2-mode (Continuous + Quiet Push-to-Talk) Voice Assistant Foreground Service।
 *
 * - লাইভ স্ক্রিন ওভারলে পিল (CommandOverlayManager) দিয়ে ইউজারের বলা কথা সাথে সাথে দেখায়।
 * - SmartCommandResolver দিয়ে সব N-best candidate + Banglish/Bangla fuzzy মিলিয়ে
 *   word-perfect কমান্ড নির্ণয় করে।
 * - Call ও Message সেন্ডের আগে ওভারলে পিলে inline হ্যাঁ/না বাটন ও ভয়েস confirm দেয়।
 */
class AssistantListenerService : Service() {

    companion object {
        const val ACTION_STOP = "com.banglakeys.app.ASSISTANT_STOP"
        const val ACTION_TRIGGER_PTT = "com.banglakeys.app.ASSISTANT_TRIGGER_PTT"
        const val ACTION_REFRESH_CONFIG = "com.banglakeys.app.ASSISTANT_REFRESH_CONFIG"
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

        fun triggerPtt(ctx: Context) {
            val active = instance
            if (running && active != null) {
                active.handler.post { active.triggerPttInternal() }
            } else {
                val i = Intent(ctx, AssistantListenerService::class.java).setAction(ACTION_TRIGGER_PTT)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            }
        }

        fun notifyConfigChanged(ctx: Context) {
            val active = instance ?: return
            active.handler.post { active.applyConfigAndIdleUi() }
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
    private var overlay: CommandOverlayManager? = null

    private var awakeUntil = 0L
    private var paused = false
    private var pttSessionActive = false
    private val restart = Runnable { listenOnce() }

    private var pending: Command? = null
    private var pendingHeardText: String = ""
    private var pendingSummary: String = ""
    private val confirmTimeoutRunnable = Runnable {
        if (pending != null) {
            AssistantLog.d("Confirmation timeout for $pending")
            clearPending()
            showResult(
                heard = null,
                summary = null,
                msg = "সময় শেষ, বাতিল হয়েছে",
                ok = false,
                canRetry = false
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_CONFIG -> {
                if (running) applyConfigAndIdleUi()
                return START_NOT_STICKY
            }
        }

        startAsForeground()
        if (!AssistantSetup.hasMic(this)) {
            toast("মাইক্রোফোন permission দিন")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) {
            executor = CommandExecutor(applicationContext)
            overlay = CommandOverlayManager(
                ctx = applicationContext,
                onConfirmClick = { confirmPendingFromUi() },
                onCancelClick = { cancelPendingFromUi() },
                onStopFlowClick = { dispatchCommand(Command.PanicStop, "Stop button", "সব কাজ বন্ধ") },
                onPttTriggerClick = { triggerPttInternal() },
                onDismissOrSleepClick = { sleepOrDormantInternal() }
            )
            running = true
            instance = this
            AssistantLog.d("AssistantListenerService started (mode=${AssistantPrefs.listeningMode(this)})")

            if (intent?.action == ACTION_TRIGGER_PTT) {
                triggerPttInternal()
            } else {
                applyConfigAndIdleUi()
            }
        } else if (intent?.action == ACTION_TRIGGER_PTT) {
            triggerPttInternal()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        clearPending()
        serviceScope.cancel()
        handler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        overlay?.hide()
        overlay = null
        AssistantLog.d("AssistantListenerService destroyed")
        super.onDestroy()
    }

    // ------------------------------------------------------------ mode & listening

    private fun isPttMode(): Boolean = AssistantPrefs.listeningMode(this) == ListeningMode.PUSH_TO_TALK

    private fun applyConfigAndIdleUi() {
        if (!running || paused) return
        handler.removeCallbacks(restart)
        if (!AssistantPrefs.overlayHudEnabled(this) || !AssistantSetup.canOverlay(this)) {
            overlay?.hide()
        }
        if (isPttMode()) {
            pttSessionActive = false
            destroyRecognizer()
            renderIdleOverlay()
        } else {
            renderIdleOverlay()
            listenOnce()
        }
    }

    private fun idleOverlayState(): CommandOverlayManager.OverlayState {
        return if (isPttMode()) {
            CommandOverlayManager.OverlayState.Dormant(AssistantSetup.isBangla(this))
        } else {
            val remSec = remainingAwakeSec()
            CommandOverlayManager.OverlayState.Listening(
                partialText = "",
                awakeSec = remSec,
                isPtt = false
            )
        }
    }

    private fun renderIdleOverlay() {
        overlay?.render(idleOverlayState())
    }

    private fun remainingAwakeSec(): Int? {
        val remMs = awakeUntil - SystemClock.elapsedRealtime()
        return if (remMs > 0L) ((remMs + 999L) / 1000L).toInt() else null
    }

    private fun triggerPttInternal() {
        if (!running || paused) return
        pttSessionActive = true
        awakeUntil = SystemClock.elapsedRealtime() + AssistantConfig.AWAKE_WINDOW_MS
        overlay?.render(
            CommandOverlayManager.OverlayState.Listening(
                partialText = "",
                awakeSec = null,
                isPtt = true
            )
        )
        handler.removeCallbacks(restart)
        listenOnce()
    }

    private fun sleepOrDormantInternal() {
        clearPending()
        awakeUntil = 0L
        if (isPttMode()) {
            pttSessionActive = false
            handler.removeCallbacks(restart)
            destroyRecognizer()
            renderIdleOverlay()
        } else {
            renderIdleOverlay()
        }
    }

    private fun scheduleNext(delayMs: Long) {
        if (isPttMode() && pending == null && !pttSessionActive) return
        handler.removeCallbacks(restart)
        handler.postDelayed(restart, delayMs)
    }

    private fun pauseInternal(p: Boolean) {
        paused = p
        if (p) {
            handler.removeCallbacks(restart)
            destroyRecognizer()
            overlay?.hide()
        } else if (running) {
            applyConfigAndIdleUi()
        }
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private fun listenOnce() {
        if (!running || paused) return
        if (isPttMode() && !pttSessionActive && pending == null) {
            renderIdleOverlay()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("এই ফোনে speech recognizer নেই")
            stopSelf()
            return
        }
        destroyRecognizer()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (pending == null) {
                    overlay?.render(
                        CommandOverlayManager.OverlayState.Listening(
                            partialText = "",
                            awakeSec = if (pttSessionActive) null else remainingAwakeSec(),
                            isPtt = pttSessionActive
                        )
                    )
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            // পুরো বাক্য শেষ হওয়ার অপেক্ষা না করে, এখন পর্যন্ত যা শোনা গেছে তা
            // লাইভ overlay-তে দেখায় ও awake window refresh করে।
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) return
                val now = SystemClock.elapsedRealtime()
                val smartWoke = SmartCommandResolver.splitWakeSmart(text).first
                val shouldShow = pttSessionActive || now < awakeUntil || smartWoke || looksLikeWakeStart(text)
                if (now < awakeUntil || smartWoke) {
                    awakeUntil = now + AssistantConfig.AWAKE_WINDOW_MS
                }
                if (shouldShow && pending == null) {
                    overlay?.render(
                        CommandOverlayManager.OverlayState.Listening(
                            partialText = text,
                            awakeSec = if (pttSessionActive) null else remainingAwakeSec(),
                            isPtt = pttSessionActive
                        )
                    )
                }
            }

            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    toast("মাইক্রোফোন permission দিন")
                    stopSelf()
                    return
                }
                if (isPttMode()) {
                    val wasPtt = pttSessionActive
                    pttSessionActive = false
                    if (pending != null) {
                        // Awaiting confirmation: keep UI buttons active, don't loop mic aggressively
                        return
                    }
                    if (wasPtt && error == SpeechRecognizer.ERROR_NO_MATCH) {
                        showResult(
                            heard = null,
                            summary = null,
                            msg = "কিছু শোনা যায়নি — আবার চেষ্টা করুন",
                            ok = false,
                            canRetry = true
                        )
                    } else {
                        renderIdleOverlay()
                    }
                    return
                }
                val delay = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> 3000L
                    else -> 250L
                }
                scheduleNext(delay)
            }

            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                handleResults(list)
                if (isPttMode()) {
                    pttSessionActive = false
                    if (pending != null) {
                        // One clean listen turn for voice Yes/No alongside overlay Confirm/Cancel buttons
                        scheduleNext(350)
                    }
                } else {
                    scheduleNext(250)
                }
            }
        })

        val lang = if (AssistantSetup.isBangla(this)) "bn-BD" else "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2500L)
            putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 2000L)
            putExtra("android.speech.extra.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 15000L)
        }
        r.startListening(intent)
    }

    private fun looksLikeWakeStart(text: String): Boolean {
        val low = text.trim().lowercase()
        return low.startsWith("hey") || low.startsWith("hay") || low.startsWith("hi ") ||
            low.startsWith("ok ") || low.startsWith("হেই") || low.startsWith("হে ") ||
            low.startsWith("হাই") || low.startsWith("ওকে")
    }

    // ------------------------------------------------------------- confirmation (overlay + voice)

    private fun setPending(cmd: Command, heardText: String, summary: String, question: String) {
        clearPending()
        pending = cmd
        pendingHeardText = heardText
        pendingSummary = summary
        handler.postDelayed(confirmTimeoutRunnable, CONFIRM_TIMEOUT_MS)
        AssistantLog.d("Pending confirm set: $cmd -> $question")
        overlay?.render(
            CommandOverlayManager.OverlayState.AwaitingConfirm(
                heardText = heardText,
                actionSummary = summary,
                question = question
            )
        )
        if (overlay?.isOverlayPermittedAndEnabled() != true) {
            toast(question)
        }
    }

    private fun clearPending() {
        pending = null
        pendingHeardText = ""
        pendingSummary = ""
        handler.removeCallbacks(confirmTimeoutRunnable)
    }

    private fun confirmPendingFromUi() {
        val current = pending ?: return
        val heard = pendingHeardText
        val summary = pendingSummary
        clearPending()
        awakeUntil = SystemClock.elapsedRealtime() + AssistantConfig.AWAKE_WINDOW_MS
        AssistantLog.d("Overlay Confirm tapped -> executing $current")
        dispatchCommand(current, heard, summary)
    }

    private fun cancelPendingFromUi() {
        if (pending == null) return
        val heard = pendingHeardText
        val summary = pendingSummary
        clearPending()
        AssistantLog.d("Overlay Cancel tapped")
        showResult(
            heard = heard,
            summary = summary,
            msg = "বাতিল হয়েছে",
            ok = false,
            canRetry = false
        )
    }

    private fun confirmationQuestion(cmd: Command): String? = when (cmd) {
        is Command.AppCall -> {
            if (AssistantPrefs.confirmBeforeAppCall(this)) {
                "${cmd.appName}-তে ${cmd.contact}-কে ${if (cmd.video) "video " else ""}call করব?"
            } else null
        }
        is Command.AppMessage -> {
            if (cmd.send && AssistantPrefs.confirmBeforeHumanSend(this)) {
                "${cmd.appName}-তে ${cmd.contact}-কে মেসেজ পাঠাব?"
            } else null
        }
        is Command.AppChat -> {
            if (cmd.send && AssistantPrefs.confirmBeforeAiChatSend(this)) {
                "${cmd.appName ?: "এই app"}-এ পাঠাব?"
            } else null
        }
        else -> null
    }

    private fun matchConfirmReply(candidates: List<String>): Boolean? {
        for (raw in candidates) {
            val (_, rest) = SmartCommandResolver.splitWakeSmart(raw)
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
        AssistantLog.d("Heard: $candidates (awake=${now < awakeUntil}, ptt=$pttSessionActive, pending=${pending != null})")

        // 1. Check pending confirmation first
        val currentPending = pending
        if (currentPending != null) {
            for (text in candidates) {
                val (woke, rest) = SmartCommandResolver.splitWakeSmart(text)
                val targetText = if (woke) rest else text
                if (CommandParser.parse(targetText) == Command.PanicStop) {
                    dispatchCommand(Command.PanicStop, text, "সব কাজ বন্ধ")
                    return
                }
            }
            when (matchConfirmReply(candidates)) {
                true -> {
                    confirmPendingFromUi()
                    return
                }
                false -> {
                    cancelPendingFromUi()
                    return
                }
                null -> {
                    AssistantLog.d("Waiting confirmation (ignored unrelated utterance)")
                    return
                }
            }
        }

        // 2. Smart multi-candidate + fuzzy command resolution
        val alreadyAwake = (now < awakeUntil) || pttSessionActive
        val res = SmartCommandResolver.resolve(candidates, alreadyAwake)

        if (res.woke && res.command == null && res.normalizedText.isBlank()) {
            awakeUntil = now + AssistantConfig.AWAKE_WINDOW_MS
            AssistantLog.d("Woke up (no command yet)")
            overlay?.render(
                CommandOverlayManager.OverlayState.Listening(
                    partialText = "",
                    awakeSec = remainingAwakeSec(),
                    isPtt = pttSessionActive
                )
            )
            if (overlay?.isOverlayPermittedAndEnabled() != true) {
                toast("🎤 বলুন…")
            }
            return
        }

        val cmd = res.command
        if (cmd == null) {
            if (alreadyAwake && res.heardText.isNotBlank()) {
                AssistantLog.d("Unrecognized while awake: '${res.heardText}'")
                showResult(
                    heard = res.heardText,
                    summary = null,
                    msg = "কমান্ড বুঝিনি — আবার বলুন",
                    ok = false,
                    canRetry = isPttMode()
                )
            }
            return
        }

        AssistantLog.d("Resolved (fuzzy=${res.fuzzyCorrected}): '${res.heardText}' -> $cmd")
        awakeUntil = now + AssistantConfig.AWAKE_WINDOW_MS

        val summary = res.summary ?: SmartCommandResolver.describe(cmd)
        val question = confirmationQuestion(cmd)
        if (question != null) {
            setPending(cmd, res.heardText, summary, question)
            return
        }

        dispatchCommand(cmd, res.heardText, summary)
    }

    private fun dispatchCommand(cmd: Command, heardText: String, summary: String) {
        when (cmd) {
            Command.Sleep -> {
                awakeUntil = 0L
                pttSessionActive = false
                showResult(
                    heard = heardText,
                    summary = summary,
                    msg = "ঘুমালাম — \"Hey Bot\" বললে জাগবো",
                    ok = true,
                    canRetry = false
                )
            }
            Command.StopAssistant -> {
                toast("Assistant বন্ধ")
                stopSelf()
            }
            Command.PanicStop -> {
                clearPending()
                serviceScope.coroutineContext.cancelChildren()
                awakeUntil = 0L
                pttSessionActive = false
                AssistantLog.d("PanicStop executed — cancelled pending and running flows")
                showResult(
                    heard = heardText,
                    summary = summary,
                    msg = "সব কাজ বন্ধ করা হয়েছে",
                    ok = false,
                    canRetry = false
                )
            }
            else -> {
                if (executor.isAsync(cmd)) {
                    overlay?.render(
                        CommandOverlayManager.OverlayState.RunningFlow(
                            appLabel = summary,
                            stepStatus = "শুরু হচ্ছে..."
                        )
                    )
                    serviceScope.launch {
                        val feedback = executor.executeAsync(cmd) { step ->
                            overlay?.render(
                                CommandOverlayManager.OverlayState.RunningFlow(
                                    appLabel = summary,
                                    stepStatus = step
                                )
                            )
                        }
                        val ok = !feedback.startsWith("❌") && !feedback.startsWith("⚠️") &&
                            !feedback.startsWith("⏱") && !feedback.startsWith("🔒")
                        showResult(
                            heard = heardText,
                            summary = summary,
                            msg = feedback,
                            ok = ok,
                            canRetry = false
                        )
                    }
                } else {
                    val feedback = executor.execute(cmd)
                    val ok = !feedback.startsWith("❌") && !feedback.startsWith("⚠️") &&
                        !feedback.contains("পাইনি") && !feedback.contains("নির্দিষ্ট নাম বলুন")
                    showResult(
                        heard = heardText,
                        summary = summary,
                        msg = feedback,
                        ok = ok,
                        canRetry = false
                    )
                }
            }
        }
    }

    private fun showResult(
        heard: String?,
        summary: String?,
        msg: String,
        ok: Boolean,
        canRetry: Boolean
    ) {
        if (overlay?.isOverlayPermittedAndEnabled() == true) {
            overlay?.renderResultWithAutoReset(
                CommandOverlayManager.OverlayState.ResultFeedback(
                    heardText = heard,
                    commandSummary = summary,
                    message = msg,
                    isSuccess = ok,
                    canRetry = canRetry
                ),
                resetAfterMs = 2600L
            ) {
                if (!running || paused || pending != null) null else idleOverlayState()
            }
        } else {
            toast(msg)
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
            .setContentText(
                if (isPttMode()) "উপরের পিল বা কীবোর্ড ট্যাপ করে কথা বলুন"
                else "\"Hey Bot\" বলে কমান্ড দিন"
            )
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
