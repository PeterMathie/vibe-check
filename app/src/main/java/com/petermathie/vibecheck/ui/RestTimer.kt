package com.petermathie.vibecheck.ui

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.petermathie.vibecheck.MainActivity

sealed interface RestTimerStartResult {
    data object Started : RestTimerStartResult
    data class StartedWithWarning(val message: String) : RestTimerStartResult
}

object RestTimer {
    const val CHANNEL_ID = "rest_timer_v2"
    private const val SETTINGS = "settings"
    private const val REST_END = "restEnd"
    const val REST_COMPLETED_AT = "restCompletedAt"
    const val REST_NOTIFICATION_ERROR = "restNotificationError"

    private fun intent(context: Context, end: Long = 0L) = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, RestTimerReceiver::class.java).putExtra(REST_END, end),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val sound = Settings.System.DEFAULT_NOTIFICATION_URI
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Visible and audible alerts when a workout rest period ends"
                enableVibration(false)
                setSound(sound, audio)
            },
        )
    }

    fun notificationsAvailable(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled()) &&
            (Build.VERSION.SDK_INT < 26 ||
                manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE) &&
            (Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }

    fun start(context: Context, seconds: Int): RestTimerStartResult {
        ensureNotificationChannel(context)
        val end = System.currentTimeMillis() + seconds.coerceAtLeast(1) * 1000L
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit()
            .putLong(REST_END, end)
            .remove(REST_COMPLETED_AT)
            .remove(REST_NOTIFICATION_ERROR)
            .apply()
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = intent(context, end)
        val precise = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        if (precise) {
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pendingIntent)
            } catch (_: SecurityException) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pendingIntent)
            }
        } else {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pendingIntent)
        }
        val warnings = buildList {
            if (!notificationsAvailable(context)) {
                add("Background notification is unavailable; keep Vibe Check open for the visible timer alert or enable notifications in Settings.")
            }
            if (!precise) {
                add("Android may delay this timer in the background until precise alarms are enabled.")
            }
        }
        return if (warnings.isEmpty()) RestTimerStartResult.Started
        else RestTimerStartResult.StartedWithWarning(warnings.joinToString(" "))
    }

    fun complete(context: Context, expectedEnd: Long): Boolean {
        val preferences = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        if (preferences.getLong(REST_END, 0L) != expectedEnd) return false
        context.getSystemService(AlarmManager::class.java).cancel(intent(context, expectedEnd))
        preferences.edit()
            .remove(REST_END)
            .putLong(REST_COMPLETED_AT, System.currentTimeMillis())
            .apply()
        postNotification(context)
        return true
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit()
            .remove(REST_END)
            .remove(REST_COMPLETED_AT)
            .apply()
    }

    private fun postNotification(context: Context) {
        if (!notificationsAvailable(context)) {
            context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit()
                .putString(REST_NOTIFICATION_ERROR, "Rest completed, but Android notifications are disabled.")
                .apply()
            return
        }
        ensureNotificationChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Rest complete")
            .setContentText("Ready for your next set")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(Uri.parse(Settings.System.DEFAULT_NOTIFICATION_URI.toString()))
            .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_LIGHTS)
            .build()
        try {
            manager.notify(1, notification)
            vibrateIfEnabled(context)
        } catch (error: SecurityException) {
            context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit()
                .putString(REST_NOTIFICATION_ERROR, "Rest completed, but the notification permission was revoked.")
                .apply()
        }
    }

    private fun vibrateIfEnabled(context: Context) {
        if (!context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getBoolean("haptic", true)) return
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 200, 100, 200), -1)
        }
    }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestTimer.complete(context, intent.getLongExtra("restEnd", 0L))
    }
}
