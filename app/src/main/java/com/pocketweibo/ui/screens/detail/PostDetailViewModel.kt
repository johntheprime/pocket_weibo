package com.pocketweibo.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketweibo.data.local.dao.CommentWithIdentity
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.entity.CommentEntity
import com.pocketweibo.data.repository.WeiboRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PostDetailViewModel(private val repository: WeiboRepository) : ViewModel() {
    
    private val _post = MutableStateFlow<PostWithIdentity?>(null)
    val post: StateFlow<PostWithIdentity?> = _post.asStateFlow()
    
    private val _comments = MutableStateFlow<List<CommentWithIdentity>>(emptyList())
    val comments: StateFlow<List<CommentWithIdentity>> = _comments.asStateFlow()
    
    fun loadPost(postId: Long) {
        viewModelScope.launch {
            repository.allPosts.collect { posts ->
                _post.value = posts.find { it.id == postId }
            }
        }
        
        viewModelScope.launch {
            repository.getCommentsByPost(postId).collect { commentList ->
                _comments.value = commentList
            }
        }
    }
    
    fun toggleLike() {
        val currentPost = _post.value ?: return
        viewModelScope.launch {
            repository.togglePostLike(currentPost.id)
        }
    }
    
    fun addComment(content: String, parentCommentId: Long? = null) {
        val currentPost = _post.value ?: return
        viewModelScope.launch {
            repository.activeIdentity.collect { identity ->
                if (identity != null && content.isNotBlank()) {
                    repository.insertComment(
                        CommentEntity(
                            postId = currentPost.id,
                            identityId = identity.id,
                            content = content,
                            parentCommentId = parentCommentId
                        )
                    )
                }
            }
        }
    }

    fun editComment(commentId: Long, newContent: String) {
        viewModelScope.launch {
            repository.getCommentsByPost(_post.value?.id ?: return@launch).first().find { it.id == commentId }?.let { comment ->
                repository.updateComment(
                    CommentEntity(
                        id = commentId,
                        postId = comment.postId,
                        identityId = comment.identityId,
                        content = newContent,
                        createdAt = comment.createdAt,
                        parentCommentId = comment.parentCommentId
                    )
                )
            }
        }
    }
    
    class Factory(private val repository: WeiboRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PostDetailViewModel(repository) as T
        }
    }
}
