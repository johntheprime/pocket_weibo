package com.pocketweibo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposeIntentViewModelTest {

    @Test
    fun newPostRequestsIncrementFromZero() {
        val vm = ComposeIntentViewModel()
        assertEquals(0, vm.composeRequests.value)
        vm.requestNewPostCompose()
        assertEquals(1, vm.composeRequests.value)
        vm.requestNewPostCompose()
        assertEquals(2, vm.composeRequests.value)
    }

    @Test
    fun shareTextIsTrimmedCappedAndCleared() {
        val vm = ComposeIntentViewModel()
        vm.offerShareText("  hello  ")
        assertEquals("hello", vm.pendingShareText.value)
        vm.offerShareText("   ")
        assertNull(vm.pendingShareText.value)
        vm.offerShareText("x".repeat(5000))
        assertEquals(2000, vm.pendingShareText.value?.length)
        vm.consumeShareText()
        assertNull(vm.pendingShareText.value)
    }

    @Test
    fun openPostIgnoresNonPositiveIds() {
        val vm = ComposeIntentViewModel()
        vm.requestOpenPost(0L)
        assertNull(vm.openPostId.value)
        vm.requestOpenPost(-3L)
        assertNull(vm.openPostId.value)
        vm.requestOpenPost(42L)
        assertEquals(42L, vm.openPostId.value)
        vm.consumeOpenPost()
        assertNull(vm.openPostId.value)
    }
}
