package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.IncomingCallActivity
import com.example.MainActivity
import com.example.data.ChecklistItem
import java.util.Collections

object AlertDispatcher {
    private const val PREFS_NAME = "wspolna_lista_dispatched_alerts"
    private const val KEY_NOTIF_HISTORY = "notif_keys"
    private const val MAX_PERSISTED_KEYS = 80

    private val dispatchedNotifKeys: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf())
    private val dispatchedCallKeys: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf())
    private val scheduledAlarmKeys: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf())

    @Synchronized
    private fun markNotifDispatched(context: Context, key: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val persisted = prefs.getStringSet(KEY_NOTIF_HISTORY, emptySet()) ?: emptySet()
        if (dispatchedNotifKeys.contains(key) || persisted.contains(key)) {
            dispatchedNotifKeys.add(key)
            return false
        }
        dispatchedNotifKeys.add(key)
        val updated = (persisted.toList() + key).takeLast(MAX_PERSISTED_KEYS).toSet()
        prefs.edit().putStringSet(KEY_NOTIF_HISTORY, updated).apply()
        return true
    }

    @Synchronized
    private fun clearCallDispatched(itemId: String) {
        dispatchedCallKeys.removeIf { it.contains("ring_${itemId}_") || it.contains("sched_now_${itemId}_") }
    }

    @Synchronized
    private fun markCallDispatched(key: String): Boolean {
        return dispatchedCallKeys.add(key)
    }

    @Synchronized
    private fun markScheduledDispatched(key: String): Boolean {
        return scheduledAlarmKeys.add(key)
    }

    fun processItems(
        context: Context,
        currentUid: String,
        listCode: String,
        items: List<ChecklistItem>,
        onInAppNotification: ((item: ChecklistItem, senderName: String) -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()

        items.forEach { item ->
            if (item.id.isBlank()) return@forEach

            if (item.status != "scheduled") {
                cancelScheduledAlarm(context, item.id)
            }
            if (item.status != "ringing" && item.status != "scheduled") {
                clearCallDispatched(item.id)
            }

            val alertSourceUid = item.alertByUid.ifBlank { item.senderUid }
            val alertSourceName = item.alertByName.ifBlank {
                item.senderName.ifBlank { "Bliska osoba" }
            }
            val isFromPartner = alertSourceUid != currentUid
            if (!isFromPartner) return@forEach

            when {
                item.status == "notified" -> {
                    val key = "notif_${item.id}_${item.reminderAcknowledgedTime}"
                    if (markNotifDispatched(context, key)) {
                        NotificationHelper.showReminderNotification(
                            context = context,
                            itemId = item.id,
                            senderName = alertSourceName,
                            text = item.text,
                            listCode = listCode
                        )
                        onInAppNotification?.invoke(item, alertSourceName)
                    }
                }

                item.deliveryMode == 2L && item.status == "ringing" -> {
                    val updatedAtMs = item.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    val callKey = "ring_${item.id}_${item.status}_${item.alertByUid}_${updatedAtMs}"
                    if (markCallDispatched(callKey)) {
                        launchIncomingCall(
                            context = context,
                            senderName = alertSourceName,
                            reminderText = item.text,
                            listCode = listCode,
                            itemId = item.id
                        )
                    }
                }

                item.deliveryMode == 2L && item.status == "scheduled" && item.scheduledTime <= now -> {
                    val updatedAtMs = item.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                    val callKey = "sched_now_${item.id}_${item.status}_${item.alertByUid}_${updatedAtMs}"
                    if (markCallDispatched(callKey)) {
                        launchIncomingCall(
                            context = context,
                            senderName = alertSourceName,
                            reminderText = item.text,
                            listCode = listCode,
                            itemId = item.id
                        )
                    }
                }

                item.deliveryMode == 2L && item.status == "scheduled" && item.scheduledTime > now -> {
                    val schedKey = "sched_${item.id}_${item.scheduledTime}"
                    if (markScheduledDispatched(schedKey)) {
                        scheduleExactAlarm(
                            context = context,
                            reminderText = item.text,
                            senderName = alertSourceName,
                            listCode = listCode,
                            itemId = item.id,
                            triggerAtMillis = item.scheduledTime
                        )
                    }
                }
            }
        }
    }

    fun launchIncomingCall(
        context: Context,
        senderName: String,
        reminderText: String,
        listCode: String,
        itemId: String
    ) {
        NotificationHelper.showIncomingCallNotification(
            context = context,
            itemId = itemId,
            senderName = senderName,
            text = reminderText,
            listCode = listCode
        )

        val callIntent = Intent(context, IncomingCallActivity::class.java).apply {
            putExtra("SENDER_NAME", senderName)
            putExtra("REMINDER_TEXT", reminderText)
            putExtra("LIST_CODE", listCode)
            putExtra("ITEM_ID", itemId)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        try {
            context.startActivity(callIntent)
        } catch (e: Exception) {
            Log.w("AlertDispatcher", "Direct startActivity blocked from background; full-screen notification posted.", e)
        }
    }

    fun scheduleExactAlarm(
        context: Context,
        reminderText: String,
        senderName: String,
        listCode: String,
        itemId: String,
        triggerAtMillis: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CallAlarmReceiver::class.java).apply {
            putExtra("SENDER_NAME", senderName)
            putExtra("REMINDER_TEXT", reminderText)
            putExtra("LIST_CODE", listCode)
            putExtra("ITEM_ID", itemId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = PendingIntent.getActivity(
            context,
            itemId.hashCode() + 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            Log.e("AlertDispatcher", "Failed to schedule alarm for $itemId", e)
        }
    }

    fun cancelScheduledAlarm(context: Context, itemId: String) {
        if (itemId.isBlank()) return
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, CallAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                itemId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (_: Exception) {
        }
    }
}
