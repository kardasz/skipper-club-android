package app.skipperclub.ui.main.profile

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePresentationTest {

    @Test
    fun formatLocationJoinsCityAndCountryName() {
        assertEquals("Gdańsk, Poland", formatLocation("Gdańsk", "PL", Locale.ENGLISH))
    }

    @Test
    fun formatLocationFallsBackToRawCountryWhenUnknown() {
        assertEquals("Zzz", formatLocation(null, "Zzz", Locale.ENGLISH))
    }

    @Test
    fun formatLocationReturnsNullWhenEmpty() {
        assertNull(formatLocation(null, null))
        assertNull(formatLocation("", "  "))
    }

    @Test
    fun formatVoyageStyleTitleCases() {
        assertEquals("Coastal", formatVoyageStyle("coastal", Locale.ENGLISH))
        assertEquals("Racing", formatVoyageStyle("  racing ", Locale.ENGLISH))
    }

    @Test
    fun formatLanguageUppercasesTwoLetterCodes() {
        assertEquals("PL", formatLanguage("pl", Locale.ENGLISH))
        assertEquals("English", formatLanguage("English", Locale.ENGLISH))
    }

    @Test
    fun formatMemberSinceParsesIsoTimestamp() {
        val formatted = formatMemberSince("2025-01-15T10:00:00Z", Locale.ENGLISH)
        assertTrue(formatted!!.contains("2025"))
    }

    @Test
    fun formatMemberSinceReturnsNullForBadInput() {
        assertNull(formatMemberSince(null))
        assertNull(formatMemberSince("not-a-date"))
    }
}
