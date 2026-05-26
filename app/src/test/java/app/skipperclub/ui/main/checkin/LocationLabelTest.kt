package app.skipperclub.ui.main.checkin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationLabelTest {
    @Test
    fun titlePrefersCleanPlaceName() {
        val label = LocationLabel(
            placeName = "  Marina Gdansk  ",
            addressLine = "Szafarnia 6",
        )

        assertEquals("Marina Gdansk", label.title)
    }

    @Test
    fun titleFallsBackToAddressLine() {
        val label = LocationLabel(
            placeName = "   ",
            addressLine = "  Szafarnia 6  ",
        )

        assertEquals("Szafarnia 6", label.title)
    }

    @Test
    fun subtitleIsHiddenWhenAddressDuplicatesPlaceNameIgnoringCase() {
        val label = LocationLabel(
            placeName = "Marina Gdansk",
            addressLine = " marina gdansk ",
        )

        assertNull(label.subtitle)
        assertEquals("Marina Gdansk", label.submissionLabel)
    }

    @Test
    fun submissionLabelCombinesDistinctTitleAndSubtitle() {
        val label = LocationLabel(
            placeName = "Marina Gdansk",
            addressLine = "Szafarnia 6",
        )

        assertEquals("Marina Gdansk, Szafarnia 6", label.submissionLabel)
    }
}
