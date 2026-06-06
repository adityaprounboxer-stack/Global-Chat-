package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val username: String
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isLocal: Boolean = false,
    val status: String = "SENT" // "SENDING", "SENT", "FAILED"
)
