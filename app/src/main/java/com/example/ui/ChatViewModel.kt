package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatDatabase
import com.example.data.repository.ChatRepository
import com.example.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = ChatDatabase.getDatabase(application)
    private val repository = ChatRepository(database.chatDao())

    // UI state for checking user profile existence
    val userProfile = repository.userProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // UI list of messages stream
    val messages = repository.messagesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startPolling()
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                _isRefreshing.value = true
                val result = repository.fetchMessages()
                _isOnline.value = result.isSuccess
                _isRefreshing.value = false
                
                // Poll every 3 seconds for real-time multiplayer feel
                delay(3000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    fun saveUsername(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.saveUserProfile(name.trim())
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val result = repository.sendMessage(text.trim())
            _isOnline.value = result.isSuccess
        }
    }

    fun retryMessage(msg: ChatMessage) {
        viewModelScope.launch {
            val result = repository.retryMessage(msg)
            _isOnline.value = result.isSuccess
        }
    }

    fun forceRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.fetchMessages()
            _isOnline.value = result.isSuccess
            _isRefreshing.value = false
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    // Factory
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
