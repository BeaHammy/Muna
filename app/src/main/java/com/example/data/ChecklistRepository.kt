package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class ChecklistRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return null
        return try {
            val doc = db.collection("users").document(uid).get().await()
            if (!doc.exists()) {
                val currentUser = auth.currentUser
                val defaultName = currentUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: currentUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "Użytkownik"
                val defaultCode = "455344"
                val defaultProfile = UserProfile(
                    uid = uid,
                    name = defaultName,
                    listCode = defaultCode,
                    role = "opiekun"
                )
                saveProfileInternal(defaultProfile)
                return defaultProfile
            }
            val profile = doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
            if (profile != null && profile.listCode.isBlank()) {
                val fixed = profile.copy(listCode = "455344")
                saveProfileInternal(fixed)
                fixed
            } else {
                profile
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error getting user profile for users/$uid", e)
            UserProfile(
                uid = uid,
                name = auth.currentUser?.displayName ?: "Użytkownik",
                listCode = "455344",
                role = "opiekun"
            )
        }
    }

    private suspend fun saveProfileInternal(profile: UserProfile) {
        try {
            val docRef = db.collection("users").document(profile.uid)
            docRef.set(
                mapOf(
                    "name" to profile.name,
                    "listCode" to profile.listCode,
                    "role" to profile.role,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            db.collection("lists").document(profile.listCode).set(
                mapOf(
                    "code" to profile.listCode,
                    "name" to "Wspólna Lista ${profile.listCode}",
                    "ownerUid" to profile.uid,
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            saveListMembership(profile.listCode, "Wspólna Lista ${profile.listCode}")
        } catch (e: Exception) {
            Log.w("ChecklistRepo", "Failed to save internal default profile", e)
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            val uid = requireUserId()
            val cleanName = profile.name.trim().take(60)
            val cleanCode = profile.listCode.trim()
            val cleanRole = if (profile.role in listOf("opiekun", "senior")) profile.role else "opiekun"
            require(cleanName.isNotBlank()) { "Name must not be blank" }
            require(cleanCode.matches(Regex("^[0-9]{6}$"))) { "List code must be 6 digits" }

            val docRef = db.collection("users").document(uid)
            val existingDoc = docRef.get().await()
            if (existingDoc.exists()) {
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "listCode" to cleanCode,
                        "role" to cleanRole,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "name" to cleanName,
                        "listCode" to cleanCode,
                        "role" to cleanRole,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error saving user profile", e)
            Result.failure(e)
        }
    }

    suspend fun createList(listCode: String, listName: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val cleanCode = listCode.trim()
            val cleanName = listName.trim().take(100).ifBlank { "Wspólna Lista $cleanCode" }
            require(cleanCode.matches(Regex("^[0-9]{6}$"))) { "List code must be 6 digits" }

            val listData = mapOf(
                "code" to cleanCode,
                "name" to cleanName,
                "ownerUid" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("lists").document(cleanCode).set(listData).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error creating list $listCode", e)
            Result.failure(e)
        }
    }

    suspend fun getSharedListName(listCode: String): String? {
        val cleanCode = listCode.trim()
        if (!cleanCode.matches(Regex("^[0-9]{6}$"))) return null
        return try {
            requireUserId()
            val doc = db.collection("lists").document(cleanCode).get().await()
            if (!doc.exists()) null else doc.getString("name")?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error reading list $cleanCode", e)
            null
        }
    }

    suspend fun checkListExists(listCode: String): Boolean {
        val cleanCode = listCode.trim()
        if (!cleanCode.matches(Regex("^[0-9]{6}$"))) return false
        return try {
            requireUserId()
            val doc = db.collection("lists").document(cleanCode).get().await()
            doc.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error checking list $cleanCode", e)
            false
        }
    }

    suspend fun saveListMembership(listCode: String, customName: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val cleanCode = listCode.trim()
            require(cleanCode.matches(Regex("^[0-9]{6}$"))) { "List code must be 6 digits" }
            val cleanName = customName.trim().take(80).ifBlank { "Lista $cleanCode" }

            val docRef = db.collection("users").document(uid)
                .collection("memberships").document(cleanCode)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "customName" to cleanName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "code" to cleanCode,
                        "customName" to cleanName,
                        "joinedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error saving list membership $listCode", e)
            Result.failure(e)
        }
    }

    suspend fun ensureMembership(listCode: String) {
        val uid = auth.currentUser?.uid ?: return
        val cleanCode = listCode.trim()
        if (!cleanCode.matches(Regex("^[0-9]{6}$"))) return
        try {
            val docRef = db.collection("users").document(uid)
                .collection("memberships").document(cleanCode)
            if (!docRef.get().await().exists()) {
                val listName = getSharedListName(cleanCode) ?: "Lista $cleanCode"
                saveListMembership(cleanCode, listName)
            }
        } catch (e: Exception) {
            Log.w("ChecklistRepo", "Could not ensure membership for $cleanCode", e)
        }
    }

    fun observeUserMemberships(): Flow<List<UserListMembership>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("users").document(uid).collection("memberships")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(
                                UserListMembership::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )?.copy(code = doc.id)
                        } catch (e: Exception) {
                            Log.e("ChecklistRepo", "Error deserializing membership ${doc.id}", e)
                            null
                        }
                    }.sortedBy { it.joinedAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.e("ChecklistRepo", "Error observing memberships", firestoreError ?: error)
                    throw error
                }
        )
    }

    suspend fun createNewNamedListAndSwitch(customName: String): Result<String> {
        return try {
            requireUserId()
            val code = String.format("%06d", Random.nextInt(100_000, 1_000_000))
            val cleanName = customName.trim().take(80).ifBlank { "Wspólna Lista $code" }
            createList(code, cleanName).getOrThrow()
            saveListMembership(code, cleanName).getOrThrow()
            setActiveList(code).getOrThrow()
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error creating new named list", e)
            Result.failure(e)
        }
    }

    suspend fun generateInviteCode(listCode: String): Result<String> {
        return try {
            requireUserId()
            val cleanCode = listCode.trim()
            require(cleanCode.matches(Regex("^[0-9]{6}$"))) { "Invalid list code" }

            val inviteCode = String.format("%06d", Random.nextInt(100_000, 1_000_000))
            val expiresAt = System.currentTimeMillis() + 15 * 60 * 1000L // 15 minutes

            db.collection("lists").document(cleanCode).update(
                mapOf(
                    "inviteCode" to inviteCode,
                    "inviteExpiresAt" to expiresAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(inviteCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error generating invite code", e)
            Result.failure(e)
        }
    }

    suspend fun joinNamedListAndSwitch(codeOrInvite: String, customName: String): Result<String> {
        return try {
            requireUserId()
            val cleanInput = codeOrInvite.trim()
            require(cleanInput.isNotBlank()) { "Kod nie może być pusty." }

            var targetListCode: String? = null
            var sharedName: String? = null

            if (cleanInput.matches(Regex("^[0-9]{6}$"))) {
                targetListCode = cleanInput
                sharedName = getSharedListName(targetListCode)
            } else {
                val querySnapshot = db.collection("lists")
                    .whereEqualTo("inviteCode", cleanInput)
                    .get()
                    .await()

                if (!querySnapshot.isEmpty) {
                    val doc = querySnapshot.documents[0]
                    val expiresAt = doc.getLong("inviteExpiresAt") ?: 0L
                    if (System.currentTimeMillis() <= expiresAt) {
                        targetListCode = doc.id
                        sharedName = doc.getString("name")
                    } else {
                        return Result.failure(IllegalArgumentException("Kod zaproszenia wygasł (ważny 15 minut)."))
                    }
                }
            }

            if (targetListCode == null || sharedName == null) {
                return Result.failure(IllegalArgumentException("Nie znaleziono listy lub kod jest nieprawidłowy/wygasł."))
            }

            val finalName = customName.trim().take(80).ifBlank { sharedName }
            saveListMembership(targetListCode, finalName).getOrThrow()
            setActiveList(targetListCode).getOrThrow()
            Result.success(targetListCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error joining list with input $codeOrInvite", e)
            Result.failure(e)
        }
    }

    suspend fun renameListMembership(listCode: String, newName: String): Result<Unit> {
        val cleanName = newName.trim().take(80)
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Nazwa nie może być pusta."))
        return saveListMembership(listCode, cleanName)
    }

    suspend fun setActiveList(listCode: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val cleanCode = listCode.trim()
            require(cleanCode.matches(Regex("^[0-9]{6}$"))) { "Invalid list code" }
            db.collection("users").document(uid).update(
                mapOf(
                    "listCode" to cleanCode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error switching active list to $listCode", e)
            Result.failure(e)
        }
    }

    suspend fun leaveList(listCode: String, nextActiveCode: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val cleanCode = listCode.trim()
            val cleanNext = nextActiveCode.trim()
            require(cleanCode.matches(Regex("^[0-9]{6}$"))) { "Invalid list code" }
            require(cleanNext.matches(Regex("^[0-9]{6}$"))) { "Invalid next active list code" }
            db.collection("users").document(uid)
                .collection("memberships").document(cleanCode)
                .delete()
                .await()
            setActiveList(cleanNext).getOrThrow()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error leaving list $listCode", e)
            Result.failure(e)
        }
    }

    fun observeItems(listCode: String): Flow<List<ChecklistItem>> = flow {
        val cleanCode = listCode.trim()
        if (!cleanCode.matches(Regex("^[0-9]{6}$")) || auth.currentUser?.uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        val uid = requireUserId()
        emitAll(
            db.collection("lists").document(cleanCode).collection("items")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(
                                ChecklistItem::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.e("ChecklistRepo", "Error deserializing item ${doc.id}", e)
                            null
                        }
                    }.sortedByDescending { it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        Log.e("ChecklistRepo", "Error observing items for list $cleanCode", firestoreError ?: error)
                    }
                    throw error
                }
        )
    }

    suspend fun addItem(item: ChecklistItem): Result<String> {
        return try {
            val uid = requireUserId()
            val cleanCode = item.listCode.trim()
            val cleanText = item.text.trim().take(500)
            val cleanSenderName = item.senderName.trim().take(60).ifBlank { "Bliska osoba" }
            val cleanDueDate = item.dueDate.trim().take(10).ifBlank {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale("pl", "PL")).format(java.util.Date())
            }
            require(cleanCode.matches(Regex("^[0-9]{6}$"))) { "List code must be 6 digits" }
            require(cleanText.isNotBlank()) { "Item text must not be blank" }

            val docRef = db.collection("lists").document(cleanCode).collection("items").document()
            val payload = mapOf(
                "listCode" to cleanCode,
                "text" to cleanText,
                "senderUid" to uid,
                "senderName" to cleanSenderName,
                "deliveryMode" to item.deliveryMode.coerceIn(0L, 2L),
                "scheduledTime" to item.scheduledTime.coerceAtLeast(0L),
                "status" to item.status,
                "reminderAcknowledgedTime" to item.reminderAcknowledgedTime.take(20),
                "dueDate" to cleanDueDate,
                "isDaily" to item.isDaily,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error adding item", e)
            Result.failure(e)
        }
    }

    suspend fun triggerItemAlert(
        listCode: String,
        itemId: String,
        deliveryMode: Long,
        status: String,
        scheduledTime: Long,
        alertByName: String,
        ackTime: String = ""
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(listCode.matches(Regex("^[0-9]{6}$"))) { "Invalid list code" }
            require(itemId.isNotBlank()) { "Item ID must not be blank" }

            val docRef = db.collection("lists").document(listCode).collection("items").document(itemId)
            val updates = mapOf(
                "deliveryMode" to deliveryMode.coerceIn(0L, 2L),
                "scheduledTime" to scheduledTime.coerceAtLeast(0L),
                "status" to status,
                "reminderAcknowledgedTime" to ackTime.take(20),
                "alertByUid" to uid,
                "alertByName" to alertByName.trim().take(60).ifBlank { "Bliska osoba" },
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error triggering alert on item $itemId", e)
            Result.failure(e)
        }
    }

    suspend fun updateItemStatus(listCode: String, itemId: String, status: String, ackTime: String = ""): Result<Unit> {
        return try {
            requireUserId()
            require(listCode.matches(Regex("^[0-9]{6}$"))) { "Invalid list code" }
            require(itemId.isNotBlank()) { "Item ID must not be blank" }

            val docRef = db.collection("lists").document(listCode).collection("items").document(itemId)
            val updates = mutableMapOf<String, Any>(
                "status" to status,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (ackTime.isNotBlank()) {
                updates["reminderAcknowledgedTime"] = ackTime.take(20)
            } else if (status == "completed") {
                val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale("pl", "PL")).format(java.util.Date())
                updates["reminderAcknowledgedTime"] = timeStr
            } else if (status == "pending") {
                updates["reminderAcknowledgedTime"] = ""
            }
            docRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error updating item status", e)
            Result.failure(e)
        }
    }

    suspend fun deleteItem(listCode: String, itemId: String): Result<Unit> {
        return try {
            requireUserId()
            require(listCode.matches(Regex("^[0-9]{6}$"))) { "Invalid list code" }
            require(itemId.isNotBlank()) { "Item ID must not be blank" }

            db.collection("lists").document(listCode).collection("items").document(itemId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ChecklistRepo", "Error deleting item", e)
            Result.failure(e)
        }
    }
}
