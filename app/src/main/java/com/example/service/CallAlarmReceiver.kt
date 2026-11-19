package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class CallAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val senderName = intent.getStringExtra("SENDER_NAME") ?: "Bliska osoba"
        val reminderText = intent.getStringExtra("REMINDER_TEXT") ?: "Przypomnienie z listy"
        val listCode = intent.getStringExtra("LIST_CODE") ?: ""
        val itemId = intent.getStringExtra("ITEM_ID") ?: ""

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WspolnaLista:CallAlarmWakeLock"
        )
        try {
            wakeLock.acquire(10_000L)
        } catch (_: Exception) {
        }

        try {
            AlertDispatcher.launchIncomingCall(
                context = context,
                senderName = senderName,
                reminderText = reminderText,
                listCode = listCode,
                itemId = itemId
            )
        } finally {
            try {
                if (wakeLock.isHeld) wakeLock.release()
            } catch (_: Exception) {
            }
        }
    }
}
