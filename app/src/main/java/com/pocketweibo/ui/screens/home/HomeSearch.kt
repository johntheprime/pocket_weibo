package com.pocketweibo.ui.screens.home

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pocketweibo.data.local.dao.CommentSearchRow
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.ui.util.matchesAnySearchKeyword
import com.pocketweibo.ui.util.parseSearchKeywords
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Date filter for home search. Bounds are local calendar days, end exclusive. */
sealed interface SearchDateRange {
    object All : SearchDateRange
    object Today : SearchDateRange
    object Last7Days : SearchDateRange
    object Last30Days : SearchDateRange
    data class Custom(val startDay: LocalDate, val endDay: LocalDate) : SearchDateRange

    /** `[start, endExclusive)` epoch millis, or null for [All]. */
    fun bounds(nowMillis: Long, zone: ZoneId): Pair<Long, Long>? {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        fun startOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        return when (this) {
            All -> null
            Today -> startOf(today) to startOf(today.plusDays(1))
            Last7Days -> startOf(today.minusDays(6)) to startOf(today.plusDays(1))
            Last30Days -> startOf(today.minusDays(29)) to startOf(today.plusDays(1))
            is Custom -> {
                val (a, b) = if (startDay <= endDay) startDay to endDay else endDay to startDay
                startOf(a) to startOf(b.plusDays(1))
            }
        }
    }
}

/**
 * Home search state, kept by `MainScreen` so it survives opening a post and coming back.
 * [text] is a [TextFieldValue] so the cursor position is kept instead of jumping to 0.
 */
data class HomeSearchState(
    val open: Boolean = false,
    val text: TextFieldValue = TextFieldValue(""),
    val range: SearchDateRange = SearchDateRange.All,
) {
    val query: String get() = text.text.trim()
    val isActive: Boolean get() = query.isNotEmpty() || range != SearchDateRange.All

    /** Same text with the cursor at the end (used when the search bar is opened or refocused). */
    fun cursorAtEnd(): HomeSearchState = copy(text = text.copy(selection = TextRange(text.text.length)))
}

fun <T> List<T>.filterByDateRange(
    range: SearchDateRange,
    createdAt: (T) -> Long,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<T> {
    val (start, end) = range.bounds(nowMillis, zone) ?: return this
    return filter { createdAt(it) in start until end }
}

/** Keywords (body, author, or any comment on the post) AND date range. */
fun filterHomeSearch(
    posts: List<PostWithIdentity>,
    query: String,
    range: SearchDateRange,
    commentRows: List<CommentSearchRow>,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<PostWithIdentity> {
    val dated = posts.filterByDateRange(range, { it.createdAt }, nowMillis, zone)
    if (query.isBlank()) return dated
    val keywords = parseSearchKeywords(query)
    if (keywords.isEmpty()) return emptyList()
    val fromComments = commentRows.mapNotNull { row ->
        row.postId.takeIf {
            matchesAnySearchKeyword(keywords, row.content, row.commentAuthorName, row.postContent, row.postAuthorName)
        }
    }.toSet()
    return dated.filter { p ->
        matchesAnySearchKeyword(keywords, p.content, p.identityName) || p.id in fromComments
    }
}
