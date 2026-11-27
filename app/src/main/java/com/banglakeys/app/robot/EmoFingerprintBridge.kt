package com.banglakeys.app.robot

import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

/**
 * ফোনের আসল ফিঙ্গারপ্রিন্ট সেন্সর দিয়ে EMO মালিককে চেনার ব্রিজ।
 * ডিভাইসে ফিঙ্গারপ্রিন্ট হার্ডওয়্যার না থাকলে বা API < 28 হলে graceful অন-স্ক্রিন পাও হোল্ড-স্ক্যান fallback দেয়।
 */
object EmoFingerprintBridge {

    fun hasHardwareFingerprint(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return false
        if (Build.VERSION.SDK_INT >= 29) {
            val bm = activity.getSystemService(BiometricManager::class.java) ?: return false
            val res = if (Build.VERSION.SDK_INT >= 30) {
                bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                bm.canAuthenticate()
            }
            return res == BiometricManager.BIOMETRIC_SUCCESS
        }
        return true
    }

    fun launchFingerprintScan(
        activity: Activity,
        onMatched: () -> Unit = {},
        onRejected: (String) -> Unit = {},
        onNeedHoldFallback: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < 28 || !hasHardwareFingerprint(activity)) {
            onNeedHoldFallback()
            return
        }

        runCatching {
            val cancelSignal = CancellationSignal()
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("EMO Owner Scan")
                .setSubtitle("ফিঙ্গারপ্রিন্ট দিয়ে EMO-কে আপনার স্পর্শ চেনান")
                .setNegativeButton(
                    "বাতিল",
                    ContextCompat.getMainExecutor(activity)
                ) { _, _ ->
                    onRejected("বাতিল করা হয়েছে")
                }
                .build()

            prompt.authenticate(
                cancelSignal,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        EmoBrainHub.handleEvent(RobotEvent.FingerprintMatched)
                        onMatched()
                    }

                    override fun onAuthenticationFailed() {
                        EmoBrainHub.handleEvent(RobotEvent.FingerprintRejected)
                        onRejected("আঙুলের ছাপ মেলেনি")
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT
                        ) {
                            onNeedHoldFallback()
                        } else {
                            onRejected(errString?.toString() ?: "ফিঙ্গারপ্রিন্ট বাতিল")
                        }
                    }
                }
            )
        }.onFailure {
            onNeedHoldFallback()
        }
    }
}
