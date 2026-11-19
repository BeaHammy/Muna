package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.IncomingCallActivity
import com.example.MainActivity
import kotlin.math.abs

object NotificationHelper {
    private const val REMINDER_CHANNEL_ID = "wspolna_lista_reminders_v2"
    private const val REMINDER_CHANNEL_NAME = "Przypomnienia z listy"

    private const val CALL_CHANNEL_ID = "wspolna_lista_calls_v2"
    private const val CALL_CHANNEL_NAME = "Połączenia przypominające"

    private const val SYNC_CHANNEL_ID = "wspolna_lista_sync"
    private const val SYNC_CHANNEL_NAME = "Synchronizacja w tle"

    private fun reminderNotifId(itemId: String): Int =
        10_000 + abs(itemId.hashCode() % 100_000)

    private fun callNotifId(itemId: String): Int =
        200_000 + abs(itemId.hashCode() % 100_000)

    fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            REMINDER_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Powiadomienia o zadaniach od bliskiej osoby"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 200, 350)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(reminderChannel)

        val callChannel = NotificationChannel(
            CALL_CHANNEL_ID,
            CALL_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Pilne alarmowe połączenia głosowe z przypomnieniem"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(callChannel)

        val syncChannel = NotificationChannel(
            SYNC_CHANNEL_ID,
            SYNC_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Utrzymuje połączenie w tle, aby odbierać telefony i przypomnienia"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(syncChannel)
    }

    fun buildSyncServiceNotification(context: Context): Notification {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            9001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SYNC_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Wspólna Lista jest aktywna")
            .setContentText("Nasłuchuję powiadomień i przypomnień od bliskiej osoby.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showReminderNotification(
        context: Context,
        itemId: String,
        senderName: String,
        text: String,
        listCode: String = ""
    ): Boolean {
        if (!hasPostNotificationPermission(context)) return false
        ensureChannels(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (listCode.isNotBlank()) {
                putExtra("LIST_CODE", listCode)
            }
        }
        val notifId = reminderNotifId(itemId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Przypomnienie od: $senderName")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun showIncomingCallNotification(
        context: Context,
        itemId: String,
        senderName: String,
        text: String,
        listCode: String
    ) {
        if (!hasPostNotificationPermission(context)) return
        ensureChannels(context)

        val callIntent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra("SENDER_NAME", senderName)
            putExtra("REMINDER_TEXT", text)
            putExtra("LIST_CODE", listCode)
            putExtra("ITEM_ID", itemId)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val notifId = callNotifId(itemId)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Dzwoni: $senderName")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                android.R.drawable.sym_call_incoming,
                "ODBIERZ",
                fullScreenPendingIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelIncomingCallNotification(context: Context, itemId: String) {
        if (itemId.isBlank()) return
        try {
            NotificationManagerCompat.from(context).cancel(callNotifId(itemId))
        } catch (_: Exception) {
        }
    }
}
