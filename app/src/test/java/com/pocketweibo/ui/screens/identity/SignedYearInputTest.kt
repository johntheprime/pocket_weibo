package com.pocketweibo.ui.screens.identity

import org.junit.Assert.assertEquals
import org.junit.Test

/** BUG-004: BC years survive editing. */
class SignedYearInputTest {
    @Test fun keepsLeadingMinus() = assertEquals("-551", signedYearInput("-551"))
    @Test fun acceptsUnicodeMinus() = assertEquals("-470", signedYearInput("\u2212470"))
    @Test fun dropsNonDigits() = assertEquals("1999", signedYearInput("19a99"))
    @Test fun capsAtFourDigits() = assertEquals("-1234", signedYearInput("-12345"))
    @Test fun loneMinusStaysEditable() = assertEquals("-", signedYearInput("-"))
}
