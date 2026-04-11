package com.myweibo.ui.screens.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myweibo.data.local.dao.CommentWithIdentity
import com.myweibo.data.local.dao.PostWithIdentity
import com.myweibo.data.local.entity.CommentEntity
import com.myweibo.data.local.entity.IdentityEntity
import com.myweibo.data.repository.WeiboRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MyPostsViewModel(private val repository: WeiboRepository) : ViewModel() {
    
    private val _allPosts = MutableStateFlow<List<PostWithIdentity>>(emptyList())
    val allPosts: StateFlow<List<PostWithIdentity>> = _allPosts.asStateFlow()
    
    private val _identities = MutableStateFlow<List<IdentityEntity>>(emptyList())
    val identities: StateFlow<List<IdentityEntity>> = _identities.asStateFlow()
    
    private val _selectedIdentityId = MutableStateFlow<Long?>(null)
    val selectedIdentityId: StateFlow<Long?> = _selectedIdentityId.asStateFlow()
    
    private val _filteredPosts = MutableStateFlow<List<PostWithIdentity>>(emptyList())
    val filteredPosts: StateFlow<List<PostWithIdentity>> = _filteredPosts.asStateFlow()
    
    private val _comments = MutableStateFlow<List<CommentWithIdentity>>(emptyList())
    val comments: StateFlow<List<CommentWithIdentity>> = _comments.asStateFlow()
    
    private val _showCommentSheet = MutableStateFlow(false)
    val showCommentSheet: StateFlow<Boolean> = _showCommentSheet.asStateFlow()
    
    private val _selectedPostId = MutableStateFlow<Long?>(null)
    val selectedPostId: StateFlow<Long?> = _selectedPostId.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            combine(
                repository.allPosts,
                repository.allIdentities
            ) { posts, identities ->
                Pair(posts, identities)
            }.collect { (posts, identities) ->
                _allPosts.value = posts
                _identities.value = identities
                filterPosts()
            }
        }
        
        viewModelScope.launch {
            _selectedIdentityId.collect {
                filterPosts()
            }
        }
    }
    
    private fun filterPosts() {
        val identityId = _selectedIdentityId.value
        _filteredPosts.value = if (identityId == null) {
            _allPosts.value
        } else {
            _allPosts.value.filter { it.identityId == identityId }
        }
    }
    
    fun selectIdentity(identityId: Long?) {
        _selectedIdentityId.value = identityId
    }
    
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
            return MyPostsViewModel(repository) as T
        }
    }
}
