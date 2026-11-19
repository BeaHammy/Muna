package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.ChecklistItem
import com.example.data.ChecklistRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ChecklistSyncService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ChecklistRepository
    private var membershipObserverJob: Job? = null
    private val listListenerJobs = mutableMapOf<String, Job>()
    private val scheduledDelayJobs = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        repository = ChecklistRepository(applicationContext)
        val notification = NotificationHelper.buildSyncServiceNotification(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uid = Firebase.auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startObservingUserLists(uid)
        return START_STICKY
    }

    private fun startObservingUserLists(uid: String) {
        if (membershipObserverJob?.isActive == true) return
        membershipObserverJob = serviceScope.launch {
            // Ensure active list from profile is included right away
            val profile = repository.getUserProfile(uid)
            val initialActiveCode = profile?.listCode?.trim().orEmpty()
            if (initialActiveCode.matches(Regex("^[0-9]{6}$"))) {
                syncObservedLists(uid, setOf(initialActiveCode))
            }

            repository.observeUserMemberships()
                .catch { e ->
                    Log.w("ChecklistSyncService", "Membership observation error", e)
                }
                .collect { memberships ->
                    val currentProfile = repository.getUserProfile(uid)
                    val codes = memberships.map { it.code }.toMutableSet()
                    val activeCode = currentProfile?.listCode?.trim().orEmpty()
                    if (activeCode.matches(Regex("^[0-9]{6}$"))) {
                        codes.add(activeCode)
                    }
                    syncObservedLists(uid, codes)
                }
        }
    }

    private fun syncObservedLists(uid: String, targetCodes: Set<String>) {
        val toRemove = listListenerJobs.keys.filter { it !in targetCodes }
        toRemove.forEach { code ->
            listListenerJobs.remove(code)?.cancel()
        }

        targetCodes.forEach { code ->
            if (listListenerJobs[code]?.isActive == true) return@forEach
            listListenerJobs[code] = serviceScope.launch {
                repository.observeItems(code)
                    .catch { e ->
                        Log.w("ChecklistSyncService", "Error observing list $code in background", e)
                    }
                    .collect { items ->
                        val activeUid = Firebase.auth.currentUser?.uid ?: return@collect
                        if (activeUid != uid) {
                            stopSelf()
                            return@collect
                        }
                        AlertDispatcher.processItems(
                            context = applicationContext,
                            currentUid = activeUid,
                            listCode = code,
                            items = items
                        )
                        scheduleInProcessBackupTimers(activeUid, code, items)
                    }
            }
        }
    }

    private fun scheduleInProcessBackupTimers(
        currentUid: String,
        listCode: String,
        items: List<ChecklistItem>
    ) {
        val now = System.currentTimeMillis()
        val activeScheduledIds = mutableSetOf<String>()

        items.forEach { item ->
            val alertSourceUid = item.alertByUid.ifBlank { item.senderUid }
            val alertSourceName = item.alertByName.ifBlank {
                item.senderName.ifBlank { "Bliska osoba" }
            }
            if (
                alertSourceUid != currentUid &&
                item.deliveryMode == 2L &&
                item.status == "scheduled" &&
                item.scheduledTime > now
            ) {
                val jobKey = "${item.id}_${item.scheduledTime}"
                activeScheduledIds.add(jobKey)
                if (scheduledDelayJobs[jobKey]?.isActive != true) {
                    val waitMs = item.scheduledTime - now
                    scheduledDelayJobs[jobKey] = serviceScope.launch {
                        delay(waitMs)
                        AlertDispatcher.launchIncomingCall(
                            context = applicationContext,
                            senderName = alertSourceName,
                            reminderText = item.text,
                            listCode = listCode,
                            itemId = item.id
                        )
                    }
                }
            }
        }

        val staleKeys = scheduledDelayJobs.keys.filter { it !in activeScheduledIds }
        staleKeys.forEach { key ->
            scheduledDelayJobs.remove(key)?.cancel()
        }
    }

    override fun onDestroy() {
        listListenerJobs.values.forEach { it.cancel() }
        scheduledDelayJobs.values.forEach { it.cancel() }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 4101

        fun start(context: Context) {
            try {
                val intent = Intent(context.applicationContext, ChecklistSyncService::class.java)
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: Exception) {
                Log.w("ChecklistSyncService", "Could not start sync foreground service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, ChecklistSyncService::class.java)
                )
            } catch (_: Exception) {
            }
        }
    }
}
