package com.myweibo.ui.screens.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myweibo.data.repository.WeiboRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MessageItem(
    val commentId: Long,
    val postId: Long,
    val postContent: String,
    val commentContent: String,
    val commentIdentityName: String,
    val commentIdentityColor: Int,
    val createdAt: Long
)

class MessageViewModel(private val repository: WeiboRepository) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()
    
    init {
        loadMessages()
    }
    
    private fun loadMessages() {
        viewModelScope.launch {
            repository.allPosts.collect { posts ->
                val messageItems = mutableListOf<MessageItem>()
                for (post in posts) {
                    repository.getCommentsByPost(post.id).collect { comments ->
                        for (comment in comments) {
                            messageItems.add(
                                MessageItem(
                                    commentId = comment.id,
                                    postId = post.id,
                                    postContent = post.content,
                                    commentContent = comment.content,
                                    commentIdentityName = comment.identityName,
                                    commentIdentityColor = comment.identityAvatarColor,
                                    createdAt = comment.createdAt
                                )
                            )
                        }
                    }
                }
                _messages.value = messageItems.sortedByDescending { it.createdAt }
            }
        }
    }
    
    class Factory(private val repository: WeiboRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MessageViewModel(repository) as T
        }
    }
}
