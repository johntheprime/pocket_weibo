package com.pocketweibo.ui.screens.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketweibo.data.local.dao.CommentWithIdentity
import com.pocketweibo.data.local.entity.CommentEntity
import com.pocketweibo.data.repository.WeiboRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReceivedMessage(
    val commentId: Long,
    val postId: Long,
    val postContent: String,
    val commentContent: String,
    val commentIdentityName: String,
    val commentIdentityResName: String,
    val createdAt: Long
)

class MessageViewModel(private val repository: WeiboRepository) : ViewModel() {
    
    private val _receivedMessages = MutableStateFlow<List<ReceivedMessage>>(emptyList())
    val receivedMessages: StateFlow<List<ReceivedMessage>> = _receivedMessages.asStateFlow()
    
    private val _sentMessages = MutableStateFlow<List<ReceivedMessage>>(emptyList())
    val sentMessages: StateFlow<List<ReceivedMessage>> = _sentMessages.asStateFlow()
    
    private val _selectedPostId = MutableStateFlow<Long?>(null)
    val selectedPostId: StateFlow<Long?> = _selectedPostId.asStateFlow()
    
    private val _comments = MutableStateFlow<List<CommentWithIdentity>>(emptyList())
    val comments: StateFlow<List<CommentWithIdentity>> = _comments.asStateFlow()
    
    private val _showCommentSheet = MutableStateFlow(false)
    val showCommentSheet: StateFlow<Boolean> = _showCommentSheet.asStateFlow()
    
    init {
        loadMessages()
    }
    
    private fun loadMessages() {
        viewModelScope.launch {
            combine(
                repository.activeIdentity,
                repository.allPosts
            ) { activeIdentity, allPosts ->
                Pair(activeIdentity, allPosts)
            }.collect { (activeIdentity, allPosts) ->
                if (activeIdentity != null) {
                    val myPostIds = allPosts.filter { it.identityId == activeIdentity.id }.map { it.id }.toSet()
                    
                    val received = mutableListOf<ReceivedMessage>()
                    val sent = mutableListOf<ReceivedMessage>()
                    
                    for (post in allPosts.filter { it.identityId == activeIdentity.id }) {
                        repository.getCommentsByPost(post.id).first().forEach { comment ->
                            received.add(
                                ReceivedMessage(
                                    commentId = comment.id,
                                    postId = post.id,
                                    postContent = post.content,
                                    commentContent = comment.content,
                                    commentIdentityName = comment.identityName,
                                    commentIdentityResName = comment.identityAvatarResName,
                                    createdAt = comment.createdAt
                                )
                            )
                        }
                    }
                    
                    for (post in allPosts.filter { it.identityId != activeIdentity.id }) {
                        repository.getCommentsByPost(post.id).first().forEach { comment ->
                            if (comment.identityId == activeIdentity.id) {
                                sent.add(
                                    ReceivedMessage(
                                        commentId = comment.id,
                                        postId = post.id,
                                        postContent = post.content,
                                        commentContent = comment.content,
                                        commentIdentityName = allPosts.find { it.identityId == activeIdentity.id }?.identityName ?: "",
                                        commentIdentityResName = activeIdentity.avatarResName,
                                        createdAt = comment.createdAt
                                    )
                                )
                            }
                        }
                    }
                    
                    _receivedMessages.value = received.sortedByDescending { it.createdAt }
                    _sentMessages.value = sent.sortedByDescending { it.createdAt }
                }
            }
        }
    }
    
    fun openComments(postId: Long) {
        _selectedPostId.value = postId
        viewModelScope.launch {
            repository.getCommentsByPost(postId).collect { commentList ->
                _comments.value = commentList
            }
        }
        _showCommentSheet.value = true
    }
    
    fun closeComments() {
        _showCommentSheet.value = false
        _selectedPostId.value = null
        _comments.value = emptyList()
    }
    
    fun addComment(postId: Long, content: String) {
        viewModelScope.launch {
            val activeIdentity = repository.activeIdentity.first()
            if (activeIdentity != null && content.isNotBlank()) {
                repository.insertComment(
                    CommentEntity(
                        postId = postId,
                        identityId = activeIdentity.id,
                        content = content
                    )
                )
            }
        }
    }

    fun deleteComment(commentId: Long, postId: Long) {
        viewModelScope.launch {
            repository.deleteComment(
                CommentEntity(
                    id = commentId,
                    postId = postId,
                    identityId = 0,
                    content = ""
                )
            )
            repository.getCommentsByPost(postId).collect { commentList ->
                _comments.value = commentList
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
