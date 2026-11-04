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
