package com.banglakeys.app.robot

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * পুরো অ্যাপ জুড়ে EMO রোবটের একক ব্রেন, ভয়েস ও বডি সেন্সর স্টেট হাব।
 */
object EmoBrainHub {

    val brain: RobotBrain = RobotBrain()

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val eventSinks = CopyOnWriteArraySet<(RobotEvent) -> Unit>()

    @Volatile
    var isVoiceListening: Boolean = false
        private set

    @Volatile
    var bodyState: EmoBodyState = EmoBodyState()
        private set

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun addEventSink(sink: (RobotEvent) -> Unit) {
        eventSinks.add(sink)
    }

    fun removeEventSink(sink: (RobotEvent) -> Unit) {
        eventSinks.remove(sink)
    }

    fun updateBody(transform: (EmoBodyState) -> EmoBodyState) {
        synchronized(lock) {
            bodyState = transform(bodyState)
        }
        notifyListeners()
    }

    fun triggerEffect(effect: BodyEffect) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            bodyState = bodyState.copy(lastEffect = effect, lastEffectAtMs = now)
        }
        notifyListeners()
    }

    fun tick() {
        val changed: Boolean
        synchronized(lock) {
            val beforeMood = brain.mood
            val beforeSleep = brain.sleeping
            val beforeCry = brain.isCrying
            brain.tick()
            changed = (beforeMood != brain.mood || beforeSleep != brain.sleeping || beforeCry != brain.isCrying)
        }
        if (changed) notifyListeners()
    }

    fun handleEvent(event: RobotEvent) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            brain.handle(event)
            val effect = when (event) {
                RobotEvent.Dropped -> BodyEffect.TEARS
                RobotEvent.Shake, RobotEvent.CompassSpin -> BodyEffect.STARS
                RobotEvent.FingerprintMatched, RobotEvent.LongPress, RobotEvent.Comforted -> BodyEffect.HEARTS
                RobotEvent.EarNear -> BodyEffect.WHISPER
                else -> null
            }
            if (effect != null) {
                bodyState = bodyState.copy(lastEffect = effect, lastEffectAtMs = now)
            }
        }
        notifyListeners()
        notifySinks(event)
    }

    fun express(
        face: FaceId,
        speech: String? = null,
        bondDelta: Int = 0,
        funDelta: Int = 0,
        wakeUp: Boolean = true,
        forceSpeech: Boolean = false,
    ) {
        synchronized(lock) {
            brain.express(
                face = face,
                text = speech,
                bondDelta = bondDelta,
                funDelta = funDelta,
                wakeUp = wakeUp,
                forceSpeech = forceSpeech,
            )
        }
        notifyListeners()
    }

    fun resetMemory() {
        synchronized(lock) {
            isVoiceListening = false
            brain.resetMemory()
            bodyState = EmoBodyState()
        }
        notifyListeners()
    }

    // ---- ভয়েস ও কমান্ড লাইফসাইকেল ইভেন্ট ----

    fun onListeningStarted(isPtt: Boolean) {
        isVoiceListening = true
        express(
            face = FaceId.CURIOUS,
            speech = if (isPtt) "শুনছি — বলুন…" else "হেই! শুনছি…",
            bondDelta = 1,
            forceSpeech = true,
        )
    }

    fun onWakeDetected() {
        isVoiceListening = true
        express(
            face = FaceId.CURIOUS,
            speech = "হ্যাঁ বলুন?",
            bondDelta = 1,
            forceSpeech = true,
        )
    }

    fun onPartialHeard(caption: String) {
        isVoiceListening = true
        val trimmed = caption.trim().take(28)
        express(
            face = FaceId.CURIOUS,
            speech = if (trimmed.isNotEmpty()) trimmed else "শুনছি…",
            forceSpeech = false,
        )
    }

    fun onAwaitingConfirm() {
        isVoiceListening = false
        express(
            face = FaceId.STARE,
            speech = "নিশ্চিত করবেন?",
            forceSpeech = true,
        )
    }

    fun onFlowRunning(appLabel: String) {
        isVoiceListening = false
        express(
            face = FaceId.SQUEEZE,
            speech = "$appLabel চালু হচ্ছে…",
            forceSpeech = true,
        )
    }

    fun onCommandSuccess(summary: String? = null) {
        isVoiceListening = false
        val clean = summary?.trim()?.take(24)
        express(
            face = FaceId.JOY,
            speech = if (!clean.isNullOrBlank()) clean else "হয়ে গেছে! ✨",
            bondDelta = 2,
            funDelta = 3,
            forceSpeech = true,
        )
    }

    fun onCommandFailure(message: String? = null) {
        isVoiceListening = false
        val clean = message?.trim()?.take(24)
        express(
            face = FaceId.MEH,
            speech = if (!clean.isNullOrBlank()) clean else "আবার বলুন?",
            forceSpeech = true,
        )
    }

    fun onSleep() {
        isVoiceListening = false
        synchronized(lock) {
            brain.handle(RobotEvent.FaceDown)
        }
        notifyListeners()
    }

    fun onIdleStandby() {
        isVoiceListening = false
        synchronized(lock) {
            if (!brain.sleeping && !brain.isCrying && brain.mood == FaceId.CURIOUS) {
                brain.express(FaceId.NORMAL, text = null, wakeUp = false)
            }
        }
        notifyListeners()
    }

    private fun notifyListeners() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.forEach { it() }
        } else {
            mainHandler.post { listeners.forEach { it() } }
        }
    }

    private fun notifySinks(event: RobotEvent) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            eventSinks.forEach { it(event) }
        } else {
            mainHandler.post { eventSinks.forEach { it(event) } }
        }
    }
}
