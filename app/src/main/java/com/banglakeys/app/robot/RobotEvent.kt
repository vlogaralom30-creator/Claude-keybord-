package com.banglakeys.app.robot

/**
 * Plan-এর Section 7-এর event bus — Phase 2-এ sensor/mic থেকে, Phase 3-এ keyboard
 * typing থেকে আসবে। এখন (Phase 1) শুধু fake button থেকে আসছে টেস্টের জন্য।
 */
sealed class RobotEvent {
    object Tap : RobotEvent()
    object LongPress : RobotEvent()
    object Shake : RobotEvent()
    object Lift : RobotEvent()
    object FaceDown : RobotEvent()
    object FaceUp : RobotEvent()
    object Clap : RobotEvent()
    object LoudSound : RobotEvent()
    object VoiceHeard : RobotEvent()
    object BatteryLow : RobotEvent()
    object Charging : RobotEvent()
    object Dark : RobotEvent()
    object Bright : RobotEvent()
    object TypingFast : RobotEvent()
    object TypingSlow : RobotEvent()
    object BackspaceBurst : RobotEvent()
    object KeywordLove : RobotEvent()
    object KeywordAnger : RobotEvent()
    object MessageSent : RobotEvent()
}
