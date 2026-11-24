package com.pocketweibo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchKeywordsTest {

    @Test
    fun parsesPipeSeparatedKeywords() {
        assertEquals(listOf("苏轼", "李白"), parseSearchKeywords("苏轼|李白"))
    }

    @Test
    fun parsesFullWidthPipeAndTrimsParts() {
        assertEquals(listOf("Su Shi", "Li Bai"), parseSearchKeywords("  Su Shi ｜ Li Bai  "))
    }

    @Test
    fun dropsBlankPartsAndKeepsDuplicates() {
        assertEquals(listOf("a", "b"), parseSearchKeywords("| a || b |"))
        assertEquals(listOf("a", "a"), parseSearchKeywords("a|a"))
    }

    @Test
    fun returnsEmptyListForBlankOrSeparatorOnlyQuery() {
        assertTrue(parseSearchKeywords("").isEmpty())
        assertTrue(parseSearchKeywords("   ").isEmpty())
        assertTrue(parseSearchKeywords("||｜|").isEmpty())
    }

    @Test
    fun matchesAnyKeywordInAnyFieldCaseInsensitive() {
        val keywords = parseSearchKeywords("su shi|poet")
        assertTrue(matchesAnySearchKeyword(keywords, "Su Shi", "poem"))
        assertTrue(matchesAnySearchKeyword(keywords, null, "A famous POET"))
        assertFalse(matchesAnySearchKeyword(keywords, "Li Bai", "poem"))
        assertFalse(matchesAnySearchKeyword(emptyList(), "anything"))
    }

    @Test
    fun findsEveryCaseInsensitiveKeywordRange() {
        val text = "Su Shi wrote poems; SU SHI also wrote prose."
        val ranges = findKeywordRanges(text, listOf("su shi"))

        assertEquals(2, ranges.size)
        assertEquals("Su Shi", text.substring(ranges[0].first, ranges[0].last + 1))
        assertEquals("SU SHI", text.substring(ranges[1].first, ranges[1].last + 1))
    }

    @Test
    fun mergesOverlappingKeywordRanges() {
        val ranges = findKeywordRanges("abcdef", listOf("abc", "bcd", "ef"))

        assertEquals(2, ranges.size)
        assertEquals(0..3, ranges[0])
        assertEquals(4..5, ranges[1])
    }

    @Test
    fun blankKeywordsProduceNoRanges() {
        assertTrue(findKeywordRanges("anything", emptyList()).isEmpty())
        assertTrue(findKeywordRanges("anything", listOf(" ", "")).isEmpty())
        assertTrue(findKeywordRanges("", listOf("x")).isEmpty())
    }
}
