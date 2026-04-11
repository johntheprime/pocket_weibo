package com.myweibo.ui.screens.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myweibo.data.local.dao.PostWithIdentity
import com.myweibo.data.local.entity.IdentityEntity
import com.myweibo.data.repository.WeiboRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed class SearchResult {
    data class IdentityResult(val identity: IdentityEntity) : SearchResult()
    data class PostResult(val post: PostWithIdentity) : SearchResult()
}

class DiscoverViewModel(private val repository: WeiboRepository) : ViewModel() {
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()
    
    private val _trendingIdentities = MutableStateFlow<List<IdentityEntity>>(emptyList())
    val trendingIdentities: StateFlow<List<IdentityEntity>> = _trendingIdentities.asStateFlow()
    
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
    }
    
    private fun observeSearch() {
        viewModelScope.launch {
            combine(
                _searchQuery,
                repository.allIdentities,
                repository.allPosts
            ) { query, identities, posts ->
                if (query.isBlank()) {
                    emptyList()
                } else {
                    val lowerQuery = query.lowercase()
                    val results = mutableListOf<SearchResult>()
                    
                    identities.filter { 
                        it.name.lowercase().contains(lowerQuery)
                    }.forEach { 
                        results.add(SearchResult.IdentityResult(it))
                    }
                    
                    posts.filter { 
                        it.identityName.lowercase().contains(lowerQuery) ||
                        it.content.lowercase().contains(lowerQuery)
                    }.forEach { 
                        results.add(SearchResult.PostResult(it))
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
    
    class Factory(private val repository: WeiboRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DiscoverViewModel(repository) as T
        }
    }
}
