package com.sailracing.app.race

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sailracing.app.MainActivity
import com.sailracing.app.R
import com.sailracing.app.di.appGraph
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.text.Formatters
import com.sailracing.domain.timer.RacePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the race session alive (GPS, countdown, beeps) while the app is in the
 * background or the screen is off. The notification mirrors the countdown and offers a stop action.
 */
class RaceService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            appGraph.raceSession.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val session = appGraph.raceSession
        startInForeground(buildNotification(NotificationText.from(session.snapshot.value)))

        serviceScope.launch {
            session.snapshot.map(NotificationText::from).distinctUntilChanged().collect { text ->
                notificationManager().notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
        serviceScope.launch {
            session.isRunning.collect { running -> if (!running) stopSelf() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: NotificationText): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, RaceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        channel.description = getString(R.string.notification_channel_description)
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** What the notification says; a value type so updates only happen when the visible text changes. */
    data class NotificationText(val title: String, val body: String) {
        companion object {
            fun from(snapshot: RaceSnapshot): NotificationText = when (snapshot.phase) {
                RacePhase.SETUP -> NotificationText("Sail Racing", "GPS on - preparing the race")
                RacePhase.COUNTDOWN -> NotificationText(
                    "Start in ${Formatters.countdown(snapshot.remainingMillis ?: 0)}",
                    snapshot.line?.let { "Distance to line ${Formatters.meters(it.distanceMeters)}" } ?: "Line not set",
                )
                RacePhase.RACING -> NotificationText(
                    "Racing ${Formatters.countdown(snapshot.remainingMillis ?: 0)}",
                    snapshot.speedMps?.let { "Speed ${Formatters.knots(it)}" } ?: "Waiting for GPS",
                )
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "race_session"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.sailracing.app.action.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RaceService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RaceService::class.java).setAction(ACTION_STOP))
        }
    }
}
