package com.pocketweibo.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.repository.WeiboRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed class SearchResult {
    data class IdentityResult(val identity: IdentityEntity) : SearchResult()
    data class PostResult(val post: PostWithIdentity) : SearchResult()
}

data class DateRange(val startDate: Long?, val endDate: Long?)

class DiscoverViewModel(private val repository: WeiboRepository) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()
    
    private val _trendingIdentities = MutableStateFlow<List<IdentityEntity>>(emptyList())
    val trendingIdentities: StateFlow<List<IdentityEntity>> = _trendingIdentities.asStateFlow()
    
    private val _trendingPosts = MutableStateFlow<List<PostWithIdentity>>(emptyList())
    val trendingPosts: StateFlow<List<PostWithIdentity>> = _trendingPosts.asStateFlow()
    
    private val _trendingTopics = MutableStateFlow<List<String>>(emptyList())
    val trendingTopics: StateFlow<List<String>> = _trendingTopics.asStateFlow()
    
    private val _selectedIdentityId = MutableStateFlow<Long?>(null)
    val selectedIdentityId: StateFlow<Long?> = _selectedIdentityId.asStateFlow()
    
    private val _dateRange = MutableStateFlow<DateRange?>(null)
    val dateRange: StateFlow<DateRange?> = _dateRange.asStateFlow()
    
    init {
        loadTrending()
        observeSearch()
    }
    
    private fun loadTrending() {
        viewModelScope.launch {
            repository.allIdentities.collect { identities ->
                _trendingIdentities.value = identities.take(5)
            }
        }
        viewModelScope.launch {
            repository.allPosts.collect { posts ->
                _trendingPosts.value = posts.take(10)
            }
        }
        viewModelScope.launch {
            repository.allPosts.collect { posts ->
                val topics = extractTrendingTopics(posts)
                _trendingTopics.value = topics.take(10)
            }
        }
    }
    
    private fun extractTrendingTopics(posts: List<PostWithIdentity>): List<String> {
        val wordCounts = mutableMapOf<String, Int>()
        val keywords = listOf("中国", "美国", "历史", "文化", "科技", "教育", "艺术", "科学", "政治", "经济", "哲学", "文学", "音乐", "体育", "旅行", "美食", "生活", "爱情", "友情", "梦想", "成功", "失败", "人生", "世界", "未来", "过去", "现在", "传统", "现代", "创新")
        
        posts.forEach { post ->
            keywords.forEach { keyword ->
                if (post.content.contains(keyword)) {
                    wordCounts[keyword] = (wordCounts[keyword] ?: 0) + 1
                }
            }
        }
        
        return wordCounts.entries.sortedByDescending { it.value }.map { it.key }
    }
    
    private fun observeSearch() {
        viewModelScope.launch {
            combine(
                _searchQuery,
                _selectedIdentityId,
                _dateRange,
                repository.allIdentities,
                repository.allPosts
            ) { query, selectedId, dateRange, identities, posts ->
                if (query.isBlank() && selectedId == null && dateRange == null) {
                    emptyList()
                } else {
                    val lowerQuery = query.lowercase()
                    val results = mutableListOf<SearchResult>()
                    var filteredPosts = posts
                    
                    if (selectedId != null) {
                        filteredPosts = filteredPosts.filter { it.identityId == selectedId }
                    }
                    
                    if (dateRange != null) {
                        filteredPosts = filteredPosts.filter { post ->
                            val afterStart = dateRange.startDate == null || post.createdAt >= dateRange.startDate
                            val beforeEnd = dateRange.endDate == null || post.createdAt <= dateRange.endDate
                            afterStart && beforeEnd
                        }
                    }
                    
                    if (selectedId != null) {
                        identities.filter { it.id == selectedId }.forEach { 
                            results.add(SearchResult.IdentityResult(it))
                        }
                    }
                    
                    filteredPosts.forEach { post ->
                        if (query.isBlank() || post.identityName.lowercase().contains(lowerQuery) || post.content.lowercase().contains(lowerQuery)) {
                            results.add(SearchResult.PostResult(post))
                        }
                    }
                    
                    results
                }
            }.collect { results ->
                _searchResults.value = results
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun setSelectedIdentity(identityId: Long?) {
        _selectedIdentityId.value = identityId
        if (identityId != null) {
            _searchQuery.value = ""
        }
    }
    
    fun setDateRange(startDate: Long?, endDate: Long?) {
        _dateRange.value = if (startDate == null && endDate == null) null else DateRange(startDate, endDate)
    }
    
    fun clearFilter() {
        _selectedIdentityId.value = null
        _dateRange.value = null
    }
    
    class Factory(private val repository: WeiboRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiscoverViewModel(repository) as T
        }
    }
}