package com.banglakeys.app

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * কিবোর্ডের performance রেকর্ড + auto-guard।
 *
 *  • প্রতিটি onDraw-এর সময় (CPU) ও "touch → পরের draw শেষ" latency শেষ ১২৮টা ring buffer-এ (কোনো allocation নেই)।
 *  • প্রতি ৯০ frame-এ guard: p95 draw > 12ms বা avg > 7ms বা p95 latency > 45ms টানা ২ বার → effects নিজে বন্ধ।
 *  • প্রতি ৯০০ frame-এ একটা সারসংক্ষেপ লাইন files/fx_perf_log.jsonl-এ (event = "baseline" যখন effects বন্ধ,
 *    "with_fx" যখন চালু) — পরে তুলনা করে বোঝা যায় কিছু ধীর হয়েছে কিনা। ফাইল ~৬৪KB-এর বেশি বাড়ে না।
 *
 * দেখতে: adb shell run-as <package> cat files/fx_perf_log.jsonl
 */
object PerfRecorder {
    private const val N = 128
    private const val CHECK_EVERY = 90
    private const val LOG_EVERY = 900
    private const val MIN_SAMPLES = 60
    const val AVG_DRAW_LIMIT_MS = 7.0
    const val P95_DRAW_LIMIT_MS = 12.0
    const val P95_LAT_LIMIT_MS = 45.0

    private val drawNs = LongArray(N)
    private var dIdx = 0
    private var dCount = 0
    private val latNs = LongArray(N)
    private var lIdx = 0
    private var lCount = 0
    private var downNs = 0L
    private var frames = 0
    private var strikes = 0
    var lastReason: String = ""
        private set

    private val io = Executors.newSingleThreadExecutor()

    data class Snapshot(
        val samples: Int,
        val avgDrawMs: Double,
        val p95DrawMs: Double,
        val latSamples: Int,
        val avgLatMs: Double,
        val p95LatMs: Double,
    )

    fun markDown() {
        downNs = System.nanoTime()
    }

    fun clearPendingDown() {
        downNs = 0L
    }

    fun onDrawDone(startNs: Long) {
        val now = System.nanoTime()
        drawNs[dIdx] = (now - startNs).coerceAtLeast(0L)
        dIdx = (dIdx + 1) % N
        if (dCount < N) dCount++
        val dn = downNs
        if (dn != 0L) {
            downNs = 0L
            val lat = now - dn
            // ১ সেকেন্ডের বেশি হলে সেটা detached/paused অবস্থায় আটকে থাকা পুরনো touch — ignore করি
            if (lat in 0L..1_000_000_000L) {
                latNs[lIdx] = lat
                lIdx = (lIdx + 1) % N
                if (lCount < N) lCount++
            }
        }
        frames++
    }

    /** onDraw-র শেষে ডাকুন। true = effects বন্ধ করতে হবে। */
    fun tick(ctx: Context, fxActive: Boolean, fxLevel: Int): Boolean {
        if (frames == 0 || frames % CHECK_EVERY != 0) return false
        val s = snapshot()
        var disable = false
        if (fxActive && s.samples >= MIN_SAMPLES) {
            val bad = s.avgDrawMs > AVG_DRAW_LIMIT_MS || s.p95DrawMs > P95_DRAW_LIMIT_MS ||
                (s.latSamples >= 20 && s.p95LatMs > P95_LAT_LIMIT_MS)
            strikes = if (bad) strikes + 1 else 0
            if (strikes >= 2) {
                lastReason = String.format(
                    Locale.US,
                    "slow: avg=%.1fms p95=%.1fms lat95=%.1fms",
                    s.avgDrawMs,
                    s.p95DrawMs,
                    s.p95LatMs,
                )
                log(ctx, "auto_disable", s, fxLevel, lastReason)
                strikes = 0
                disable = true
            }
        } else {
            strikes = 0
        }
        if (frames % LOG_EVERY == 0 && s.samples >= MIN_SAMPLES) {
            log(ctx, if (fxActive) "with_fx" else "baseline", s, fxLevel, "")
        }
        return disable
    }

    fun snapshot(): Snapshot {
        val dr = drawNs.copyOf(dCount).also { it.sort() }
        val la = latNs.copyOf(lCount).also { it.sort() }
        return Snapshot(
            samples = dCount,
            avgDrawMs = avgMs(dr),
            p95DrawMs = percentile(dr, 95) / 1e6,
            latSamples = lCount,
            avgLatMs = avgMs(la),
            p95LatMs = percentile(la, 95) / 1e6,
        )
    }

    /** [sorted] ছোট→বড় সাজানো থাকতে হবে। খালি হলে 0। */
    fun percentile(sorted: LongArray, pct: Int): Long {
        if (sorted.isEmpty()) return 0L
        val idx = ((pct.coerceIn(0, 100) / 100.0) * (sorted.size - 1)).roundToInt()
        return sorted[idx]
    }

    private fun avgMs(a: LongArray): Double = if (a.isEmpty()) 0.0 else a.average() / 1e6

    fun reset() {
        dIdx = 0; dCount = 0; lIdx = 0; lCount = 0; frames = 0; strikes = 0; downNs = 0L
    }

    private fun r2(v: Double) = (v * 100.0).roundToInt() / 100.0

    private fun log(ctx: Context, event: String, s: Snapshot, level: Int, note: String) {
        val app = ctx.applicationContext
        val line = try {
            JSONObject()
                .put("t", System.currentTimeMillis())
                .put("event", event)
                .put("fx_level", level)
                .put("samples", s.samples)
                .put("avg_draw_ms", r2(s.avgDrawMs))
                .put("p95_draw_ms", r2(s.p95DrawMs))
                .put("avg_lat_ms", r2(s.avgLatMs))
                .put("p95_lat_ms", r2(s.p95LatMs))
                .put("note", note)
                .put("device", Build.MODEL)
                .put("sdk", Build.VERSION.SDK_INT)
                .toString()
        } catch (_: Exception) {
            return
        }
        io.execute {
            try {
                val f = File(app.filesDir, "fx_perf_log.jsonl")
                f.appendText(line + "\n")
                if (f.length() > 64 * 1024) {
                    f.writeText(f.readLines().takeLast(150).joinToString("\n") + "\n")
                }
            } catch (_: Exception) {
            }
        }
    }
}
