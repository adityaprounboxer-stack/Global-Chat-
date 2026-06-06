package com.example.data.local

import androidx.room.*
import com.example.model.ChatMessage
import com.example.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getMessagesFlow(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessages(): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    @Query("DELETE FROM chat_messages WHERE status = 'SENT'")
    suspend fun clearSentMessages()

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}
