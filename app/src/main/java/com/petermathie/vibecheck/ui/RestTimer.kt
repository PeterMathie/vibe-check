package com.petermathie.vibecheck.ui

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import com.petermathie.vibecheck.MainActivity

object RestTimer {
    private fun intent(context: Context) = PendingIntent.getBroadcast(context, 1, Intent(context, RestTimerReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun start(context: Context, seconds: Int) {
        val end = System.currentTimeMillis() + seconds.coerceAtLeast(1) * 1000L
        context.getSharedPreferences("settings",0).edit().putLong("restEnd",end).apply()
        val alarm = context.getSystemService(AlarmManager::class.java)
        if(Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,end,intent(context))
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, intent(context))
    }
    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
        context.getSharedPreferences("settings",0).edit().remove("restEnd").apply()
    }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("rest", "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
        val open = PendingIntent.getActivity(context,2,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context,"rest").setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Rest complete").setContentText("Ready for your next set").setContentIntent(open).setAutoCancel(true).setDefaults(Notification.DEFAULT_ALL).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        try { manager.notify(1,notification) } catch (_: SecurityException) { }
        context.getSharedPreferences("settings",0).edit().remove("restEnd").apply()
    }
}
