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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ২-মোড ভয়েস অ্যাসিস্ট্যান্ট Foreground Service:
 *  - CONTINUOUS: "Hey Bot ..." শুনলে জেগে ওঠে + awake window জুড়ে ধারাবাহিক কমান্ড নেয়।
 *  - PUSH_TO_TALK: মাইক চুপ থাকে; overlay side-tab বা notification বাটন চাপলে একবার শোনে।
 *
 * নোট: SmartCommandResolver দিয়ে N-best + Banglish fuzzy ম্যাচিং করে, প্রয়োজনে
 * GeminiCommandInterpreter fallback চালায়, এবং ঝুঁকিপূর্ণ কল/মেসেজে Confirm নেয়।
 */
class AssistantListenerService : Service() {

    companion object {
        const val ACTION_STOP = "com.banglakeys.app.ASSISTANT_STOP"
        const val ACTION_TRIGGER_PTT = "com.banglakeys.app.ASSISTANT_TRIGGER_PTT"
        const val ACTION_REFRESH_CONFIG = "com.banglakeys.app.ASSISTANT_REFRESH_CONFIG"
        private const val CHANNEL_ID = "assistant_listen"
        private const val NOTIF_ID = 4201

        @Volatile
        var running: Boolean = false
            private set

        private var instance: AssistantListenerService? = null

        fun start(ctx: Context) {
            // Mic permission ছাড়া microphone-type foreground service শুরু করলে Android 14+ এ crash করে
            if (!AssistantSetup.hasMic(ctx)) {
                Toast.makeText(ctx, "মাইক্রোফোন permission দিন", Toast.LENGTH_SHORT).show()
                return
            }
            val i = Intent(ctx, AssistantListenerService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AssistantListenerService::class.java))
        }

        fun triggerPtt(ctx: Context) {
            if (!AssistantSetup.hasMic(ctx)) {
                Toast.makeText(ctx, "মাইক্রোফোন permission দিন", Toast.LENGTH_SHORT).show()
                return
            }
            val i = Intent(ctx, AssistantListenerService::class.java).setAction(ACTION_TRIGGER_PTT)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun notifyConfigChanged(ctx: Context) {
            if (!running) return
            val i = Intent(ctx, AssistantListenerService::class.java).setAction(ACTION_REFRESH_CONFIG)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        /** Keyboard-এর নিজের voice typing চলার সময় true দিন, শেষ হলে false (mic conflict এড়াতে)। */
        fun setPaused(paused: Boolean) {
            instance?.pauseInternal(paused)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recognizer: SpeechRecognizer? = null
    private lateinit var executor: CommandExecutor
    private lateinit var overlay: CommandOverlayManager

    private var awakeUntil = 0L
    private var paused = false
    private var pttSessionActive = false
    private var geminiInFlight = false
    private var pendingCommand: Command? = null
    private var pendingSummary: String = ""
    private var activeFlowJob: Job? = null
    private var errorStreak = 0

    private val restart = Runnable { listenOnce() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH_CONFIG -> {
                if (running) {
                    startAsForeground()
                    applyModeChange()
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        if (!AssistantSetup.hasMic(this) || !startAsForeground()) {
            toast("মাইক্রোফোন permission দিন")
            stopSelf()
            return START_NOT_STICKY
        }

        val firstStart = !running
        if (firstStart) {
            executor = CommandExecutor(applicationContext)
            overlay = CommandOverlayManager(
                ctx = applicationContext,
                onConfirmClick = { confirmPendingCommand() },
                onCancelClick = { cancelPendingCommand() },
                onStopFlowClick = { panicStopAll() },
                onPttTriggerClick = { beginPttListening() },
                onDismissOrSleepClick = { dismissActiveListening() }
            )
            running = true
            instance = this
        }

        if (intent?.action == ACTION_TRIGGER_PTT) {
            beginPttListening()
        } else if (firstStart) {
            renderIdleOverlay()
            if (AssistantPrefs.listeningMode(this) == ListeningMode.CONTINUOUS) {
                listenOnce()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        instance = null
        handler.removeCallbacksAndMessages(null)
        activeFlowJob?.cancel()
        serviceScope.cancel()
        recognizer?.destroy()
        recognizer = null
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }

    // ------------------------------------------------------------ mode & overlay helpers

    private fun currentMode(): ListeningMode = AssistantPrefs.listeningMode(this)

    private fun idleOverlayState(): CommandOverlayManager.OverlayState {
        val now = SystemClock.elapsedRealtime()
        val bangla = AssistantSetup.isBangla(this)
        return when {
            pttSessionActive -> CommandOverlayManager.OverlayState.Listening("", null, isPtt = true)
            currentMode() == ListeningMode.PUSH_TO_TALK ->
                CommandOverlayManager.OverlayState.Dormant(isBangla = bangla)
            now < awakeUntil -> {
                val sec = ((awakeUntil - now) / 1000L).toInt().coerceAtLeast(1)
                CommandOverlayManager.OverlayState.Listening("", sec, isPtt = false)
            }
            else -> CommandOverlayManager.OverlayState.Listening("", null, isPtt = false)
        }
    }

    private fun renderIdleOverlay() {
        if (!::overlay.isInitialized) return
        overlay.render(idleOverlayState())
    }

    private fun applyModeChange() {
        overlay.refreshPosition()
        if (paused) return
        if (currentMode() == ListeningMode.PUSH_TO_TALK) {
            if (!pttSessionActive) {
                handler.removeCallbacks(restart)
                recognizer?.cancel()
                recognizer?.destroy()
                recognizer = null
            }
            renderIdleOverlay()
        } else {
            renderIdleOverlay()
            if (recognizer == null) scheduleNext(200)
        }
    }

    private fun beginPttListening() {
        if (!running) return
        paused = false
        pendingCommand = null
        pttSessionActive = true
        awakeUntil = SystemClock.elapsedRealtime() + AssistantConfig.AWAKE_WINDOW_MS
        overlay.render(CommandOverlayManager.OverlayState.Listening("", null, isPtt = true))
        handler.removeCallbacks(restart)
        listenOnce()
    }

    private fun dismissActiveListening() {
        awakeUntil = 0L
        pttSessionActive = false
        pendingCommand = null
        if (currentMode() == ListeningMode.PUSH_TO_TALK) {
            handler.removeCallbacks(restart)
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
        renderIdleOverlay()
    }

    // ------------------------------------------------------------ listening

    private fun scheduleNext(delayMs: Long) {
        handler.removeCallbacks(restart)
        if (!running || paused) return
        val shouldContinue =
            currentMode() == ListeningMode.CONTINUOUS || pttSessionActive || pendingCommand != null
        if (!shouldContinue) {
            renderIdleOverlay()
            return
        }
        handler.postDelayed(restart, delayMs)
    }

    private fun pauseInternal(p: Boolean) {
        paused = p
        if (p) {
            handler.removeCallbacks(restart)
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
            if (::overlay.isInitialized) overlay.hide()
        } else if (running) {
            renderIdleOverlay()
            if (currentMode() == ListeningMode.CONTINUOUS || pttSessionActive) {
                scheduleNext(400)
            }
        }
    }

    private fun listenOnce() {
        if (!running || paused || geminiInFlight) return
        if (currentMode() == ListeningMode.PUSH_TO_TALK && !pttSessionActive && pendingCommand == null) {
            renderIdleOverlay()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("এই ফোনে speech recognizer নেই")
            stopSelf()
            return
        }
        recognizer?.let {
            try { it.cancel() } catch (_: Exception) {}
            try { it.destroy() } catch (_: Exception) {}
        }
        recognizer = null
        val r = try {
            SpeechRecognizer.createSpeechRecognizer(this)
        } catch (e: Exception) {
            AssistantLog.w("createSpeechRecognizer failed", e)
            null
        }
        if (r == null) {
            errorStreak = (errorStreak + 1).coerceAtMost(6)
            scheduleNext(1000L * errorStreak)
            return
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (pendingCommand == null && activeFlowJob?.isActive != true) {
                    renderIdleOverlay()
                }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (pendingCommand != null || activeFlowJob?.isActive == true) return
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (text.isEmpty()) return

                val now = SystemClock.elapsedRealtime()
                val alreadyAwake = pttSessionActive || now < awakeUntil
                val (woke, rest) = SmartCommandResolver.splitWakeSmart(text)
                if (alreadyAwake || woke) {
                    // কথা বলতে থাকলে awake window বাড়াই, যাতে মাঝে থামলে "ঘুমিয়ে" না যায়
                    awakeUntil = maxOf(awakeUntil, now + AssistantConfig.AWAKE_WINDOW_MS)
                    val sec = if (now < awakeUntil) ((awakeUntil - now) / 1000L).toInt().coerceAtLeast(1) else null
                    val display = if (woke) rest.ifEmpty { "শুনছি…" } else text
                    overlay.render(
                        CommandOverlayManager.OverlayState.Listening(
                            partialText = display,
                            awakeSec = sec,
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
                if (pttSessionActive) {
                    pttSessionActive = false
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        showFeedback(null, null, "কথা শোনা যায়নি", isSuccess = false, canRetry = true)
                    } else {
                        renderIdleOverlay()
                    }
                    return
                }
                // একটানা error হলে ধীরে ধীরে বিরতি বাড়াই — মাইক বারবার on/off হয়ে beep/ব্যাটারি নষ্ট না করতে
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    errorStreak = 0 // চুপচাপ থাকা স্বাভাবিক, error নয়
                } else {
                    errorStreak = (errorStreak + 1).coerceAtMost(6)
                }
                val delay = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
                    SpeechRecognizer.ERROR_CLIENT -> 700L * (errorStreak.coerceAtLeast(1))
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                    SpeechRecognizer.ERROR_SERVER -> 3000L
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 250L
                    else -> 500L * errorStreak.coerceAtLeast(1)
                }
                if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    recognizer?.destroy()
                    recognizer = null
                }
                scheduleNext(delay)
            }

            override fun onResults(results: Bundle?) {
                errorStreak = 0
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val wasPtt = pttSessionActive
                pttSessionActive = false
                if (list.isEmpty() && wasPtt) {
                    showFeedback(null, null, "কথা শোনা যায়নি", isSuccess = false, canRetry = true)
                }
                handleResults(list, wasPtt)
                if (currentMode() == ListeningMode.CONTINUOUS || pendingCommand != null) {
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
            // ছোট বিরতিতে ("Hey Bot ... open TikTok") কেটে না দিতে — আগের FIXES.md-এ থাকলেও কোডে হারিয়ে গিয়েছিল
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
        }
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            AssistantLog.w("startListening failed", e)
            errorStreak = (errorStreak + 1).coerceAtMost(6)
            scheduleNext(1000L * errorStreak)
        }
    }

    // ------------------------------------------------------------- commands & confirmation

    private fun handleResults(candidates: List<String>, wasPtt: Boolean) {
        if (candidates.isEmpty()) return
        val now = SystemClock.elapsedRealtime()

        // ১) Pending Confirmation থাকলে আগে হ্যাঁ/না শুনি
        if (pendingCommand != null) {
            if (handleConfirmationVoice(candidates)) return
            // নতুন সরাসরি কমান্ড হলে আগের confirm বাতিল করে নতুনটি প্রসেস করি
            pendingCommand = null
        }

        val alreadyAwake = wasPtt || now < awakeUntil
        val fgPkg = AssistantAccessibilityService.instance?.foregroundPackage().orEmpty()
        AssistantMemory.shared.updateScreenContext(fgPkg)
        val res = SmartCommandResolver.resolveWithMemory(
            candidates = candidates,
            alreadyAwake = alreadyAwake,
            memory = AssistantMemory.shared,
            foregroundPackage = fgPkg
        )

        if (!res.woke && !alreadyAwake) return

        // শুধু "Hey Bot" বললে
        if (res.woke && res.command == null && res.normalizedText.isBlank()) {
            awakeUntil = now + AssistantConfig.AWAKE_WINDOW_MS
            overlay.render(
                CommandOverlayManager.OverlayState.Listening(
                    partialText = "বলুন…",
                    awakeSec = (AssistantConfig.AWAKE_WINDOW_MS / 1000L).toInt(),
                    isPtt = wasPtt
                )
            )
            if (!::overlay.isInitialized || !overlay.isOverlayPermittedAndEnabled()) {
                toast("🎤 বলুন…")
            }
            return
        }

        val cmd = res.command
        if (cmd != null) {
            awakeUntil = now + AssistantConfig.AWAKE_WINDOW_MS
            dispatchCommand(cmd, res.heardText, res.summary)
            return
        }

        // ২) স্থানীয় resolver না পারলে Gemini Fallback (যদি key থাকে)
        val fallbackQuery = res.heardText.ifBlank { candidates.first() }
        if (GeminiConfig.hasKey(applicationContext) && fallbackQuery.isNotBlank()) {
            geminiInFlight = true
            overlay.render(
                CommandOverlayManager.OverlayState.Listening(
                    partialText = "Gemini দিয়ে বোঝা হচ্ছে…",
                    awakeSec = null,
                    isPtt = false
                )
            )
            GeminiCommandInterpreter.interpret(applicationContext, fallbackQuery) { gCmd ->
                geminiInFlight = false
                if (!running) return@interpret
                if (gCmd != null) {
                    awakeUntil = SystemClock.elapsedRealtime() + AssistantConfig.AWAKE_WINDOW_MS
                    dispatchCommand(gCmd, fallbackQuery, SmartCommandResolver.describe(gCmd))
                } else {
                    showFeedback(fallbackQuery, null, "বুঝিনি: $fallbackQuery", isSuccess = false, canRetry = true)
                }
                if (currentMode() == ListeningMode.CONTINUOUS) scheduleNext(250)
            }
            return
        }

        if (fallbackQuery.isNotBlank()) {
            showFeedback(fallbackQuery, null, "বুঝিনি: $fallbackQuery", isSuccess = false, canRetry = true)
        }
    }

    private fun handleConfirmationVoice(candidates: List<String>): Boolean {
        for (raw in candidates) {
            val low = " " + raw.lowercase().trim() + " "
            if (CommandParser.CONFIRM_YES_WORDS.any { low.contains(" $it ") }) {
                confirmPendingCommand()
                return true
            }
            if (CommandParser.CONFIRM_NO_WORDS.any { low.contains(" $it ") }) {
                cancelPendingCommand()
                return true
            }
        }
        return false
    }

    private fun needsConfirmation(cmd: Command): String? = when (cmd) {
        is Command.Call ->
            if (AssistantPrefs.confirmBeforeAppCall(this)) "${cmd.target}-কে ফোন কল করবেন?" else null
        is Command.AppCall ->
            if (AssistantPrefs.confirmBeforeAppCall(this)) {
                "${cmd.appName}-তে ${cmd.contact}-কে ${if (cmd.video) "ভিডিও" else "অডিও"} কল করবেন?"
            } else null
        is Command.AppMessage ->
            if (cmd.send && AssistantPrefs.confirmBeforeHumanSend(this)) {
                "${cmd.appName}-তে ${cmd.contact}-কে মেসেজ পাঠাবেন?"
            } else null
        is Command.AppChat ->
            if (cmd.send && AssistantPrefs.confirmBeforeAiChatSend(this)) {
                "${cmd.appName ?: "অ্যাপ"}-এ লেখা সেন্ড করবেন?"
            } else null
        else -> null
    }

    private fun dispatchCommand(cmd: Command, heardText: String?, summary: String?) {
        when (cmd) {
            Command.Sleep -> {
                awakeUntil = 0L
                pendingCommand = null
                showFeedback(heardText, summary, "ঘুমালাম — \"Hey Bot\" বললে জাগবো", isSuccess = true, canRetry = false)
                return
            }
            Command.StopAssistant -> {
                pendingCommand = null
                toast("🤖 Assistant বন্ধ")
                stopSelf()
                return
            }
            Command.PanicStop -> {
                panicStopAll()
                return
            }
            else -> {}
        }

        val question = needsConfirmation(cmd)
        if (question != null) {
            pendingCommand = cmd
            pendingSummary = summary ?: SmartCommandResolver.describe(cmd)
            overlay.render(
                CommandOverlayManager.OverlayState.AwaitingConfirm(
                    heardText = heardText.orEmpty(),
                    actionSummary = pendingSummary,
                    question = question
                )
            )
            return
        }

        executeNow(cmd, heardText, summary ?: SmartCommandResolver.describe(cmd))
    }

    private fun confirmPendingCommand() {
        val cmd = pendingCommand ?: return
        val sum = pendingSummary
        pendingCommand = null
        pendingSummary = ""
        executeNow(cmd, null, sum)
    }

    private fun cancelPendingCommand() {
        pendingCommand = null
        pendingSummary = ""
        showFeedback(null, null, "বাতিল করা হয়েছে", isSuccess = false, canRetry = false)
    }

    private fun panicStopAll() {
        pendingCommand = null
        pendingSummary = ""
        activeFlowJob?.cancel()
        activeFlowJob = null
        awakeUntil = 0L
        pttSessionActive = false
        showFeedback(null, "সব কাজ বন্ধ", "থামানো হয়েছে", isSuccess = true, canRetry = false)
    }

    private fun executeNow(cmd: Command, heardText: String?, summary: String) {
        if (executor.isAsync(cmd)) {
            activeFlowJob?.cancel()
            val label = when (cmd) {
                is Command.AppChat -> cmd.appName ?: "Chat"
                is Command.AppCall -> cmd.appName
                is Command.AppMessage -> cmd.appName
                is Command.ChromeNavigate -> "Chrome"
                is Command.InAppSearch -> cmd.appKey ?: "Search"
                is Command.AppAction -> cmd.appKey ?: "Action"
                else -> "Assistant"
            }
            overlay.render(CommandOverlayManager.OverlayState.RunningFlow(label, "শুরু হচ্ছে…"))
            activeFlowJob = serviceScope.launch {
                try {
                    val result = executor.executeAsync(cmd) { progress ->
                        overlay.render(CommandOverlayManager.OverlayState.RunningFlow(label, progress))
                    }
                    val ok = !result.startsWith("❌") && !result.startsWith("⚠️") && !result.startsWith("⏱")
                    showFeedback(heardText, summary, result, isSuccess = ok, canRetry = !ok)
                } catch (_: CancellationException) {
                    showFeedback(heardText, summary, "থামানো হয়েছে", isSuccess = false, canRetry = false)
                } catch (e: Exception) {
                    AssistantLog.w("Async command failed", e)
                    showFeedback(heardText, summary, "কাজটি সম্পন্ন হয়নি", isSuccess = false, canRetry = true)
                }
            }
        } else {
            val result = executor.execute(cmd)
            if (result.isNotBlank()) {
                val ok = !result.startsWith("⚠️") && !result.startsWith("❌") && !result.contains("পাইনি") && !result.contains("পারলাম না")
                showFeedback(heardText, summary, result, isSuccess = ok, canRetry = !ok)
            } else {
                renderIdleOverlay()
            }
        }
    }

    private fun showFeedback(
        heardText: String?,
        summary: String?,
        message: String,
        isSuccess: Boolean,
        canRetry: Boolean
    ) {
        if (::overlay.isInitialized && overlay.isOverlayPermittedAndEnabled()) {
            overlay.renderResultWithAutoReset(
                CommandOverlayManager.OverlayState.ResultFeedback(
                    heardText = heardText,
                    commandSummary = summary,
                    message = message,
                    isSuccess = isSuccess,
                    canRetry = canRetry
                ),
                resetAfterMs = 2600L,
                nextStateSupplier = {
                    if (running && pendingCommand == null && activeFlowJob?.isActive != true) {
                        idleOverlayState()
                    } else null
                }
            )
        } else {
            toast(message)
        }
    }

    private fun toast(msg: String) {
        if (msg.isBlank()) return
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    // --------------------------------------------------------- notification

    private fun startAsForeground(): Boolean {
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
        val pttPi = PendingIntent.getService(
            this, 2,
            Intent(this, AssistantListenerService::class.java).setAction(ACTION_TRIGGER_PTT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openPi = PendingIntent.getActivity(
            this, 1, Intent(this, AssistantActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPtt = currentMode() == ListeningMode.PUSH_TO_TALK
        val contentText = if (isPtt) {
            "Quiet Push-to-Talk চালু — পাশে ট্যাপ বা 'বলুন' চাপুন"
        } else {
            "\"Hey Bot\" বলে কমান্ড দিন"
        }

        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        @Suppress("DEPRECATION")
        val n = b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BanglaKeys Assistant চালু")
            .setContentText(contentText)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_btn_speak_now, "বলুন", pttPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "বন্ধ করুন", stopPi)
            .setOngoing(true)
            .build()

        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, n)
            }
            true
        } catch (e: Exception) {
            AssistantLog.w("startForeground failed", e)
            false
        }
    }
}
