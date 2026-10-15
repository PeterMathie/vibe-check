package com.petermathie.vibecheck

import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.ui.RestTimer
import com.petermathie.vibecheck.ui.RestTimerReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestTimerTest {
    @Test
    fun completionChannelIsVisibleAndAudible() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        RestTimer.ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(RestTimer.CHANNEL_ID)
            assertTrue(channel.importance >= NotificationManager.IMPORTANCE_HIGH)
            assertTrue(channel.sound != null)
        }
    }

    @Test
    fun receiverCompletesPersistedTimerAcrossProcessLifecycle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("settings", 0)
        val end = System.currentTimeMillis()
        preferences.edit().clear().putLong("restEnd", end).commit()

        RestTimerReceiver().onReceive(
            context,
            Intent(context, RestTimerReceiver::class.java).putExtra("restEnd", end),
        )

        assertEquals(0L, preferences.getLong("restEnd", 0L))
        assertTrue(preferences.getLong(RestTimer.REST_COMPLETED_AT, 0L) >= end)
    }
}
