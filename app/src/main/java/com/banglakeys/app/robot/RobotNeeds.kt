package com.banglakeys.app.robot

/** Plan-এর Section 7 অনুযায়ী robot-এর "অবস্থা" — ০-১০০ স্কেলে। */
data class RobotNeeds(
    var energy: Int = 80,   // কমলে ঘুম পায় (hunger ~ battery, Phase 2-এ আসল battery জুড়বে)
    var bond: Int = 50,     // আদর/attachment, বেশি হলে বেশি positive mood
    var funLevel: Int = 60, // কম হলে বিরক্ত/বোরিং mood বেশি আসবে
) {
    fun clamp() {
        energy = energy.coerceIn(0, 100)
        bond = bond.coerceIn(0, 100)
        funLevel = funLevel.coerceIn(0, 100)
    }
}
