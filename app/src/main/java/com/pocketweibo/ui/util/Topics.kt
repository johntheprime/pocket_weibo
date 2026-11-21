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

private data class ParsedHashtag(val start: Int, val end: Int, val displayName: String, val key: String)

private fun parseHashtags(content: String): List<ParsedHashtag> =
    HashtagRegex.findAll(content).mapNotNull { match ->
        val group = match.groups[1] ?: return@mapNotNull null
        val displayName = group.value.trimEnd(*TrailingHashtagPunctuation)
        if (displayName.isEmpty()) return@mapNotNull null
        val key = displayName.lowercase(Locale.ROOT)
        if (isReservedTopicKey(key)) return@mapNotNull null
        ParsedHashtag(
            start = group.range.first - 1,
            end = group.range.first + displayName.length,
            displayName = displayName,
            key = key
        )
    }.toList()

/** Extracts distinct hashtag topics from [content], in first-seen order. */
fun extractTopicTags(content: String): List<TopicTag> =
    parseHashtags(content)
        .distinctBy { it.key }
        .map { TopicTag(displayName = it.displayName, key = it.key) }

/** True for the reserved `TODO` / `#TODO` tag. */
fun isReservedTopicKey(key: String): Boolean = key.lowercase(Locale.ROOT) == RESERVED_TOPIC_KEY

/** A tappable hashtag span inside a post body. [start]/[end] include the leading `#`. */
data class TopicLink(val start: Int, val end: Int, val displayName: String, val key: String)

/** Every hashtag span in [content], in text order, using the same rules as [extractTopicTags]. */
fun extractTopicLinks(content: String): List<TopicLink> =
    parseHashtags(content).map { TopicLink(it.start, it.end, it.displayName, it.key) }
