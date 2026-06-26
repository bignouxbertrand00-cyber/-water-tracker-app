package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.WaterDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WaterAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = WaterDatabase.getDatabase(context)
                val profile = db.waterDao().getProfileDirect() ?: return@launch

                if (!profile.remindersEnabled) {
                    return@launch
                }

                val dailyGoal = profile.dailyGoalMl
                val allLogs = try {
                    db.waterDao().getAllLogsFlow().first()
                } catch (e: Exception) {
                    emptyList()
                }
                
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val todayLogs = allLogs.filter { it.dateString == todayStr }
                val totalToday = todayLogs.sumOf { it.amountMl }

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channelId = "water_tracker_notifications"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "Water Tracker Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Periodic reminders to stay hydrated."
                        setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION), null)
                        enableVibration(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val activityIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val percentage = if (dailyGoal > 0) ((totalToday.toFloat() / dailyGoal) * 100).toInt() else 0
                val percentageStr = "$percentage%"

                val lang = profile.language.lowercase()

                val title = when (lang) {
                    "fr" -> "Il est temps de boire de l'eau ! 💧"
                    "es" -> "¡Hora de hidratarse! 💧"
                    "de" -> "Zeit, Wasser zu trinken! 💧"
                    else -> "Time to Hydrate! 💧"
                }

                val messages = when (lang) {
                    "fr" -> listOf(
                        "Votre objectif personnalisé est de $dailyGoal ml par jour. Vous avez bu $totalToday ml ($percentageStr) jusqu'à présent. Continuez ! 👍",
                        "Objectif d'hydratation : Vous devez boire $dailyGoal ml par jour ! Prenez un verre d'eau maintenant. 🌊",
                        "Vous avez besoin de $dailyGoal ml par jour pour maintenir vos cellules au top ! Vous en êtes à $totalToday ml aujourd'hui. 🥤",
                        "N'oubliez pas de vous hydrater ! Votre cible recommandée est de $dailyGoal ml par jour. Restez plein d'énergie ! ✨"
                    )
                    "es" -> listOf(
                        "Tu meta personalizada es de $dailyGoal ml al día. Has bebido $totalToday ml ($percentageStr) hasta ahora. ¡Sigue así! 👍",
                        "Meta de hidratación: ¡Necesitas beber $dailyGoal ml al día! Consigue un vaso de agua ahora mismo. 🌊",
                        "¡Necesitas $dailyGoal ml al día para un rendimiento celular óptimo! Llevas $totalToday ml hoy. 🥤",
                        "¡Recuerda hidratarte! Tu meta recomendada es de $dailyGoal ml al día. ¡Mantente con energía! ✨"
                    )
                    "de" -> listOf(
                        "Ihr persönliches Ziel ist $dailyGoal ml pro Tag. Sie haben bisher $totalToday ml ($percentageStr) getrunken. Weiter so! 👍",
                        "Hydrationsziel: Sie müssen $dailyGoal ml am Tag trinken! Holen Sie sich jetzt ein Glas Wasser. 🌊",
                        "Sie benötigen $dailyGoal ml täglich für optimale Zellleistung! Sie sind heute bei $totalToday ml. 🥤",
                        "Erinnerung ans Trinken! Ihr empfohlenes Ziel ist $dailyGoal ml am Tag. Bleiben Sie fit! ✨"
                    )
                    else -> listOf(
                        "Your customized target is $dailyGoal ml in a day. You have drunk $totalToday ml ($percentageStr) so far. Keep it up! 👍",
                        "Hydration target: You need to drink $dailyGoal ml in a day! Grab a glass of water now. 🌊",
                        "You need $dailyGoal ml in a day to maintain optimal cellular performance! You're at $totalToday ml today. 🥤",
                        "Remember to hydrate! Your recommended target is $dailyGoal ml in a day. Stay energized! ✨"
                    )
                }

                val bodyText = messages.random()

                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(bodyText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                notificationManager.notify(System.currentTimeMillis().toInt(), notification)

                // Schedule NEXT reminder with AlarmManager
                WaterAlarmScheduler.scheduleReminder(context, profile.reminderIntervalMinutes)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
