package com.pocketweibo.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoMarkerTest {

    @Test
    fun acceptsStartOfPostWithAnyDelimiter() {
        assertTrue(isTodoMarked("TODO: 买菜"))
        assertTrue(isTodoMarked("TODO-回电话"))
        assertTrue(isTodoMarked("TODO买菜"))
        assertTrue(isTodoMarked("TODO"))
    }

    @Test
    fun acceptsHashtagAnywhere() {
        assertTrue(isTodoMarked("周末计划 #TODO 买菜"))
        assertTrue(isTodoMarked("周末计划 #TODO买菜"))
        assertTrue(isTodoMarked("周末计划 #TODO: 买菜"))
    }

    @Test
    fun ignoresLowercaseAndMidWordMatches() {
        assertFalse(isTodoMarked("todo: 买菜"))
        assertFalse(isTodoMarked("今天 todo 买菜"))
        assertFalse(isTodoMarked("今天 TODO 买菜"))
        assertFalse(isTodoMarked("TODOS 清单"))
        assertFalse(isTodoMarked("TODO2"))
        assertFalse(isTodoMarked("notTODO: 买菜"))
    }
}
