package com.pocketweibo.ui.screens.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketweibo.data.local.dao.CommentSearchRow
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.repository.WeiboRepository
import com.pocketweibo.ui.util.extractTopicTags
import com.pocketweibo.ui.util.isReservedTopicKey
import com.pocketweibo.ui.util.matchesAnySearchKeyword
import com.pocketweibo.ui.util.parseSearchKeywords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed class SearchResult {
    data class IdentityResult(val identity: IdentityEntity) : SearchResult()
    data class PostResult(val post: PostWithIdentity) : SearchResult()
    data class CommentResult(val row: CommentSearchRow) : SearchResult()
}

/** A topic derived from hashtags. `key` is stable across capitalization changes. */
data class TopicItem(
    val key: String,
    val displayName: String,
    val postCount: Int,
    val firstAt: Long,
    val lastAt: Long,
    val identityCount: Int
)

private class TopicAccumulator(
    val key: String,
    val displayName: String,
    var firstAt: Long,
    var lastAt: Long,
    val identityIds: MutableSet<Long>
) {
    var postCount: Int = 1

    fun toTopicItem() = TopicItem(
        key = key,
        displayName = displayName,
        postCount = postCount,
        firstAt = firstAt,
        lastAt = lastAt,
        identityCount = identityIds.size
    )
}

class TopicsViewModel(private val repository: WeiboRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _topics = MutableStateFlow<List<TopicItem>>(emptyList())
    val topics: StateFlow<List<TopicItem>> = _topics.asStateFlow()

    private val _allPosts = MutableStateFlow<List<PostWithIdentity>>(emptyList())
    val allPosts: StateFlow<List<PostWithIdentity>> = _allPosts.asStateFlow()

    init {
        observeTopics()
        observeSearch()
    }

    private fun observeTopics() {
        viewModelScope.launch {
            repository.allPosts.collect { posts ->
                _allPosts.value = posts
                val grouped = LinkedHashMap<String, TopicAccumulator>()
                // DAO already returns newest first, so the first display name we see is the latest.
                for (post in posts) {
                    for (tag in extractTopicTags(post.content)) {
                        if (isReservedTopicKey(tag.key)) continue
                        val current = grouped[tag.key]
                        if (current == null) {
                            grouped[tag.key] = TopicAccumulator(
                                key = tag.key,
                                displayName = tag.displayName,
                                firstAt = post.createdAt,
                                lastAt = post.createdAt,
                                identityIds = mutableSetOf(post.identityId ?: -1L)
                            )
                        } else {
                            current.postCount += 1
                            current.firstAt = minOf(current.firstAt, post.createdAt)
                            current.lastAt = maxOf(current.lastAt, post.createdAt)
                            current.identityIds.add(post.identityId ?: -1L)
                        }
                    }
                }
                _topics.value = grouped.values.map { it.toTopicItem() }.sortedWith(
                    compareByDescending<TopicItem> { it.lastAt }
                        .thenByDescending { it.postCount }
                        .thenBy { it.displayName }
                )
            }
        }
    }

    private fun observeSearch() {
        viewModelScope.launch {
            combine(
                _searchQuery,
                repository.allIdentities,
                repository.allPosts,
                repository.allCommentsForSearch
            ) { query, identities, posts, comments ->
                val keywords = parseSearchKeywords(query)
                if (keywords.isEmpty()) {
                    emptyList()
                } else {
                    val results = mutableListOf<SearchResult>()

                    identities.filter { identity ->
                        matchesAnySearchKeyword(keywords, identity.name)
                    }.forEach {
                        results.add(SearchResult.IdentityResult(it))
                    }

                    posts.filter { post ->
                        matchesAnySearchKeyword(keywords, post.identityName, post.content)
                    }.forEach {
                        results.add(SearchResult.PostResult(it))
                    }

                    comments.filter { row ->
                        matchesAnySearchKeyword(
                            keywords,
                            row.content,
                            row.commentAuthorName,
                            row.postContent,
                            row.postAuthorName
                        )
                    }.forEach { row ->
                        results.add(SearchResult.CommentResult(row))
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
            return TopicsViewModel(repository) as T
        }
    }
}
