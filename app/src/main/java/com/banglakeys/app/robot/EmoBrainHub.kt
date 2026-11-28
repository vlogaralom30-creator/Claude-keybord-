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

    @Volatile
    private var forcedMove: RobotBodyMove? = null

    @Volatile
    private var forcedMoveExpiresAtMs: Long = 0L

    @Volatile
    private var forcedHdEye: HdEyeStyle? = null

    @Volatile
    private var forcedHdEyeExpiresAtMs: Long = 0L

    @Volatile
    var lastKeyboardHopAtMs: Long = -10_000L
        private set

    @Volatile
    var lastKeyboardPetAtMs: Long = -10_000L
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
            forcedMove = null
            forcedMoveExpiresAtMs = 0L
            forcedHdEye = null
            forcedHdEyeExpiresAtMs = 0L
            lastKeyboardHopAtMs = -10_000L
            lastKeyboardPetAtMs = -10_000L
            brain.resetMemory()
            bodyState = EmoBodyState()
        }
        notifyListeners()
    }

    fun playBodyMove(move: RobotBodyMove, durationMs: Long = 5_000L) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            forcedMove = move
            forcedMoveExpiresAtMs = now + durationMs
            forcedHdEye = null
            forcedHdEyeExpiresAtMs = 0L
            lastKeyboardHopAtMs = now
            when (move) {
                RobotBodyMove.CRY_SIT -> brain.handle(RobotEvent.Dropped)
                RobotBodyMove.BELLY_FLOP -> brain.handle(RobotEvent.FaceDown)
                else -> brain.express(
                    face = move.previewFace,
                    text = "${move.badge} ${move.titleBn}",
                    bondDelta = 1,
                    funDelta = 2,
                    wakeUp = true,
                    forceSpeech = true,
                )
            }
            if (move == RobotBodyMove.DIZZY_WOBBLE) {
                bodyState = bodyState.copy(lastEffect = BodyEffect.STARS, lastEffectAtMs = now)
            } else if (move == RobotBodyMove.JUMP_WAVE) {
                bodyState = bodyState.copy(lastEffect = BodyEffect.HEARTS, lastEffectAtMs = now)
            }
        }
        notifyListeners()
    }

    fun playHdEye(style: HdEyeStyle, durationMs: Long = 5_500L) {
        val now = System.currentTimeMillis()
        val mappedFace = when (style) {
            HdEyeStyle.GLANCE -> FaceId.CURIOUS
            HdEyeStyle.CHILL -> FaceId.CALM
            HdEyeStyle.SPARKLE -> FaceId.JOY
            HdEyeStyle.GOOGLY -> FaceId.SWIRL
            HdEyeStyle.PINPOINT -> FaceId.SHOCKED
            HdEyeStyle.FOCUSED -> FaceId.HYPED
            HdEyeStyle.PEEK -> FaceId.SMILE
            HdEyeStyle.FIERCE -> FaceId.ANGRY
        }
        synchronized(lock) {
            forcedHdEye = style
            forcedHdEyeExpiresAtMs = now + durationMs
            forcedMove = null
            forcedMoveExpiresAtMs = 0L
            lastKeyboardHopAtMs = now
            brain.express(
                face = mappedFace,
                text = "${style.badge} ${style.titleBn}",
                funDelta = 1,
                wakeUp = true,
                forceSpeech = true,
            )
        }
        notifyListeners()
    }

    fun clearPlayOverrides() {
        val hadOverride = forcedMove != null || forcedHdEye != null
        forcedMove = null
        forcedMoveExpiresAtMs = 0L
        forcedHdEye = null
        forcedHdEyeExpiresAtMs = 0L
        if (hadOverride) notifyListeners()
    }

    fun triggerTapHop() {
        lastKeyboardHopAtMs = System.currentTimeMillis()
        notifyListeners()
    }

    fun triggerPetWiggle() {
        lastKeyboardPetAtMs = System.currentTimeMillis()
        notifyListeners()
    }

    fun activeForcedMove(nowMs: Long = System.currentTimeMillis()): RobotBodyMove? {
        val m = forcedMove ?: return null
        if (nowMs > forcedMoveExpiresAtMs) {
            forcedMove = null
            return null
        }
        return m
    }

    fun activeForcedHdEye(nowMs: Long = System.currentTimeMillis()): HdEyeStyle? {
        val s = forcedHdEye ?: return null
        if (nowMs > forcedHdEyeExpiresAtMs) {
            forcedHdEye = null
            return null
        }
        return s
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
