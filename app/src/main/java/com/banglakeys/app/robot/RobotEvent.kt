package com.banglakeys.app.robot

/**
 * EMO রোবট ব্রেইনের সব ইভেন্ট:
 * স্পর্শ, ফিজিক্যাল মোশন (ঝাঁকি, পড়ে যাওয়া, তোলা), কম্পাস, ইয়ার প্রক্সিমিটি,
 * ফিঙ্গারপ্রিন্ট আইডেন্টিটি, লাইট, ব্যাটারি এবং কিবোর্ড টাইপিং সিগন্যাল।
 */
sealed class RobotEvent {
    object Tap : RobotEvent()
    object LongPress : RobotEvent()
    object Shake : RobotEvent()
    object Lift : RobotEvent()
    object FreeFall : RobotEvent()
    object Dropped : RobotEvent()
    object Comforted : RobotEvent()
    object FaceDown : RobotEvent()
    object FaceUp : RobotEvent()
    object Clap : RobotEvent()
    object LoudSound : RobotEvent()
    object VoiceHeard : RobotEvent()
    object BatteryLow : RobotEvent()
    object Charging : RobotEvent()
    object Dark : RobotEvent()
    object Bright : RobotEvent()
    object FingerprintMatched : RobotEvent()
    object FingerprintRejected : RobotEvent()
    object CompassSpin : RobotEvent()
    object EarNear : RobotEvent()
    object EarAway : RobotEvent()
    object TypingFast : RobotEvent()
    object TypingSlow : RobotEvent()
    object BackspaceBurst : RobotEvent()
    object KeywordLove : RobotEvent()
    object KeywordAnger : RobotEvent()
    object KeywordLaugh : RobotEvent()
    object KeywordKiss : RobotEvent()
    object KeywordShock : RobotEvent()
    object KeywordCry : RobotEvent()
    object KeywordCute : RobotEvent()
    object MessageSent : RobotEvent()
}
