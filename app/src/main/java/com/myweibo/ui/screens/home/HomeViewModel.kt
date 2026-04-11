package com.myweibo.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myweibo.data.local.dao.CommentWithIdentity
import com.myweibo.data.local.entity.CommentEntity
import com.myweibo.data.repository.WeiboRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: WeiboRepository) : ViewModel() {
    
    private val _selectedPostId = MutableStateFlow<Long?>(null)
    val selectedPostId: StateFlow<Long?> = _selectedPostId.asStateFlow()
    
    private val _comments = MutableStateFlow<List<CommentWithIdentity>>(emptyList())
    val comments: StateFlow<List<CommentWithIdentity>> = _comments.asStateFlow()
    
    private val _showCommentSheet = MutableStateFlow(false)
    val showCommentSheet: StateFlow<Boolean> = _showCommentSheet.asStateFlow()
    
    fun toggleLike(postId: Long) {
        viewModelScope.launch {
            repository.togglePostLike(postId)
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
            val activeIdentity = repository.activeIdentity
            activeIdentity.collect { identity ->
                if (identity != null && content.isNotBlank()) {
                    repository.insertComment(
                        CommentEntity(
                            postId = postId,
                            identityId = identity.id,
                            content = content
                        )
                    )
                }
            }
        }
    }
    
    class Factory(private val repository: WeiboRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
