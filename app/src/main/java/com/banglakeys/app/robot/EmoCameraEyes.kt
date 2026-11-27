package com.banglakeys.app.robot

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * সামনের ক্যামেরা ব্যবহার করে EMO-র "চোখ" (Eye Cam):
 * - কেবল EMO Body স্ক্রিন খোলা থাকলে ও Eye Cam অন করলেই চালু হয় (কোনো ছবি সেভ বা রেকর্ড হয় না)
 * - Y-plane আলো ও সামনের মানুষ/সাবজেক্টের কেন্দ্র বিশ্লেষণ করে EMO-র চোখের দৃষ্টি ডানে-বামে ও উপরে-নিচে ঘোরায়
 * - ক্যামেরা হাত দিয়ে ঢেকে দিলে বা অন্ধকারে গেলে EMO তা বুঝতে পারে
 */
class EmoCameraEyes(private val context: Context) {

    private val appCtx = context.applicationContext
    private val analyzerExec = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    @Volatile private var active = false

    private var smoothX = 0f
    private var smoothY = 0f
    private var lastAnalyzeMs = 0L
    private var darkFrames = 0
    private var wasCovered = false

    fun start(lifecycleOwner: LifecycleOwner, onError: ((String) -> Unit)? = null) {
        if (active) return
        active = true
        val future = ProcessCameraProvider.getInstance(appCtx)
        future.addListener({
            if (!active) return@addListener
            runCatching {
                val camProvider = future.get()
                provider = camProvider
                val resSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(320, 240),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analyzerExec) { proxy ->
                    analyzeFrame(proxy)
                }

                camProvider.unbindAll()
                camProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
                EmoBrainHub.updateBody { it.copy(cameraEyesActive = true) }
            }.onFailure { err ->
                active = false
                EmoBrainHub.updateBody { it.copy(cameraEyesActive = false, faceDetected = false) }
                onError?.invoke(err.message ?: "Front camera unavailable")
            }
        }, ContextCompat.getMainExecutor(appCtx))
    }

    fun stop() {
        active = false
        runCatching {
            ContextCompat.getMainExecutor(appCtx).execute {
                runCatching { provider?.unbindAll() }
            }
        }
        darkFrames = 0
        wasCovered = false
        EmoBrainHub.updateBody {
            it.copy(cameraEyesActive = false, faceDetected = false, camGazeX = 0f, camGazeY = 0f)
        }
    }

    fun shutdown() {
        stop()
        analyzerExec.shutdownNow()
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (!active || now - lastAnalyzeMs < 150L) return
            lastAnalyzeMs = now

            val plane = proxy.planes.firstOrNull() ?: return
            val buf = plane.buffer
            val w = proxy.width
            val h = proxy.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            if (w <= 0 || h <= 0) return

            val stepX = (w / 24).coerceAtLeast(1)
            val stepY = (h / 18).coerceAtLeast(1)

            var totalLuma = 0L
            var sampleCount = 0
            var weightSum = 0f
            var sumNormX = 0f
            var sumNormY = 0f

            var y = 0
            while (y < h) {
                val rowOff = y * rowStride
                var x = 0
                while (x < w) {
                    val idx = rowOff + x * pixelStride
                    if (idx < buf.limit()) {
                        val luma = buf.get(idx).toInt() and 0xFF
                        totalLuma += luma
                        sampleCount++
                        // মুখ/সাবজেক্টের উজ্জ্বলতা ও কনট্রাস্ট কেন্দ্র হিসেবে ধরতে উঁচু লুমিন্যান্সে ওজন দেওয়া
                        if (luma > 55) {
                            val nx = (x.toFloat() / w) * 2f - 1f
                            val ny = (y.toFloat() / h) * 2f - 1f
                            val weight = (luma - 50) / 205f
                            sumNormX += nx * weight
                            sumNormY += ny * weight
                            weightSum += weight
                        }
                    }
                    x += stepX
                }
                y += stepY
            }

            if (sampleCount == 0) return
            val avgLuma = totalLuma.toFloat() / sampleCount

            if (avgLuma < 14f) {
                darkFrames++
                if (darkFrames >= 4 && !wasCovered) {
                    wasCovered = true
                    EmoBrainHub.updateBody { it.copy(faceDetected = false, camGazeX = 0f, camGazeY = 0f) }
                    EmoBrainHub.handleEvent(RobotEvent.Dark)
                }
                return
            } else {
                if (wasCovered && avgLuma > 28f) {
                    wasCovered = false
                    EmoBrainHub.handleEvent(RobotEvent.Bright)
                }
                darkFrames = 0
            }

            val detected = weightSum > 4f
            if (detected) {
                // সামনের ক্যামেরা মিরর হওয়ায় X দিক উল্টে দেওয়া হয় যাতে ইউজার ডানে গেলে EMO ডানে তাকায়
                val targetX = (-(sumNormX / weightSum) * 1.4f).coerceIn(-1f, 1f)
                val targetY = ((sumNormY / weightSum) * 1.2f).coerceIn(-1f, 1f)
                if (abs(targetX - smoothX) > 0.03f || abs(targetY - smoothY) > 0.03f) {
                    smoothX += 0.30f * (targetX - smoothX)
                    smoothY += 0.30f * (targetY - smoothY)
                }
            }
            EmoBrainHub.updateBody {
                it.copy(
                    cameraEyesActive = true,
                    faceDetected = detected,
                    camGazeX = if (detected) smoothX else 0f,
                    camGazeY = if (detected) smoothY else 0f,
                )
            }
        } finally {
            proxy.close()
        }
    }
}
