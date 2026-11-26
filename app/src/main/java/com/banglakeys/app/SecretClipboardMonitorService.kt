package com.banglakeys.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * সিস্টেম ক্লিপবোর্ড ব্যাকগ্রাউন্ডে পর্যবেক্ষণ করার জন্য নিবেদিত Service।
 *
 * - কপি করা লেখায় এনক্রিপ্টেড Glyph প্যাটার্ন (`Runic`, `Dots`, `Shapes`) শনাক্ত হলে
 *   [SecretClipboardDetector] দিয়ে ব্যাকগ্রাউন্ড থ্রেডে ডিক্রিপ্ট করে।
 * - ম্যাচ পাওয়া মাত্র [SecretBannerStateHolder]-এ ইভেন্ট পাঠায়, যা কিবোর্ডের উপরের
 *   ডেডিকেটেড [SecretDecodeBannerView] বারটি ট্রিগার করে।
 * - নিরাপত্তার জন্য নোটিফিকেশনে কখনোই ডিক্রিপ্ট করা টেক্সট দেখানো হয় না।
 */
class SecretClipboardMonitorService : Service() {

    inner class LocalBinder : Binder() {
        fun service(): SecretClipboardMonitorService = this@SecretClipboardMonitorService
    }

    private val binder = LocalBinder()
    private var clipboardManager: ClipboardManager? = null
    private var detectorOverride: SecretClipboardDetector? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        readAndInspectClipboard(force = false)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        _running.value = true
        registerClipboardObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val explicitText = intent?.getStringExtra(EXTRA_CLIP_TEXT)
        val force = intent?.getBooleanExtra(EXTRA_FORCE_DECODE, false) ?: false
        if (!startAsForeground()) {
            if (!explicitText.isNullOrBlank()) {
                inspectText(explicitText, force = force)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        if (!explicitText.isNullOrBlank()) {
            inspectText(explicitText, force = force)
        } else {
            readAndInspectClipboard(force = force)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterClipboardObserver()
        if (instance === this) {
            instance = null
        }
        _running.value = false
        super.onDestroy()
    }

    internal fun setDetectorForTest(detector: SecretClipboardDetector?) {
        detectorOverride = detector
    }

    internal fun inspectText(rawText: String?, force: Boolean = false) {
        if (!SecretConfig.clipboardMonitorEnabled(this)) return
        val det = detectorOverride ?: SecretBannerStateHolder.detector(applicationContext)
        det.inspect(rawText, force = force) { result ->
            SecretBannerStateHolder.publish(result)
        }
    }

    private fun readAndInspectClipboard(force: Boolean) {
        try {
            val cm = clipboardManager
                ?: (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
                    clipboardManager = it
                }
                ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            inspectText(text, force = force)
        } catch (_: Exception) {
        }
    }

    private fun registerClipboardObserver() {
        if (clipboardManager != null) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboardManager = cm
        cm.addPrimaryClipChangedListener(clipListener)
    }

    private fun unregisterClipboardObserver() {
        val cm = clipboardManager ?: return
        try {
            cm.removePrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {
        }
        clipboardManager = null
    }

    private fun startAsForeground(): Boolean {
        return try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.secret_monitor_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        setShowBadge(false)
                    }
                )
            }

            val stopPi = PendingIntent.getService(
                this,
                11,
                Intent(this, SecretClipboardMonitorService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val openPi = PendingIntent.getActivity(
                this,
                12,
                Intent(this, SecretComposeActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val builder = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
            @Suppress("DEPRECATION")
            val notif = builder
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(getString(R.string.secret_monitor_notif_title))
                .setContentText(getString(R.string.secret_monitor_notif_desc))
                .setContentIntent(openPi)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.secret_monitor_stop_action),
                    stopPi,
                )
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notif)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val ACTION_START = "com.banglakeys.app.SECRET_CLIPBOARD_START"
        const val ACTION_STOP = "com.banglakeys.app.SECRET_CLIPBOARD_STOP"
        const val ACTION_INSPECT_CLIPBOARD = "com.banglakeys.app.SECRET_CLIPBOARD_INSPECT"
        const val EXTRA_CLIP_TEXT = "extra_clip_text"
        const val EXTRA_FORCE_DECODE = "extra_force_decode"

        private const val CHANNEL_ID = "secret_clipboard_monitor"
        private const val NOTIF_ID = 4202

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        @Volatile
        private var instance: SecretClipboardMonitorService? = null

        fun start(ctx: Context): Boolean {
            if (!SecretConfig.clipboardMonitorEnabled(ctx)) return false
            val intent = Intent(ctx, SecretClipboardMonitorService::class.java).setAction(ACTION_START)
            return try {
                if (Build.VERSION.SDK_INT >= 26) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, SecretClipboardMonitorService::class.java))
            } catch (_: Exception) {
            }
            _running.value = false
        }

        fun inspectNow(ctx: Context, rawText: String? = null, force: Boolean = false) {
            val active = instance
            if (active != null && !rawText.isNullOrBlank()) {
                active.inspectText(rawText, force = force)
                return
            }
            if (active != null && rawText == null) {
                active.readAndInspectClipboard(force = force)
                return
            }
            if (!SecretConfig.clipboardMonitorEnabled(ctx)) return
            val intent = Intent(ctx, SecretClipboardMonitorService::class.java)
                .setAction(ACTION_INSPECT_CLIPBOARD)
                .putExtra(EXTRA_FORCE_DECODE, force)
                .apply {
                    if (!rawText.isNullOrBlank()) putExtra(EXTRA_CLIP_TEXT, rawText)
                }
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            } catch (_: Exception) {
                if (!rawText.isNullOrBlank()) {
                    SecretBannerStateHolder.detector(ctx).inspect(rawText, force = force) { result ->
                        SecretBannerStateHolder.publish(result)
                    }
                }
            }
        }
    }
}
