package app.skipperclub.ui.main.posts

import app.skipperclub.data.PostCoordinates
import app.skipperclub.data.PostSortField
import app.skipperclub.data.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearMeTest {

    @Test
    fun `nautical miles convert to kilometres`() {
        assertEquals(2, nauticalMilesToKm(1)) // 1.852 -> 2
        assertEquals(22, nauticalMilesToKm(12)) // 22.224 -> 22
        assertEquals(93, nauticalMilesToKm(50)) // 92.6 -> 93
    }

    @Test
    fun `kilometres round-trip back to nautical miles within range`() {
        assertEquals(12, kmToNauticalMiles(nauticalMilesToKm(12)))
        assertEquals(NearMeMaxNm, kmToNauticalMiles(nauticalMilesToKm(NearMeMaxNm)))
        assertEquals(NearMeMinNm, kmToNauticalMiles(0))
        assertEquals(NearMeMaxNm, kmToNauticalMiles(10_000))
    }

    @Test
    fun `withNearMe sets center, radius and nearest-first ordering`() {
        val center = PostCoordinates(lat = 54.35, lng = 18.65)
        val filters = PostFilters(query = "storm").withNearMe(center, radiusNm = 12, label = "My location")

        assertEquals(center, filters.center)
        assertEquals("My location", filters.centerLabel)
        assertEquals(22, filters.radiusKm)
        assertEquals(PostSortField.Distance, filters.sort)
        assertEquals(SortOrder.Asc, filters.order)
        assertTrue(filters.isNearMeActive)
        assertEquals(12, filters.nearMeRadiusNm)
        // Unrelated filters are preserved.
        assertEquals("storm", filters.query)
    }

    @Test
    fun `withNearMe clamps radius to the slider range`() {
        val center = PostCoordinates(0.0, 0.0)
        assertEquals(nauticalMilesToKm(NearMeMaxNm), PostFilters().withNearMe(center, 999, "x").radiusKm)
        assertEquals(nauticalMilesToKm(NearMeMinNm), PostFilters().withNearMe(center, 0, "x").radiusKm)
    }

    @Test
    fun `clearNearMe removes distance search and restores chronological order`() {
        val center = PostCoordinates(54.35, 18.65)
        val cleared = PostFilters(query = "storm")
            .withNearMe(center, radiusNm = 12, label = "My location")
            .clearNearMe()

        assertNull(cleared.center)
        assertNull(cleared.centerLabel)
        assertNull(cleared.radiusKm)
        assertEquals(PostSortField.PublishedAt, cleared.sort)
        assertEquals(SortOrder.Desc, cleared.order)
        assertFalse(cleared.isNearMeActive)
        assertNull(cleared.nearMeRadiusNm)
        assertEquals("storm", cleared.query)
    }
}
