package com.banglakeys.app.robot

/**
 * রেফারেন্স শীটের ৮টি HD কার্টুন EMO চোখের স্টাইল (Classic EMO Cyan Glow)।
 */
enum class HdEyeStyle(
    val titleBn: String,
    val titleEn: String,
    val badge: String,
) {
    GLANCE("তাকানো", "Glance", "👀"),
    CHILL("রিল্যাক্স", "Chill", "😌"),
    SPARKLE("ঝলমলে", "Sparkle", "🥺"),
    GOOGLY("ঘোরানো", "Googly", "😵‍💫"),
    PINPOINT("অবাক", "Pinpoint", "😳"),
    FOCUSED("গভীর", "Focused", "🤩"),
    PEEK("খুশি", "Peek", "😊"),
    FIERCE("রাগী", "Fierce", "😠");

    companion object {
        fun fromFace(face: FaceId): HdEyeStyle = when (face) {
            FaceId.CURIOUS, FaceId.NORMAL -> GLANCE
            FaceId.REST, FaceId.CALM, FaceId.BORED, FaceId.MEH, FaceId.SMUG -> CHILL
            FaceId.JOY, FaceId.BEAMING, FaceId.UWU, FaceId.GIGGLE, FaceId.KISS_SHY, FaceId.WINK_KISS -> SPARKLE
            FaceId.DIZZY, FaceId.SWIRL -> GOOGLY
            FaceId.SHOCKED, FaceId.NERVOUS, FaceId.SWEAT, FaceId.PLEAD, FaceId.CRY_RIVER -> PINPOINT
            FaceId.STARE, FaceId.HYPED, FaceId.SQUEEZE -> FOCUSED
            FaceId.SMILE, FaceId.WINK, FaceId.MISCHIEF, FaceId.SLY -> PEEK
            FaceId.ANGRY, FaceId.FURIOUS, FaceId.SHOUT, FaceId.HMPH, FaceId.GRUMPY -> FIERCE
        }
    }
}
