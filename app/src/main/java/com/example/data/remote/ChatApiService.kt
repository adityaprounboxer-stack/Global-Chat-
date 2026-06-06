package com.example.data.remote

import com.example.model.ChatMessage
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface ChatApiService {
    @GET("{bucket}/global_messages")
    suspend fun getMessages(
        @Path("bucket") bucket: String
    ): List<ChatMessage>

    @PUT("{bucket}/global_messages")
    suspend fun updateMessages(
        @Path("bucket") bucket: String,
        @Body messages: List<ChatMessage>
    ): List<ChatMessage>
}
