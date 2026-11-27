package com.banglakeys.app.robot

enum class BodyEffect {
    NONE,
    TEARS,
    STARS,
    HEARTS,
    WHISPER,
}

/**
 * ফোনের রিয়েল-টাইম বডি সেন্সর ও ইন্দ্রিয় অবস্থা।
 */
data class EmoBodyState(
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val tiltGazeX: Float = 0f,
    val tiltGazeY: Float = 0f,
    val camGazeX: Float = 0f,
    val camGazeY: Float = 0f,
    val azimuthDeg: Float = 0f,
    val compassLabel: String = "N",
    val lux: Float = 180f,
    val isNearEar: Boolean = false,
    val cameraEyesActive: Boolean = false,
    val faceDetected: Boolean = false,
    val batteryPct: Int = 80,
    val isCharging: Boolean = false,
    val sensorsLive: Boolean = false,
    val lastImpactG: Float = 9.8f,
    val lastEffect: BodyEffect = BodyEffect.NONE,
    val lastEffectAtMs: Long = 0L,
) {
    fun combinedGazeX(): Float =
        (if (cameraEyesActive && faceDetected) camGazeX * 0.85f + tiltGazeX * 0.15f else tiltGazeX)
            .coerceIn(-1f, 1f)

    fun combinedGazeY(): Float =
        (if (cameraEyesActive && faceDetected) camGazeY * 0.85f + tiltGazeY * 0.15f else tiltGazeY)
            .coerceIn(-1f, 1f)

    companion object {
        fun cardinalFromAzimuth(deg: Float): String {
            val norm = ((deg % 360f) + 360f) % 360f
            return when {
                norm < 22.5f || norm >= 337.5f -> "N"
                norm < 67.5f -> "NE"
                norm < 112.5f -> "E"
                norm < 157.5f -> "SE"
                norm < 202.5f -> "S"
                norm < 247.5f -> "SW"
                norm < 292.5f -> "W"
                else -> "NW"
            }
        }
    }
}
