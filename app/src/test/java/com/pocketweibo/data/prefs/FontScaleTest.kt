package com.pocketweibo.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class FontScaleTest {

    @Test
    fun stepsIncludeDefaultInAscendingOrder() {
        assertEquals(listOf(85, 100, 115, 130), FontScale.steps)
        assertEquals(100, FontScale.DEFAULT)
    }

    @Test
    fun normalizeSnapsToNearestSelectableStep() {
        assertEquals(FontScale.DEFAULT, FontScale.normalize(null))
        assertEquals(100, FontScale.normalize(100))
        assertEquals(85, FontScale.normalize(92))
        assertEquals(115, FontScale.normalize(108))
        assertEquals(130, FontScale.normalize(999))
        assertEquals(85, FontScale.normalize(-10))
    }
}
