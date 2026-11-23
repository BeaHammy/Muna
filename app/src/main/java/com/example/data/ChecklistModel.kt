package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val listCode: String = "",
    val role: String = "opiekun", // "opiekun" or "senior"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserListMembership(
    val code: String = "",
    val customName: String = "",
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ListDocument(
    val code: String = "",
    val name: String = "",
    val ownerId: String = "",
    val members: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class InviteDocument(
    val id: String = "",
    val listId: String = "",
    val code: String = "",
    val createdBy: String = "",
    @ServerTimestamp val expiresAt: Timestamp? = null,
    val used: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ListMemberInfo(
    val uid: String = "",
    val name: String = "",
    val role: String = "opiekun"
)

@Keep
@IgnoreExtraProperties
data class ChecklistItem(
    val id: String = "",
    val listCode: String = "",
    val text: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val deliveryMode: Long = 0L, // 0: tylko dodaj, 1: powiadomienie, 2: zadzwoń z przypomnieniem
    val scheduledTime: Long = 0L, // timestamp ms or 0
    val status: String = "pending", // "pending", "notified", "scheduled", "ringing", "answered", "rejected", "completed", "pinned", "cancelled"
    val reminderAcknowledgedTime: String = "",
    val alertByUid: String = "",
    val alertByName: String = "",
    val dueDate: String = "", // Format YYYY-MM-DD
    val isDaily: Boolean = false, // Repeat daily
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
