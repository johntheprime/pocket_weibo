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
}
