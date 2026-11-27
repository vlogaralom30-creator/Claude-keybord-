package com.banglakeys.app.robot

/**
 * Plan-এর Section 7 (Brain design) অনুযায়ী state machine।
 * - needs (energy/bond/fun) সময়ের সাথে ধীরে ধীরে কমে (constants নিচে, Phase 2-এ
 *   আসল ফোনে পরীক্ষা করে tune করতে হবে — plan-এর jhakuni-threshold-এর মতোই)
 * - event এলে তাৎক্ষণিক mood+needs বদলায়, আর কিছুক্ষণ idle picker থামিয়ে রাখে
 *   ("Interrupt rule")
 * - idle picker নিজে থেকে কিছুক্ষণ পরপর একটা নতুন mood বাছে, tag-ভিত্তিক weight দিয়ে,
 *   আগেরটা সাথে সাথে repeat করে না
 * - energy < 18 হলে ঘুমিয়ে পড়ে (REST face), energy 75+ না হওয়া পর্যন্ত জাগে না;
 *   face-down (forced sleep) হলে energy বাড়লেও face-up না হওয়া পর্যন্ত জাগে না
 * - কথা বলা (speechText) সর্বোচ্চ প্রতি ৫ সেকেন্ডে একবার (rate limit)
 *
 * এখনো যা নেই (পরের ধাপ): আসল sensor/battery/typing থেকে event পাঠানো (Phase 2/3),
 * TTS/voice (Phase 4)। এখন শুধু fake event দিয়ে brain-টা ঠিকঠাক কাজ করছে কিনা
 * টেস্ট করা যাবে — `RobotFaceDemoActivity`-এর বাটনগুলো দেখুন।
 */
class RobotBrain(private val nowMs: () -> Long = { System.currentTimeMillis() }) {

    val needs = RobotNeeds()
    var mood: FaceId = FaceId.NORMAL
        private set
    var speechText: String? = null
        private set
    var sleeping: Boolean = false
        private set
    var isCrying: Boolean = false
        private set
    var isFreeFalling: Boolean = false
        private set
    private var cryingSinceMs = 0L
    private var forcedSleep = false

    private var lastEventAt = 0L
    private var lastSpeechAt = -SPEECH_RATE_LIMIT_MS
    private var nextIdlePickAt = 0L
    private var lastEnergyDecayAt = 0L
    private var lastFunDecayAt = 0L
    private var lastBondDecayAt = 0L

    init {
        val now = nowMs()
        lastEventAt = now - INTERRUPT_MS
        lastEnergyDecayAt = now
        lastFunDecayAt = now
        lastBondDecayAt = now
        nextIdlePickAt = now + IDLE_GAP_MS.random()
    }

    // ----------------------------------------------------------- tick

    /** নিয়মিত call করুন (demo-তে View-এর draw loop থেকে, আসল keyboard-এও একই রকম)। */
    fun tick() {
        val now = nowMs()
        decayNeeds(now)

        if (needs.energy < SLEEP_BELOW && !sleeping) {
            sleeping = true
            setMood(FaceId.REST, "ঘুম পাচ্ছে… 😴", now)
        }
        if (sleeping && !forcedSleep && needs.energy >= WAKE_ABOVE) {
            sleeping = false
            setMood(FaceId.NORMAL, "ভালো ঘুম হলো!", now)
        }
        if (sleeping) return // ঘুমিয়ে থাকলে idle picker চলবে না

        if (isCrying && now - cryingSinceMs >= CRY_AUTO_SOOTHE_MS) {
            isCrying = false
            setMood(FaceId.CALM, "ধীরে ধীরে শান্ত হচ্ছি…", now)
            return
        }
        if (isCrying || isFreeFalling) return

        val interrupted = now - lastEventAt < INTERRUPT_MS
        if (!interrupted && now >= nextIdlePickAt) {
            idlePick(now)
            nextIdlePickAt = now + IDLE_GAP_MS.random()
        }
    }

    private fun decayNeeds(now: Long) {
        // ঘুমের মধ্যে energy কমে না, বরং আসল বিশ্রামের মতো আস্তে আস্তে বাড়ে —
        // নাহলে Charging event না এলে কখনো জাগবে না।
        if (sleeping) {
            if (now - lastEnergyDecayAt >= SLEEP_REGEN_MS) {
                needs.energy += 1; lastEnergyDecayAt = now
            }
        } else {
            if (now - lastEnergyDecayAt >= ENERGY_DECAY_MS) {
                needs.energy -= 1; lastEnergyDecayAt = now
            }
            if (now - lastFunDecayAt >= FUN_DECAY_MS) {
                needs.funLevel -= 1; lastFunDecayAt = now
            }
            if (now - lastBondDecayAt >= BOND_DECAY_MS) {
                needs.bond -= 1; lastBondDecayAt = now
            }
        }
        needs.clamp()
    }

    // ----------------------------------------------------------- idle picker

    private fun idlePick(now: Long) {
        val candidates = FaceId.values().filter { it != mood && it.tag != 's' }
        val weighted = candidates.flatMap { f -> List(tagWeight(f.tag)) { f } }
        val pick = weighted.randomOrNull() ?: return
        setMood(pick, null, now)
    }

    private fun tagWeight(tag: Char): Int = when (tag) {
        'p' -> if (needs.bond > 60) 4 else 2
        'n' -> if (needs.bond < 30 || needs.funLevel < 30) 4 else 1
        'f' -> if (needs.funLevel > 60) 3 else 1
        'u' -> 3
        'r' -> 1
        else -> 1
    }

    // ----------------------------------------------------------- events

    fun handle(event: RobotEvent) {
        val now = nowMs()
        lastEventAt = now
        // ঘুমের মধ্যে বেশিরভাগ event ignore হবে, শুধু জোরে শব্দ/ঝাঁকি/FaceUp জাগাবে, আর Charging এনার্জি বাড়াবে
        val canHandleWhileSleeping = event is RobotEvent.Clap ||
            event is RobotEvent.LoudSound ||
            event is RobotEvent.Shake ||
            event is RobotEvent.FreeFall ||
            event is RobotEvent.Dropped ||
            event is RobotEvent.FingerprintMatched ||
            event is RobotEvent.FaceUp ||
            event is RobotEvent.Charging
        if (sleeping && !canHandleWhileSleeping) {
            return
        }
        when (event) {
            RobotEvent.Tap -> {
                if (isCrying) {
                    soothe(now, "একটু ভালো লাগছে… আরেকটু আদর করো")
                } else {
                    needs.bond += 2; setMood(FaceId.SMILE, "হাই! 👋", now)
                }
            }
            RobotEvent.LongPress -> {
                if (isCrying) {
                    soothe(now, "এখন ব্যথা কমেছে 🥰")
                } else {
                    needs.bond += 4; setMood(FaceId.JOY, "আদর লাগে ভালো 🥰", now)
                }
            }
            RobotEvent.Shake -> {
                sleeping = false; forcedSleep = false; isFreeFalling = false
                needs.funLevel -= 5
                setMood(FaceId.ANGRY, "আরে আস্তে! 😠", now)
            }
            RobotEvent.Lift -> {
                if (isCrying) {
                    soothe(now, "তুলে নিলে? বাঁচালে! 🥺")
                } else {
                    setMood(FaceId.CURIOUS, "উপরে কই যাচ্ছি? 🤔", now)
                }
            }
            RobotEvent.FreeFall -> {
                sleeping = false; forcedSleep = false; isFreeFalling = true
                setMoodForceSpeech(FaceId.SHOCKED, "আরে! পড়ে যাচ্ছি! 😱", now)
            }
            RobotEvent.Dropped -> {
                sleeping = false; forcedSleep = false; isFreeFalling = false
                isCrying = true; cryingSinceMs = now
                needs.funLevel -= 10; needs.energy -= 6; needs.bond -= 2
                setMoodForceSpeech(FaceId.CRY_RIVER, "উফ! ব্যথা পেলাম তো, আদর করো… 😭", now)
            }
            RobotEvent.Comforted -> soothe(now, "এখন শান্ত লাগছে ❤️")
            RobotEvent.FaceDown -> {
                forcedSleep = true; sleeping = true; isFreeFalling = false; isCrying = false
                setMood(FaceId.REST, "জোর করে ঘুম... 😴", now)
            }
            RobotEvent.FaceUp -> {
                forcedSleep = false; sleeping = false; isFreeFalling = false
                if (!isCrying) setMood(FaceId.NORMAL, "উঠে গেলাম", now)
            }
            RobotEvent.Clap -> {
                sleeping = false; forcedSleep = false; needs.funLevel += 5
                setMood(FaceId.SHOCKED, "হুঁশ! কী হলো? 😳", now)
            }
            RobotEvent.LoudSound -> {
                sleeping = false; forcedSleep = false
                setMood(FaceId.STARE, "কীসের আওয়াজ? 👀", now)
            }
            RobotEvent.VoiceHeard -> setMood(FaceId.CURIOUS, "শুনছি...", now)
            RobotEvent.BatteryLow -> setMood(FaceId.SWEAT, "চার্জ লাগবে 🔋", now)
            RobotEvent.Charging -> {
                needs.energy += 10
                if (!sleeping) {
                    setMood(FaceId.HYPED, "চার্জ হচ্ছে, দারুণ লাগছে ⚡", now)
                }
            }
            RobotEvent.Dark -> if (!isCrying) setMood(FaceId.PLEAD, "অন্ধকার লাগছে 🥺", now)
            RobotEvent.Bright -> if (!isCrying) setMood(FaceId.WINK, "আলো! 😉", now)
            RobotEvent.FingerprintMatched -> {
                sleeping = false; forcedSleep = false; isCrying = false; isFreeFalling = false
                needs.bond += 10; needs.funLevel += 6
                setMoodForceSpeech(FaceId.BEAMING, "চিনতে পেরেছি! তুমি আমার প্রিয় মানুষ ❤️", now)
            }
            RobotEvent.FingerprintRejected -> {
                setMoodForceSpeech(FaceId.GRUMPY, "হুম… ফিঙ্গারপ্রিন্ট মিলল না তো! 😒", now)
            }
            RobotEvent.CompassSpin -> {
                sleeping = false; forcedSleep = false
                needs.funLevel += 3
                setMoodForceSpeech(FaceId.SWIRL, "চারদিকে ঘুরছি! মাথা ঘুরছে 😵‍💫", now)
            }
            RobotEvent.EarNear -> {
                if (!isCrying) setMoodForceSpeech(FaceId.SMUG, "কানের কাছে কী বলবে? 🤫", now)
            }
            RobotEvent.EarAway -> {
                if (!isCrying && !sleeping) setMood(FaceId.WINK, "পিক-আ-বু! 😉", now)
            }
            RobotEvent.TypingFast -> setMood(FaceId.HYPED, "তাড়াতাড়ি লিখছো!", now)
            RobotEvent.TypingSlow -> setMood(FaceId.BORED, "কিছু ভাবছো?", now)
            RobotEvent.BackspaceBurst -> {
                val face = if (mood == FaceId.NERVOUS) FaceId.SWEAT else FaceId.NERVOUS
                setMood(face, "ঠিক আছে, নার্ভাস হয়ো না 😅", now)
            }
            RobotEvent.KeywordLove -> { needs.bond += 6; setMood(FaceId.JOY, "❤️", now) }
            RobotEvent.KeywordAnger -> { needs.bond -= 3; setMood(FaceId.FURIOUS, "রাগ কোরো না, শান্ত হও 💢", now) }
            RobotEvent.KeywordLaugh -> {
                needs.funLevel += 5
                val face = if (mood == FaceId.GIGGLE) FaceId.BEAMING else FaceId.GIGGLE
                setMood(face, "হাহাহা! 😂", now)
            }
            RobotEvent.KeywordKiss -> {
                needs.bond += 5
                val face = if (mood == FaceId.WINK_KISS) FaceId.KISS_SHY else FaceId.WINK_KISS
                setMood(face, "উম্মাহ! 😘", now)
            }
            RobotEvent.KeywordShock -> {
                needs.funLevel += 2
                setMood(FaceId.SHOCKED, "অ্যাঁ?! সত্যি?! 😳", now)
            }
            RobotEvent.KeywordCry -> {
                needs.bond += 1
                setMood(FaceId.PLEAD, "মন খারাপ করো না 🥺", now)
            }
            RobotEvent.KeywordCute -> {
                needs.bond += 4
                val face = if (mood == FaceId.UWU) FaceId.MISCHIEF else FaceId.UWU
                setMood(face, "কিউট! >ω<", now)
            }
            RobotEvent.MessageSent -> setMood(FaceId.WINK, null, now)
        }
        needs.clamp()
    }

    private fun soothe(now: Long, msg: String) {
        isCrying = false
        isFreeFalling = false
        needs.bond += 5
        needs.funLevel += 6
        setMoodForceSpeech(FaceId.CALM, msg, now)
    }

    /** সরাসরি যেকোনো ১২টি এক্সপ্রেশন ও স্পিচ সেট করে (ভয়েস/কন্ট্রোল সেন্টার ইভেন্টের জন্য)। */
    fun express(
        face: FaceId,
        text: String? = null,
        bondDelta: Int = 0,
        funDelta: Int = 0,
        wakeUp: Boolean = true,
        forceSpeech: Boolean = false,
    ) {
        val now = nowMs()
        lastEventAt = now
        if (wakeUp) {
            sleeping = false
            forcedSleep = false
        }
        needs.bond += bondDelta
        needs.funLevel += funDelta
        needs.clamp()
        mood = face
        if (text == null) {
            speechText = null
        } else if (forceSpeech || now - lastSpeechAt >= SPEECH_RATE_LIMIT_MS) {
            speechText = text
            lastSpeechAt = now
        }
    }

    // ----------------------------------------------------------- helpers

    private fun setMood(face: FaceId, text: String?, now: Long) {
        mood = face
        if (text != null && now - lastSpeechAt >= SPEECH_RATE_LIMIT_MS) {
            speechText = text
            lastSpeechAt = now
        } else if (text == null) {
            speechText = null
        }
    }

    private fun setMoodForceSpeech(face: FaceId, text: String, now: Long) {
        mood = face
        speechText = text
        lastSpeechAt = now
    }

    /** Settings-এর "সব memory মুছে ফেলো" বাটনের জন্য (plan Section 9)। */
    fun resetMemory() {
        val now = nowMs()
        needs.energy = 80; needs.bond = 50; needs.funLevel = 60
        sleeping = false; forcedSleep = false
        isCrying = false; isFreeFalling = false; cryingSinceMs = 0L
        mood = FaceId.NORMAL
        speechText = null
        lastSpeechAt = now - SPEECH_RATE_LIMIT_MS
        lastEventAt = now - INTERRUPT_MS
        lastEnergyDecayAt = now
        lastFunDecayAt = now
        lastBondDecayAt = now
        nextIdlePickAt = now + IDLE_GAP_MS.random()
    }

    companion object {
        private const val SLEEP_BELOW = 18
        private const val WAKE_ABOVE = 75
        private const val INTERRUPT_MS = 4000L
        private const val SPEECH_RATE_LIMIT_MS = 5000L
        private const val CRY_AUTO_SOOTHE_MS = 25_000L
        private val IDLE_GAP_MS = 4000..8000

        // ডেমো/টেস্টে কয়েক মিনিটেই sleep/wake cycle চোখে দেখা যাওয়ার জন্য এই মান —
        // আসল ফোনে ধীরে (ঘণ্টার হিসেবে) করতে এখানে বাড়িয়ে দিন (Phase 2-এ সত্যিকারের
        // ব্যবহার দেখে tune করা হবে, plan-এর jhakuni-threshold-এর মতোই)।
        private const val ENERGY_DECAY_MS = 3_000L
        private const val SLEEP_REGEN_MS = 2_000L
        private const val FUN_DECAY_MS = 5_000L
        private const val BOND_DECAY_MS = 60_000L
    }
}
