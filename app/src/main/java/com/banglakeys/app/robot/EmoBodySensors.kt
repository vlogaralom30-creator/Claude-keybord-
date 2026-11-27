package com.banglakeys.app.robot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * অ্যাক্সেলেরোমিটার থেকে ফোনের ফিজিক্যাল টিল্ট (pitch/roll) ও চোখের দৃষ্টির দিক মসৃণভাবে বের করে।
 */
class BodyTiltFilter {
    private var smoothX = 0f
    private var smoothY = 0f
    private var smoothZ = 9.8f

    var pitchDeg: Float = 0f
        private set
    var rollDeg: Float = 0f
        private set
    var gazeX: Float = 0f
        private set
    var gazeY: Float = 0f
        private set
    var gravityX: Float = 0f
        private set
    var gravityY: Float = 0f
        private set
    var gravityZ: Float = 9.8f
        private set

    fun update(ax: Float, ay: Float, az: Float) {
        val alpha = 0.22f
        smoothX += alpha * (ax - smoothX)
        smoothY += alpha * (ay - smoothY)
        smoothZ += alpha * (az - smoothZ)
        gravityX = smoothX
        gravityY = smoothY
        gravityZ = smoothZ

        // Portrait হাতে ধরা অবস্থায় বাম-ডান টিল্টে rollDeg এবং সামনে-পেছনে হেলে পড়লে pitchDeg
        val rawRoll = (atan2(-smoothX.toDouble(), sqrt((smoothY * smoothY + smoothZ * smoothZ).toDouble())) * 180.0 / PI).toFloat()
        val rawPitch = (atan2(smoothY.toDouble(), smoothZ.toDouble()) * 180.0 / PI).toFloat() - 35f

        rollDeg = rawRoll.coerceIn(-24f, 24f)
        pitchDeg = rawPitch.coerceIn(-24f, 24f)
        gazeX = (rollDeg / 20f).coerceIn(-1f, 1f)
        gazeY = (pitchDeg / 20f).coerceIn(-1f, 1f)
    }

    fun reset() {
        smoothX = 0f; smoothY = 0f; smoothZ = 9.8f
        pitchDeg = 0f; rollDeg = 0f; gazeX = 0f; gazeY = 0f
    }
}

/**
 * ২-ধাপের ড্রপ ডিটেক্টর (Free-Fall -> Impact) + জোরে ঝাঁকি (Shake -> Dizzy) + FaceDown/FaceUp ডিটেক্টর।
 * কোনো Android SensorManager ডিপেন্ডেন্সি ছাড়াই বিশুদ্ধ লজিক — সরাসরি ইউনিট টেস্টযোগ্য।
 */
class DropAndShakeDetector {
    sealed class Output {
        data object FreeFall : Output()
        data class Dropped(val impactG: Float) : Output()
        data object Shake : Output()
        data object VigorousDizzyShake : Output()
        data object FaceDown : Output()
        data object FaceUp : Output()
    }

    private var freeFallStartedMs = -1L
    private var freeFallConfirmedMs = -1L
    private var freeFallEmitted = false
    private var lastDropMs = -10_000L
    private var lastShakeMs = -10_000L
    private var shakeTimestamps = ArrayDeque<Long>()

    private var faceDownSinceMs = -1L
    private var isCurrentlyFaceDown = false
    private var lowPassZ = 9.8f

    fun onAccelerometerSample(ax: Float, ay: Float, az: Float, nowMs: Long): List<Output> {
        val out = ArrayList<Output>(2)
        val mag = sqrt(ax * ax + ay * ay + az * az)

        // ১) ফ্রি-ফল (মুক্ত পতন): ওজনশূন্যতা < 2.4 m/s^2 কমপক্ষে 120ms ধরে থাকলে
        if (mag < FREE_FALL_MAG) {
            if (freeFallStartedMs < 0L) freeFallStartedMs = nowMs
            if (!freeFallEmitted && nowMs - freeFallStartedMs >= FREE_FALL_MIN_MS) {
                freeFallEmitted = true
                freeFallConfirmedMs = nowMs
                out.add(Output.FreeFall)
            }
        } else {
            // ফ্রি-ফলের পরপরই বড় ধাক্কা -> পড়ে যাওয়া (Dropped)
            val withinFallWindow = freeFallConfirmedMs > 0L && (nowMs - freeFallConfirmedMs <= DROP_IMPACT_WINDOW_MS)
            if (withinFallWindow && mag >= DROP_IMPACT_AFTER_FALL_MAG && nowMs - lastDropMs >= DROP_COOLDOWN_MS) {
                lastDropMs = nowMs
                resetFreeFall()
                out.add(Output.Dropped(mag / 9.81f))
                return out
            }
            if (freeFallConfirmedMs > 0L && nowMs - freeFallConfirmedMs > DROP_IMPACT_WINDOW_MS) {
                resetFreeFall()
            } else if (mag >= 4.2f && !freeFallEmitted) {
                freeFallStartedMs = -1L
            }
        }

        // ২) তীব্র আঘাত / সরাসরি ঠাস করে ফেলা (>= 27.5 m/s^2 ~ 2.8g)
        if (mag >= HARD_SLAM_MAG && nowMs - lastDropMs >= DROP_COOLDOWN_MS && freeFallStartedMs < 0L) {
            lastDropMs = nowMs
            resetFreeFall()
            out.add(Output.Dropped(mag / 9.81f))
            return out
        }

        // ৩) ঝাঁকি (Shake): 17.5..27.4 m/s^2 এবং ফ্রি-ফলে না থাকলে
        if (mag >= SHAKE_MAG && !withinRecentFall(nowMs) && nowMs - lastShakeMs >= SHAKE_COOLDOWN_MS) {
            lastShakeMs = nowMs
            shakeTimestamps.addLast(nowMs)
            while (shakeTimestamps.isNotEmpty() && nowMs - shakeTimestamps.first() > DIZZY_SHAKE_WINDOW_MS) {
                shakeTimestamps.removeFirst()
            }
            if (shakeTimestamps.size >= 3) {
                shakeTimestamps.clear()
                out.add(Output.VigorousDizzyShake)
            } else {
                out.add(Output.Shake)
            }
        }

        // ৪) উল্টো হয়ে থাকা (FaceDown / FaceUp)
        lowPassZ += 0.25f * (az - lowPassZ)
        if (!isCurrentlyFaceDown) {
            if (lowPassZ < FACE_DOWN_Z) {
                if (faceDownSinceMs < 0L) faceDownSinceMs = nowMs
                if (nowMs - faceDownSinceMs >= FACE_DOWN_HOLD_MS) {
                    isCurrentlyFaceDown = true
                    out.add(Output.FaceDown)
                }
            } else {
                faceDownSinceMs = -1L
            }
        } else if (lowPassZ > FACE_UP_Z) {
            isCurrentlyFaceDown = false
            faceDownSinceMs = -1L
            out.add(Output.FaceUp)
        }

        return out
    }

    private fun withinRecentFall(nowMs: Long): Boolean =
        freeFallConfirmedMs > 0L && (nowMs - freeFallConfirmedMs <= 700L)

    private fun resetFreeFall() {
        freeFallStartedMs = -1L
        freeFallConfirmedMs = -1L
        freeFallEmitted = false
    }

    companion object {
        private const val FREE_FALL_MAG = 2.4f
        private const val FREE_FALL_MIN_MS = 120L
        private const val DROP_IMPACT_WINDOW_MS = 1600L
        private const val DROP_IMPACT_AFTER_FALL_MAG = 18.5f
        private const val HARD_SLAM_MAG = 27.5f
        private const val DROP_COOLDOWN_MS = 2500L
        private const val SHAKE_MAG = 17.2f
        private const val SHAKE_COOLDOWN_MS = 700L
        private const val DIZZY_SHAKE_WINDOW_MS = 2200L
        private const val FACE_DOWN_Z = -8.2f
        private const val FACE_DOWN_HOLD_MS = 900L
        private const val FACE_UP_Z = 6.2f
    }
}

/**
 * কম্পাস / ম্যাগনেটোমিটার ঘূর্ণন ডিটেক্টর — 0°/360° wrap-around সামলে দ্রুত ঘুরলে Dizzy ডিটেক্ট করে।
 */
class CompassSpinTracker {
    private data class Sample(val tMs: Long, val deltaDeg: Float)
    private var prevAzimuth: Float? = null
    private val history = ArrayDeque<Sample>()
    private var lastSpinMs = -10_000L

    fun onAzimuth(deg: Float, nowMs: Long): Boolean {
        val norm = ((deg % 360f) + 360f) % 360f
        val prev = prevAzimuth
        prevAzimuth = norm
        if (prev == null) return false

        var delta = norm - prev
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        history.addLast(Sample(nowMs, delta))
        while (history.isNotEmpty() && nowMs - history.first().tMs > SPIN_WINDOW_MS) {
            history.removeFirst()
        }
        val netRotation = history.sumOf { it.deltaDeg.toDouble() }.toFloat()
        if (abs(netRotation) >= SPIN_THRESHOLD_DEG && nowMs - lastSpinMs >= SPIN_COOLDOWN_MS) {
            lastSpinMs = nowMs
            history.clear()
            return true
        }
        return false
    }

    fun reset() {
        prevAzimuth = null
        history.clear()
    }

    companion object {
        private const val SPIN_WINDOW_MS = 2800L
        private const val SPIN_THRESHOLD_DEG = 290f
        private const val SPIN_COOLDOWN_MS = 3000L
    }
}

/**
 * ফোনের ফিজিক্যাল হার্ডওয়্যার সেন্সর (Accelerometer, Magnetometer, Proximity, Light, Battery)
 * এবং EmoBrainHub-কে সংযুক্ত করে। শুধু ফোরগ্রাউন্ড স্ক্রিন খোলা থাকলেই চালু থাকে।
 */
class EmoBodySensorController(context: Context) : SensorEventListener {

    private val appCtx = context.applicationContext
    private val sensorManager = appCtx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val proxSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val tiltFilter = BodyTiltFilter()
    private val dropShake = DropAndShakeDetector()
    private val compassSpin = CompassSpinTracker()

    private val accelVec = FloatArray(3)
    private val magVec = FloatArray(3)
    private val rotMat = FloatArray(9)
    private val orientAngles = FloatArray(3)
    private var hasMag = false

    private var running = false
    private var lastUiSyncMs = 0L
    private var lastLightReactionMs = 0L
    private var isDarkState = false
    private var batteryRegistered = false

    private var currentAzimuth = 0f
    private var currentLux = 180f
    private var currentNearEar = false
    private var currentBatteryPct = 80
    private var currentCharging = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val i = intent ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else currentBatteryPct
            val becameCharging = !currentCharging && charging
            val becameLow = currentBatteryPct > 18 && pct <= 18 && !charging
            currentBatteryPct = pct
            currentCharging = charging

            EmoBrainHub.updateBody {
                it.copy(batteryPct = pct, isCharging = charging)
            }
            if (becameCharging) {
                EmoBrainHub.handleEvent(RobotEvent.Charging)
            } else if (becameLow) {
                EmoBrainHub.handleEvent(RobotEvent.BatteryLow)
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        tiltFilter.reset()
        compassSpin.reset()
        accelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proxSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }

        runCatching {
            appCtx.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryRegistered = true
        }

        EmoBrainHub.updateBody { it.copy(sensorsLive = true) }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { sensorManager?.unregisterListener(this) }
        if (batteryRegistered) {
            runCatching { appCtx.unregisterReceiver(batteryReceiver) }
            batteryRegistered = false
        }
        EmoBrainHub.updateBody {
            it.copy(sensorsLive = false, isNearEar = false, pitchDeg = 0f, rollDeg = 0f, tiltGazeX = 0f, tiltGazeY = 0f)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        val now = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                accelVec[0] = ax; accelVec[1] = ay; accelVec[2] = az
                tiltFilter.update(ax, ay, az)

                val outputs = dropShake.onAccelerometerSample(ax, ay, az, now)
                for (o in outputs) {
                    when (o) {
                        DropAndShakeDetector.Output.FreeFall ->
                            EmoBrainHub.handleEvent(RobotEvent.FreeFall)
                        is DropAndShakeDetector.Output.Dropped -> {
                            EmoBrainHub.updateBody { it.copy(lastImpactG = o.impactG) }
                            EmoBrainHub.handleEvent(RobotEvent.Dropped)
                        }
                        DropAndShakeDetector.Output.Shake ->
                            EmoBrainHub.handleEvent(RobotEvent.Shake)
                        DropAndShakeDetector.Output.VigorousDizzyShake -> {
                            EmoBrainHub.handleEvent(RobotEvent.Shake)
                            EmoBrainHub.express(
                                FaceId.SWIRL,
                                "ওরে বাবা, চক্কর খাচ্ছি! 😵‍💫",
                                forceSpeech = true,
                            )
                            EmoBrainHub.triggerEffect(BodyEffect.STARS)
                        }
                        DropAndShakeDetector.Output.FaceDown ->
                            EmoBrainHub.handleEvent(RobotEvent.FaceDown)
                        DropAndShakeDetector.Output.FaceUp ->
                            EmoBrainHub.handleEvent(RobotEvent.FaceUp)
                    }
                }

                if (hasMag && SensorManager.getRotationMatrix(rotMat, null, accelVec, magVec)) {
                    SensorManager.getOrientation(rotMat, orientAngles)
                    val deg = (((orientAngles[0] * 180f / PI.toFloat()) % 360f) + 360f) % 360f
                    currentAzimuth = deg
                    if (compassSpin.onAzimuth(deg, now)) {
                        EmoBrainHub.handleEvent(RobotEvent.CompassSpin)
                    }
                }

                if (now - lastUiSyncMs >= 55L) {
                    lastUiSyncMs = now
                    val card = EmoBodyState.cardinalFromAzimuth(currentAzimuth)
                    EmoBrainHub.updateBody {
                        it.copy(
                            sensorsLive = true,
                            pitchDeg = tiltFilter.pitchDeg,
                            rollDeg = tiltFilter.rollDeg,
                            tiltGazeX = tiltFilter.gazeX,
                            tiltGazeY = tiltFilter.gazeY,
                            azimuthDeg = currentAzimuth,
                            compassLabel = card,
                        )
                    }
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                magVec[0] = event.values[0]
                magVec[1] = event.values[1]
                magVec[2] = event.values[2]
                hasMag = true
            }

            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange.coerceAtLeast(1f)
                val near = event.values[0] < minOf(maxRange, 4.5f)
                if (near != currentNearEar) {
                    currentNearEar = near
                    EmoBrainHub.updateBody { it.copy(isNearEar = near) }
                    EmoBrainHub.handleEvent(if (near) RobotEvent.EarNear else RobotEvent.EarAway)
                }
            }

            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                currentLux = lux
                EmoBrainHub.updateBody { it.copy(lux = lux) }
                if (now - lastLightReactionMs > 4500L) {
                    if (!isDarkState && lux < 6f) {
                        isDarkState = true
                        lastLightReactionMs = now
                        EmoBrainHub.handleEvent(RobotEvent.Dark)
                    } else if (isDarkState && lux > 35f) {
                        isDarkState = false
                        lastLightReactionMs = now
                        EmoBrainHub.handleEvent(RobotEvent.Bright)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * ব্রেন ইভেন্ট হলেই EMO-র স্পিকার (TTS + Synth সাউন্ড) ও হ্যাপটিক মোটর (Heartbeat/Cry/Dizzy) সক্রিয় করে।
 */
class EmoLiveReactor(context: Context) {
    private val appCtx = context.applicationContext
    val voice = EmoVoiceEngine(appCtx)
    private var attached = false

    private val sink: (RobotEvent) -> Unit = { event ->
        val soft = EmoBrainHub.bodyState.isNearEar
        when (event) {
            RobotEvent.FreeFall -> {
                voice.react(EmoEventReaction(EmoTone.GASP_FALL, "আরে, পড়ে যাচ্ছি!", urgent = true), soft)
            }
            RobotEvent.Dropped -> {
                voice.react(EmoEventReaction(EmoTone.CRY_SOB, "উফ! ব্যথা পেলাম তো, আদর করো!", urgent = true), soft)
                EmoHaptics.cryTremble(appCtx)
            }
            RobotEvent.Comforted -> {
                voice.react(EmoEventReaction(EmoTone.PURR_WARM, "এখন শান্ত লাগছে!", urgent = true), soft)
                EmoHaptics.purr(appCtx)
            }
            RobotEvent.LongPress -> {
                voice.react(EmoEventReaction(EmoTone.PURR_WARM, "আদর ভালো লাগছে"), soft)
                EmoHaptics.purr(appCtx)
            }
            RobotEvent.Tap -> {
                voice.react(EmoEventReaction(EmoTone.HAPPY_CHIRP, null), soft)
                EmoHaptics.tapPulse(appCtx)
            }
            RobotEvent.Shake -> {
                voice.react(EmoEventReaction(EmoTone.DIZZY_WARBLE, "আরে আস্তে!", urgent = false), soft)
                EmoHaptics.dizzyBuzz(appCtx)
            }
            RobotEvent.CompassSpin -> {
                voice.react(EmoEventReaction(EmoTone.DIZZY_WARBLE, "মাথা ঘুরছে!", urgent = true), soft)
                EmoHaptics.dizzyBuzz(appCtx)
            }
            RobotEvent.FingerprintMatched -> {
                voice.react(EmoEventReaction(EmoTone.HAPPY_CHIRP, "চিনতে পেরেছি! তুমি আমার প্রিয় মানুষ", urgent = true), soft)
                EmoHaptics.heartbeat(appCtx)
            }
            RobotEvent.FingerprintRejected -> {
                voice.react(EmoEventReaction(EmoTone.CURIOUS_BLIP, "ফিঙ্গারপ্রিন্ট মিলল না তো!"), soft)
                EmoHaptics.tapPulse(appCtx)
            }
            RobotEvent.Lift -> {
                voice.react(EmoEventReaction(EmoTone.CURIOUS_BLIP, null), soft)
                EmoHaptics.tapPulse(appCtx)
            }
            RobotEvent.EarNear -> {
                voice.react(EmoEventReaction(EmoTone.CURIOUS_BLIP, "কানের কাছে কী বলবে?", urgent = true), softNearEar = true)
            }
            RobotEvent.EarAway -> {
                voice.react(EmoEventReaction(EmoTone.HAPPY_CHIRP, "পিক আ বু!"), softNearEar = false)
            }
            RobotEvent.Clap, RobotEvent.LoudSound, RobotEvent.VoiceHeard -> {
                voice.react(EmoEventReaction(EmoTone.CURIOUS_BLIP, null), soft)
            }
            RobotEvent.Charging, RobotEvent.Bright, RobotEvent.FaceUp -> {
                voice.react(EmoEventReaction(EmoTone.WAKE_CHIME, null), soft)
            }
            RobotEvent.KeywordLaugh, RobotEvent.KeywordKiss, RobotEvent.KeywordCute -> {
                voice.react(EmoEventReaction(EmoTone.HAPPY_CHIRP, null), soft)
            }
            RobotEvent.KeywordShock -> {
                voice.react(EmoEventReaction(EmoTone.CURIOUS_BLIP, null), soft)
            }
            else -> Unit
        }
    }

    fun start() {
        if (attached) return
        attached = true
        EmoBrainHub.addEventSink(sink)
    }

    fun stop() {
        if (!attached) return
        attached = false
        EmoBrainHub.removeEventSink(sink)
    }

    fun release() {
        stop()
        voice.release()
    }
}
