package com.banglakeys.app.robot

/**
 * ফুল-বডি EMO রোবটের দুটি সুইচযোগ্য থিম/স্কিন:
 * - [LAVENDER_3D]: নরম প্যাস্টেল বেগুনি 3D চাবি বডি + ক্রিম ভিসর + গ্লসি ডার্ক চোখ
 * - [CLASSIC_CYAN]: অবসিডিয়ান ডার্ক চ্যাসিস + নিয়ন সায়ান গ্লো + HD সায়ান চোখ
 */
enum class RobotBodySkin(
    val titleBn: String,
    val titleEn: String,
    val badge: String,
    val bodyHighlight: Int,
    val bodyCore: Int,
    val bodyShadow: Int,
    val bodyRim: Int,
    val limbCore: Int,
    val limbShadow: Int,
    val visorTop: Int,
    val visorBottom: Int,
    val visorOutline: Int,
    val eyeAccent: Int,
    val blushColor: Int,
    val motionLineColor: Int,
    val shadowColor: Int,
    val isLightVisor: Boolean,
) {
    LAVENDER_3D(
        titleBn = "ল্যাভেন্ডার 3D",
        titleEn = "Lavender",
        badge = "💜",
        bodyHighlight = 0xFFE3D0FF.toInt(),
        bodyCore = 0xFFC5A3FF.toInt(),
        bodyShadow = 0xFF9167DB.toInt(),
        bodyRim = 0xFFF3EAFF.toInt(),
        limbCore = 0xFFCCAFFF.toInt(),
        limbShadow = 0xFF8C62D4.toInt(),
        visorTop = 0xFFFFFBF4.toInt(),
        visorBottom = 0xFFF7EBDD.toInt(),
        visorOutline = 0xFFE4D0F7.toInt(),
        eyeAccent = 0xFF231B2E.toInt(),
        blushColor = 0xFFFF96AC.toInt(),
        motionLineColor = 0xFFB892FF.toInt(),
        shadowColor = 0x3A6E4CA8,
        isLightVisor = true,
    ),
    CLASSIC_CYAN(
        titleBn = "ক্লাসিক সায়ান",
        titleEn = "Cyan",
        badge = "🩵",
        bodyHighlight = 0xFF1C2C42.toInt(),
        bodyCore = 0xFF101927.toInt(),
        bodyShadow = 0xFF070C14.toInt(),
        bodyRim = 0xFF36D1FF.toInt(),
        limbCore = 0xFF142133.toInt(),
        limbShadow = 0xFF080E18.toInt(),
        visorTop = 0xFF0B131E.toInt(),
        visorBottom = 0xFF050A11.toInt(),
        visorOutline = 0x9936D1FF.toInt(),
        eyeAccent = 0xFF36D1FF.toInt(),
        blushColor = 0xFFFF6B9C.toInt(),
        motionLineColor = 0xFF66E2FF.toInt(),
        shadowColor = 0x55001622,
        isLightVisor = false,
    );

    fun toggled(): RobotBodySkin = if (this == LAVENDER_3D) CLASSIC_CYAN else LAVENDER_3D
}
