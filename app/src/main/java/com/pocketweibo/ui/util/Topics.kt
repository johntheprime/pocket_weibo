package com.pocketweibo.ui.util

import java.util.Locale

/**
 * A hashtag as written in a post, plus its case-insensitive lookup key.
 * The display name keeps the author's capitalization; [key] merges `#AI` and `#ai`.
 */
data class TopicTag(val displayName: String, val key: String)

/** `TODO` is reserved for the first-class TODO list, not the topic index. */
private const val RESERVED_TOPIC_KEY = "todo"

/**
 * Stops at whitespace and the punctuation people normally use to end a hashtag.
 * CJK text has no word separator, so authors can end a tag with punctuation or a space.
 */
private val HashtagRegex = Regex(
    """#([^\s#\[\](){}<>"'“”‘’，,。．！？!?；;：:|｜]+)"""
)

private val TrailingHashtagPunctuation = charArrayOf(
    '.', '。', '．', ',', '，', '、', ';', '；', ':', '：', '!', '！', '?', '？',
    '|', '｜', ')', ']', '}', '>', '”', '’', '"', '\''
)

/** Extracts distinct hashtag topics from [content], in first-seen order. */
fun extractTopicTags(content: String): List<TopicTag> {
    val seen = HashSet<String>()
    return HashtagRegex.findAll(content)
        .map { it.groupValues[1].trimEnd(*TrailingHashtagPunctuation) }
        .filter { it.isNotEmpty() }
        .mapNotNull { raw ->
            val key = raw.lowercase(Locale.ROOT)
            if (isReservedTopicKey(key) || !seen.add(key)) null else TopicTag(displayName = raw, key = key)
        }
        .toList()
}

/** True for the reserved `TODO` / `#TODO` tag. */
fun isReservedTopicKey(key: String): Boolean = key.lowercase(Locale.ROOT) == RESERVED_TOPIC_KEY
