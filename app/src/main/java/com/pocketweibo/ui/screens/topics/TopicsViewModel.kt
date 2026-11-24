package com.pocketweibo.ui.screens.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.repository.WeiboRepository
import com.pocketweibo.ui.util.extractTopicTags
import com.pocketweibo.ui.util.isReservedTopicKey
import com.pocketweibo.ui.util.isTodoMarked
import com.pocketweibo.ui.util.resolveTopicKey
import com.pocketweibo.ui.util.stableTopicDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** A topic derived from hashtags. `key` is stable across capitalization changes. */
data class TopicItem(
    val key: String,
    val displayName: String,
    val postCount: Int,
    val firstAt: Long,
    val lastAt: Long,
    val identityCount: Int,
    /** Every hashtag key folded into this topic, including manual merge sources. */
    val sourceKeys: Set<String> = emptySet()
)

enum class TodoReason { MARKER, REMINDER, BOTH }

/** An unfinished TODO post and why it qualified for the list. */
data class TodoItem(
    val post: PostWithIdentity,
    val reason: TodoReason,
    val lastFiredAt: Long?
)

private class TopicAccumulator(
    val key: String,
    var firstAt: Long,
    var lastAt: Long,
    val identityIds: MutableSet<Long>
) {
    var postCount: Int = 1
    val displayNames = mutableListOf<String>()
    val sourceKeys = mutableSetOf(key)

    fun toTopicItem() = TopicItem(
        key = key,
        displayName = stableTopicDisplayName(displayNames),
        postCount = postCount,
        firstAt = firstAt,
        lastAt = lastAt,
        identityCount = identityIds.size,
        sourceKeys = sourceKeys.toSet()
    )
}

private data class TopicSnapshot(
    val posts: List<PostWithIdentity>,
    val aliases: Map<String, String>,
    val exclusions: Set<Pair<String, Long>>
)

class TopicsViewModel(private val repository: WeiboRepository) : ViewModel() {

    private val _topics = MutableStateFlow<List<TopicItem>>(emptyList())
    val topics: StateFlow<List<TopicItem>> = _topics.asStateFlow()

    private val _allPosts = MutableStateFlow<List<PostWithIdentity>>(emptyList())
    val allPosts: StateFlow<List<PostWithIdentity>> = _allPosts.asStateFlow()

    /** Manual topic merges: lowercase source key → lowercase target key. */
    private val _topicAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val topicAliases: StateFlow<Map<String, String>> = _topicAliases.asStateFlow()

    /** Posts manually removed from a canonical topic. */
    private val _topicExclusions = MutableStateFlow<Set<Pair<String, Long>>>(emptySet())
    val topicExclusions: StateFlow<Set<Pair<String, Long>>> = _topicExclusions.asStateFlow()

    private val _lastFirings = MutableStateFlow<Map<Long, Long>>(emptyMap())
    private val _unfinishedTodos = MutableStateFlow<List<TodoItem>>(emptyList())
    val unfinishedTodos: StateFlow<List<TodoItem>> = _unfinishedTodos.asStateFlow()

    private val _completedTodos = MutableStateFlow<List<PostWithIdentity>>(emptyList())
    val completedTodos: StateFlow<List<PostWithIdentity>> = _completedTodos.asStateFlow()

    init {
        observeTopics()
        observeReminderFirings()
        observeTodos()
    }

    private fun observeTopics() {
        viewModelScope.launch {
            combine(
                repository.allPosts,
                repository.topicAliases,
                repository.topicExclusions
            ) { posts, aliasRows, exclusionRows ->
                TopicSnapshot(
                    posts = posts,
                    aliases = aliasRows.associate { it.fromKey to it.toKey },
                    exclusions = exclusionRows.map { it.topicKey to it.postId }.toSet()
                )
            }.collect { snapshot ->
                _allPosts.value = snapshot.posts
                _topicAliases.value = snapshot.aliases
                _topicExclusions.value = snapshot.exclusions
                _topics.value = buildTopics(snapshot)
            }
        }
    }

    private fun buildTopics(snapshot: TopicSnapshot): List<TopicItem> {
        val grouped = LinkedHashMap<String, TopicAccumulator>()
        for (post in snapshot.posts) {
            // Resolve aliases before de-duplicating so `#AI #ai` counts once after a merge.
            val resolvedTags = extractTopicTags(post.content)
                .map { it to resolveTopicKey(it.key, snapshot.aliases) }
                .distinctBy { it.second }
            for ((tag, key) in resolvedTags) {
                if (isReservedTopicKey(tag.key)) continue
                if ((key to post.id) in snapshot.exclusions) continue
                val current = grouped[key]
                if (current == null) {
                    grouped[key] = TopicAccumulator(
                        key = key,
                        firstAt = post.createdAt,
                        lastAt = post.createdAt,
                        identityIds = mutableSetOf(post.identityId ?: -1L)
                    ).also {
                        it.displayNames.add(tag.displayName)
                        it.sourceKeys.add(tag.key)
                    }
                } else {
                    current.postCount += 1
                    current.displayNames.add(tag.displayName)
                    current.sourceKeys.add(tag.key)
                    current.firstAt = minOf(current.firstAt, post.createdAt)
                    current.lastAt = maxOf(current.lastAt, post.createdAt)
                    current.identityIds.add(post.identityId ?: -1L)
                }
            }
        }
        return grouped.values.map { it.toTopicItem() }.sortedWith(
            compareByDescending<TopicItem> { it.lastAt }
                .thenByDescending { it.postCount }
                .thenBy { it.displayName }
        )
    }

    private fun observeReminderFirings() {
        viewModelScope.launch {
            repository.observeLastReminderFirings().collect { rows ->
                _lastFirings.value = rows.associate { it.postId to it.lastFiredAtMillis }
            }
        }
    }

    private fun observeTodos() {
        viewModelScope.launch {
            combine(_allPosts, _lastFirings) { posts, firings ->
                val unfinished = mutableListOf<TodoItem>()
                val completed = mutableListOf<PostWithIdentity>()
                for (post in posts) {
                    if (post.todoDoneAt != null) {
                        completed.add(post)
                        continue
                    }
                    val marked = isTodoMarked(post.content)
                    val firedAt = firings[post.id]
                    if (marked || firedAt != null) {
                        val reason = when {
                            marked && firedAt != null -> TodoReason.BOTH
                            marked -> TodoReason.MARKER
                            else -> TodoReason.REMINDER
                        }
                        unfinished.add(TodoItem(post, reason, firedAt))
                    }
                }
                unfinished.sortWith(
                    compareByDescending<TodoItem> { it.lastFiredAt ?: it.post.createdAt }
                        .thenByDescending { it.post.createdAt }
                )
                completed.sortByDescending { it.todoDoneAt ?: 0L }
                unfinished to completed
            }.collect { (unfinished, completed) ->
                _unfinishedTodos.value = unfinished
                _completedTodos.value = completed
            }
        }
    }

    fun markTodoDone(postId: Long) {
        viewModelScope.launch {
            repository.setTodoDoneAt(postId, System.currentTimeMillis())
        }
    }

    fun reopenTodo(postId: Long) {
        viewModelScope.launch {
            repository.setTodoDoneAt(postId, null)
        }
    }

    fun mergeTopics(fromKey: String, toKey: String) {
        viewModelScope.launch { repository.mergeTopics(fromKey, toKey) }
    }

    /** Splits every merged spelling that currently resolves to [canonicalKey] back out. */
    fun unmergeTopicAliases(canonicalKey: String) {
        viewModelScope.launch { repository.unmergeTopicAliases(canonicalKey) }
    }

    fun excludePostFromTopic(topicKey: String, postId: Long) {
        viewModelScope.launch { repository.excludePostFromTopic(topicKey, postId) }
    }

    fun restoreExcludedPosts(topicKey: String) {
        viewModelScope.launch { repository.restoreExcludedPosts(topicKey) }
    }

    class Factory(private val repository: WeiboRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TopicsViewModel(repository) as T
        }
    }
}
