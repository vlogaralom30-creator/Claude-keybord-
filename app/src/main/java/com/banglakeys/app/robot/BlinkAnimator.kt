package com.banglakeys.app.robot

/**
 * Plan-এর Section 8 অনুযায়ী: "blink (70-160 frame-এ একবার)"। এখানে frame-কে 60fps
 * ধরে মিলিসেকেন্ডে আনা হয়েছে (৭০-১৬০ frame ≈ ১.২-২.৭ সেকেন্ড)। [tick] প্রতি frame-এ
 * (Choreographer/View.postOnAnimation দিয়ে) call করুন, এটা blink progress (0f-1f)
 * ফেরত দেবে — সরাসরি [FaceRenderer.draw]-এর blink প্যারামিটারে বসিয়ে দিন।
 */
class BlinkAnimator(private val nowMs: () -> Long = { System.currentTimeMillis() }) {

    private var nextBlinkAt = scheduleNext()
    private var blinkStartAt = -1L
    private val blinkDurationMs = 140L // পুরো বন্ধ-হয়ে-খোলার সময়

    private fun scheduleNext(): Long {
        val gapMs = (1200..2700).random()
        return nowMs() + gapMs
    }

    /** @return 0f (চোখ খোলা) থেকে 1f (পুরো বন্ধ) — [FaceRenderer.draw]-এর blink arg-এ দিন */
    fun tick(): Float {
        val now = nowMs()
        if (blinkStartAt < 0 && now >= nextBlinkAt) {
            blinkStartAt = now
        }
        if (blinkStartAt < 0) return 0f

        val elapsed = now - blinkStartAt
        if (elapsed >= blinkDurationMs) {
            blinkStartAt = -1L
            nextBlinkAt = scheduleNext()
            return 0f
        }
        // bাঁও-ফেরত ত্রিকোণ তরঙ্গ: অর্ধেক সময় বন্ধ হওয়া, অর্ধেক সময় খোলা
        val half = blinkDurationMs / 2f
        val t = elapsed.toFloat()
        return if (t < half) t / half else 1f - (t - half) / half
    }
}
