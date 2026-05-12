package com.autoscroll.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.autoscroll.app.MainActivity
import com.autoscroll.app.R
import com.autoscroll.app.applicationScope
import com.autoscroll.app.core.AutoScrollEngine
import com.autoscroll.app.overlay.OverlayController
import com.autoscroll.app.settingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that:
 *   1. Posts an ongoing notification (Android 8+ requires this within 5s of
 *      [Context.startForegroundService]).
 *   2. Updates that notification with live status from [AutoScrollEngine].
 *   3. Hosts the floating overlay window (via [OverlayController]).
 *
 * Does NOT perform swipes — that is the job of [AutoScrollAccessibilityService]
 * + [AutoScrollEngine]. This service is purely the user-visible chrome.
 */
class AutoScrollForegroundService : LifecycleService() {

    private var overlay: OverlayController? = null

    /** Cached notification builder so updates don't recreate from scratch. */
    private lateinit var builder: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate — entering foreground")

        notificationManager = getSystemService(NotificationManager::class.java)
            ?: error("NotificationManager unavailable")
        ensureNotificationChannel()
        builder = baseBuilder()
        startForegroundCompat(builder.build())

        AutoScrollEngine.refreshFromService()

        overlay = OverlayController(
            context = this,
            onTogglePause = ::toggleMasterSwitch,
            onCloseRequested = ::stopSelfRequested,
        ).also { it.show() }

        // Live-update the notification with engine state. Each emission rebuilds
        // only the changing fields (text). Cheap and avoids stale info.
        lifecycleScope.launch {
            AutoScrollEngine.state.collect { state ->
                val text = if (state.isActive && state.currentPlatform != null) {
                    getString(
                        R.string.notification_text_active,
                        state.currentPlatform!!.displayName,
                        state.secondsRemaining,
                    )
                } else {
                    getString(R.string.notification_text_standby)
                }
                builder.setContentText(text)
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelfRequested()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy — leaving foreground")
        overlay?.dismiss()
        overlay = null
        super.onDestroy()
    }

    // ---- Notification ----------------------------------------------------

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        val openAppPi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPi = PendingIntent.getService(
            this,
            1,
            Intent(this, AutoScrollForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text_standby))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppPi)
            .addAction(0, getString(R.string.action_stop), stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---- Action handlers (notification + overlay) -----------------------

    private fun stopSelfRequested() {
        Log.i(TAG, "stopSelfRequested — disabling master switch + stopping service")
        applicationContext.applicationScope.launch {
            applicationContext.settingsRepository.setEnabled(false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toggleMasterSwitch() {
        applicationContext.applicationScope.launch {
            val repo = applicationContext.settingsRepository
            val current = repo.settingsFlow.first()
            repo.setEnabled(!current.isEnabled)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelfRequested()
        super.onTaskRemoved(rootIntent)
    }

    private companion object {
        const val TAG = "AutoScrollFGS"
        const val CHANNEL_ID = "autoscroll_status"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.autoscroll.app.ACTION_STOP"
    }

    object Controller {
        fun start(context: Context) {
            val intent = Intent(context, AutoScrollForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AutoScrollForegroundService::class.java))
        }
    }
}
