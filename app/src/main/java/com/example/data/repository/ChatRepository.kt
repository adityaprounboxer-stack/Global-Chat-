package com.example.data.repository

import android.util.Log
import com.example.data.local.ChatDao
import com.example.data.local.ChatDatabase
import com.example.data.remote.ChatApiService
import com.example.model.ChatMessage
import com.example.model.UserProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class ChatRepository(private val chatDao: ChatDao) {

    companion object {
        private const val TAG = "ChatRepository"
        private const val BUCKET_ID = "chat_ea58c0759830"
        private const val MAX_MESSAGES_LIMIT = 100
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    private val apiService: ChatApiService = Retrofit.Builder()
        .baseUrl("https://kvdb.io/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(ChatApiService::class.java)

    // Profiles Flow
    val userProfileFlow: Flow<UserProfile?> = chatDao.getUserProfileFlow()

    // Local message updates Flow
    val messagesFlow: Flow<List<ChatMessage>> = chatDao.getMessagesFlow()

    suspend fun saveUserProfile(username: String) {
        chatDao.insertUserProfile(UserProfile(id = 1, username = username))
    }

    suspend fun getUsername(): String {
        return chatDao.getUserProfile()?.username ?: ""
    }

    // Pull messages from remote, merge with local unsent, and save to local db
    suspend fun fetchMessages(): Result<Unit> {
        return try {
            val remoteMessages = try {
                apiService.getMessages(BUCKET_ID)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    emptyList()
                } else {
                    throw e
                }
            }

            // Clean statuses from remote: remote objects should not have SENDING/FAILED
            val sanitizedRemote = remoteMessages.map { 
                it.copy(isLocal = false, status = "SENT") 
            }

            // Get local unsent messages to preserve them
            val localMessages = chatDao.getAllMessages()
            val unsent = localMessages.filter { it.status == "SENDING" || it.status == "FAILED" }

            // Merge: Server is the source of truth, plus append unsent ones
            val mergedList = (sanitizedRemote + unsent)
                .distinctBy { it.id }
                .sortedBy { it.timestamp }

            // Clear sent, then insert newly fetched + local unsent
            chatDao.clearSentMessages()
            chatDao.insertMessages(mergedList)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching messages: ", e)
            Result.failure(e)
        }
    }

    // Post a message. Handles local instant-save, remote sync, and fail states
    suspend fun sendMessage(messageText: String): Result<Unit> {
        val currentUser = getUsername()
        if (currentUser.isEmpty()) {
            return Result.failure(Exception("Username is not set"))
        }

        val messageId = java.util.UUID.randomUUID().toString()
        val newMessage = ChatMessage(
            id = messageId,
            sender = currentUser,
            text = messageText,
            timestamp = System.currentTimeMillis(),
            isLocal = true,
            status = "SENDING"
        )

        // 1. Insert locally as SENDING to update UI instantaneously
        chatDao.insertMessage(newMessage)

        return try {
            // 2. Fetch latest remote messages
            val remoteMessages = try {
                apiService.getMessages(BUCKET_ID)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    emptyList()
                } else {
                    throw e
                }
            }

            // 3. Filter out any unsent/failed from remote representation
            val sanitizedRemote = remoteMessages.map { 
                it.copy(isLocal = false, status = "SENT") 
            }

            // 4. Append our new message with status = SENT
            val updatedMessage = newMessage.copy(status = "SENT")
            val newMergedList = (sanitizedRemote + updatedMessage)
                .distinctBy { it.id }
                .sortedBy { it.timestamp }
                .takeLast(MAX_MESSAGES_LIMIT) // Prune size to 100 items to fit KVdb limits

            // 5. Send list back to server
            apiService.updateMessages(BUCKET_ID, newMergedList)

            // 6. Refresh local database with newly confirmed list
            chatDao.clearSentMessages()
            chatDao.insertMessages(newMergedList)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message online: ", e)
            // 7. Update status to FAILED in local db
            val failedMessage = newMessage.copy(status = "FAILED")
            chatDao.insertMessage(failedMessage)
            Result.failure(e)
        }
    }

    // Retry sending a failed message
    suspend fun retryMessage(failedMsg: ChatMessage): Result<Unit> {
        // Mark as SENDING first
        val sendingMsg = failedMsg.copy(status = "SENDING")
        chatDao.insertMessage(sendingMsg)

        return try {
            val remoteMessages = try {
                apiService.getMessages(BUCKET_ID)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    emptyList()
                } else {
                    throw e
                }
            }

            val sanitizedRemote = remoteMessages.map { 
                it.copy(isLocal = false, status = "SENT") 
            }

            val confirmedMsg = sendingMsg.copy(status = "SENT")
            val newMergedList = (sanitizedRemote + confirmedMsg)
                .distinctBy { it.id }
                .sortedBy { it.timestamp }
                .takeLast(MAX_MESSAGES_LIMIT)

            apiService.updateMessages(BUCKET_ID, newMergedList)

            chatDao.clearSentMessages()
            chatDao.insertMessages(newMergedList)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Retry failed: ", e)
            val failedMessage = sendingMsg.copy(status = "FAILED")
            chatDao.insertMessage(failedMessage)
            Result.failure(e)
        }
    }

    suspend fun clearHistory() {
        try {
            apiService.updateMessages(BUCKET_ID, emptyList())
            chatDao.clearAllMessages()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear remote history: ", e)
            chatDao.clearAllMessages()
        }
    }
}
