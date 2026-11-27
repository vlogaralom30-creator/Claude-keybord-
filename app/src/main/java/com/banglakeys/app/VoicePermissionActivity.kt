package com.banglakeys.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

/**
 * Voice typing-এর জন্য RECORD_AUDIO runtime permission লাগে। IME নিজে একটা Service
 * হওয়ায় সরাসরি permission চাইতে পারে না, তাই কিবোর্ডের 🎤 বাটনে প্রথমবার চাপলে এই
 * অদৃশ্য (transparent) activity খুলে permission dialog দেখায়, উত্তর পেলেই নিজে বন্ধ
 * হয়ে যায় — আলাদা কোনো screen দেখা যায় না।
 */
class VoicePermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        getSharedPreferences("bk", MODE_PRIVATE).edit().putBoolean("voice_perm_asked", true).apply()
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        val permanentlyDenied = !granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        getSharedPreferences("bk", MODE_PRIVATE).edit()
            .putBoolean("voice_perm_asked", !granted)
            .putBoolean("voice_perm_blocked", permanentlyDenied)
            .apply()
        Toast.makeText(
            this,
            when {
                granted -> "Permission দেওয়া হয়েছে, এখন 🎤 বাটনে চাপুন"
                permanentlyDenied -> "ভয়েস টাইপিং চালাতে App Settings → Permissions → Microphone চালু করুন"
                else -> "Permission ছাড়া voice typing কাজ করবে না"
            },
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    companion object {
        private const val REQUEST_CODE = 101
    }
}
