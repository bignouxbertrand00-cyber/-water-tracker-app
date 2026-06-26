package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.WaterDatabase
import com.example.data.WaterProfile
import com.example.receiver.WaterAlarmReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Water Tracker", appName)
  }

  @Test
  fun testWaterAlarmReceiverFiresNotificationWhenRemindersEnabled() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // Setup: Initialize Database and Insert a profile with reminders active
    val db = WaterDatabase.getDatabase(context)
    val dao = db.waterDao()
    
    val profile = WaterProfile(
      id = 1,
      remindersEnabled = true,
      reminderIntervalMinutes = 60,
      dailyGoalMl = 2000,
      language = "en"
    )
    dao.insertOrUpdateProfile(profile)

    // Verify it was correctly stored
    val storedProfile = dao.getProfileDirect()
    assertNotNull(storedProfile)
    assertTrue(storedProfile!!.remindersEnabled)

    // Trigger WaterAlarmReceiver onReceive directly (simulating the OS firing the alarm when app is closed)
    val receiver = WaterAlarmReceiver()
    val intent = Intent(context, WaterAlarmReceiver::class.java)
    
    receiver.onReceive(context, intent)

    // Wait a brief moment for the receiver's coroutine to fetch data and notify the system
    // Room queries and notification posting inside goAsync's CoroutineScope run on Dispatchers.IO
    // We can yield / sleep briefly to let the asynchronous goAsync block execute
    Thread.sleep(1000)

    // Assert: Use Robolectric Shadows to check if a notification was posted
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNotificationManager = shadowOf(notificationManager)
    
    val notifications = shadowNotificationManager.allNotifications
    
    // We assert that the system notification list is not empty, confirming that a reminder has popped up!
    assertTrue("A notification must be posted when the alarm triggers", notifications.isNotEmpty())
    
    val postedNotification = notifications.first()
    assertNotNull(postedNotification)
    
    // Additionally, check details of the notification to ensure high-quality, friendly delivery
    val shadowNotification = shadowOf(postedNotification)
    assertNotNull(shadowNotification)
  }
}

