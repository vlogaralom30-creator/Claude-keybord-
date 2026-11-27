package com.banglakeys.app.robot

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.banglakeys.app.AssistantPrefs
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class EmoTone {
    HAPPY_CHIRP,
    CURIOUS_BLIP,
    DIZZY_WARBLE,
    CRY_SOB,
    PURR_WARM,
    GASP_FALL,
    WAKE_CHIME,
}

/**
 * ১৬-বিট PCM রোবট সাউন্ড সিন্থেসাইজার — কোনো অডিও ফাইল ছাড়াই
 * EMO-র মতো কিউট চিপ, ডিজি ওয়ার্বল, কান্নার শব্দ ও পার-পার সাউন্ড তৈরি করে।
 */
object EmoSoundSynth {
    private const val SAMPLE_RATE = 22050
    private val audioExec = Executors.newSingleThreadExecutor()

    fun play(tone: EmoTone, soft: Boolean = false) {
        audioExec.execute {
            runCatching {
                val samples = synthesize(tone, if (soft) 0.25f else 0.72f)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                val durationMs = (samples.size * 1000L) / SAMPLE_RATE
                Thread.sleep(durationMs + 40L)
                track.stop()
                track.release()
            }
        }
    }

    internal fun synthesize(tone: EmoTone, gain: Float): ShortArray {
        return when (tone) {
            EmoTone.HAPPY_CHIRP -> concatNotes(
                listOf(660f to 75, 880f to 85, 1175f to 130),
                gain
            )
            EmoTone.CURIOUS_BLIP -> glideTone(520f, 890f, 170, gain)
            EmoTone.DIZZY_WARBLE -> warbleTone(580f, 190f, 11f, 380, gain)
            EmoTone.CRY_SOB -> concatNotes(
                listOf(640f to 130, 520f to 150, 410f to 210),
                gain * 0.9f
            )
            EmoTone.PURR_WARM -> warbleTone(170f, 35f, 18f, 320, gain * 0.85f)
            EmoTone.GASP_FALL -> glideTone(480f, 1250f, 150, gain)
            EmoTone.WAKE_CHIME -> concatNotes(
                listOf(523f to 80, 659f to 80, 784f to 120),
                gain
            )
        }
    }

    private fun glideTone(f0: Float, f1: Float, ms: Int, gain: Float): ShortArray {
        val count = (SAMPLE_RATE * ms) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val freq = f0 + (f1 - f0) * t
            val env = sin(PI * t).toFloat()
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val v = (sin(phase) * env * gain * 18000).toInt().coerceIn(-32767, 32767)
            out[i] = v.toShort()
        }
        return out
    }

    private fun warbleTone(baseHz: Float, swingHz: Float, lfoHz: Float, ms: Int, gain: Float): ShortArray {
        val count = (SAMPLE_RATE * ms) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = sin(PI * (i.toDouble() / count)).toFloat()
            val freq = baseHz + sin(2.0 * PI * lfoHz * t).toFloat() * swingHz
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val v = (sin(phase) * env * gain * 16500).toInt().coerceIn(-32767, 32767)
            out[i] = v.toShort()
        }
        return out
    }

    private fun concatNotes(notes: List<Pair<Float, Int>>, gain: Float): ShortArray {
        val parts = notes.map { (freq, ms) ->
            val count = (SAMPLE_RATE * ms) / 1000
            val buf = ShortArray(count)
            for (i in 0 until count) {
                val p = i.toDouble() / count
                val env = (1.0 - exp(-p * 12.0)) * (1.0 - p * 0.85)
                val v = (sin(2.0 * PI * freq * i / SAMPLE_RATE) * env * gain * 17000)
                    .toInt().coerceIn(-32767, 32767)
                buf[i] = v.toShort()
            }
            buf
        }
        val total = parts.sumOf { it.size }
        val out = ShortArray(total)
        var offset = 0
        for (p in parts) {
            p.copyInto(out, offset)
            offset += p.size
        }
        return out
    }
}

/**
 * বাংলা TTS + রোবট টোন স্পিকার ইঞ্জিন।
 * কানের কাছে (Proximity near) ধরলে ভলিউম/শব্দ শান্ত হয়ে যায়।
 */
class EmoVoiceEngine(context: Context) {
    private val appCtx = context.applicationContext
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private var lastSpokeAt = 0L

    init {
        runCatching {
            tts = TextToSpeech(appCtx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    val bnBd = Locale("bn", "BD")
                    val res = engine.setLanguage(bnBd)
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        val bn = Locale("bn")
                        val resBn = engine.setLanguage(bn)
                        if (resBn == TextToSpeech.LANG_MISSING_DATA || resBn == TextToSpeech.LANG_NOT_SUPPORTED) {
                            engine.setLanguage(Locale.US)
                        }
                    }
                    engine.setPitch(1.18f)
                    engine.setSpeechRate(1.04f)
                    ready = true
                }
            }
        }
    }

    fun react(event: EmoEventReaction, softNearEar: Boolean) {
        if (!AssistantPrefs.emoVoiceSfxEnabled(appCtx)) return
        EmoSoundSynth.play(event.tone, soft = softNearEar)
        val line = event.spokenLine ?: return
        val now = System.currentTimeMillis()
        if (!event.urgent && now - lastSpokeAt < 2600L) return
        lastSpokeAt = now
        speak(line, soft = softNearEar)
    }

    fun speak(text: String, soft: Boolean = false) {
        if (!AssistantPrefs.emoVoiceSfxEnabled(appCtx) || !ready) return
        val clean = text.replace(Regex("[^\\p{L}\\p{N}\\s,.!?।]"), "").trim()
        if (clean.isEmpty()) return
        runCatching {
            val engine = tts ?: return
            engine.setPitch(if (soft) 1.05f else 1.2f)
            engine.setSpeechRate(if (soft) 0.94f else 1.05f)
            val bundle = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, if (soft) 0.35f else 0.95f)
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            engine.speak(clean, TextToSpeech.QUEUE_FLUSH, bundle, "emo_${System.currentTimeMillis()}")
        }
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
            tts = null
            ready = false
        }
    }
}

data class EmoEventReaction(
    val tone: EmoTone,
    val spokenLine: String? = null,
    val urgent: Boolean = false,
)

/**
 * ফিজিক্যাল হ্যাপটিক হার্টবিট, কান্নার কম্পন, পার ও ঝাঁকি ফিডব্যাক।
 */
object EmoHaptics {

    fun heartbeat(ctx: Context) {
        if (!AssistantPrefs.emoHapticsEnabled(ctx)) return
        vibrateWaveform(ctx, longArrayOf(0, 42, 115, 58), intArrayOf(0, 140, 0, 210))
    }

    fun purr(ctx: Context) {
        if (!AssistantPrefs.emoHapticsEnabled(ctx)) return
        vibrateWaveform(ctx, longArrayOf(0, 35, 55, 42, 55, 48), intArrayOf(0, 90, 0, 115, 0, 95))
    }

    fun dizzyBuzz(ctx: Context) {
        if (!AssistantPrefs.emoHapticsEnabled(ctx)) return
        vibrateWaveform(ctx, longArrayOf(0, 45, 70, 45, 70, 55), intArrayOf(0, 180, 0, 150, 0, 190))
    }

    fun cryTremble(ctx: Context) {
        if (!AssistantPrefs.emoHapticsEnabled(ctx)) return
        vibrateWaveform(ctx, longArrayOf(0, 75, 85, 55, 110, 70), intArrayOf(0, 220, 0, 160, 0, 190))
    }

    fun tapPulse(ctx: Context) {
        if (!AssistantPrefs.emoHapticsEnabled(ctx)) return
        vibrateWaveform(ctx, longArrayOf(0, 26), intArrayOf(0, 120))
    }

    private fun vibrateWaveform(ctx: Context, timings: LongArray, amplitudes: IntArray) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) {
                val eff = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                vibrator.vibrate(eff)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        }
    }
}
