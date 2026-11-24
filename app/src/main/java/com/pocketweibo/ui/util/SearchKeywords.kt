package com.pocketweibo.ui.util

/** `|` (ASCII) and `｜` (full-width) separate alternative search keywords. */
private val SearchKeywordSeparator = Regex("[|｜]")

/**
 * Splits a raw query into OR keywords.
 *
 * `"苏轼 | 李白"` → `["苏轼", "李白"]`; blank parts are dropped.
 */
fun parseSearchKeywords(query: String): List<String> =
    query.split(SearchKeywordSeparator)
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/** True if any keyword occurs in at least one of [fields] (case-insensitive). */
fun matchesAnySearchKeyword(keywords: List<String>, vararg fields: String?): Boolean =
    keywords.isNotEmpty() && fields.any { field ->
        field != null && keywords.any { keyword -> field.contains(keyword, ignoreCase = true) }
    }

/**
 * All `start until end` ranges in [text] that match any of [keywords] (case-insensitive).
 * Overlapping matches are merged so the UI can draw one highlight span per region.
 */
fun findKeywordRanges(text: String, keywords: List<String>): List<IntRange> {
    if (text.isEmpty()) return emptyList()
    val needleRanges = mutableListOf<IntRange>()
    for (raw in keywords) {
        val keyword = raw.trim()
        if (keyword.isEmpty()) continue
        var from = 0
        while (from <= text.length - keyword.length) {
            val index = text.indexOf(keyword, from, ignoreCase = true)
            if (index < 0) break
            needleRanges.add(index until (index + keyword.length))
            from = index + keyword.length.coerceAtLeast(1)
        }
    }
    if (needleRanges.isEmpty()) return emptyList()
    needleRanges.sortBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (range in needleRanges) {
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged.add(range)
        }
    }
    return merged
}
