package app.skipperclub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CruiseReviewsDeepLinkTest {

    @Test
    fun parsesCruiseIdFromLocalizedReviewsPath() {
        assertEquals(
            "019cad57-87bd-7e53-9276-c5b34c748b1d",
            parseCruiseReviewsId(listOf("en", "cruises", "019cad57-87bd-7e53-9276-c5b34c748b1d", "reviews")),
        )
    }

    @Test
    fun parsesCruiseIdWithoutLocalePrefix() {
        assertEquals("c1", parseCruiseReviewsId(listOf("cruises", "c1", "reviews")))
    }

    @Test
    fun returnsNullForCruiseDetailPath() {
        assertNull(parseCruiseReviewsId(listOf("en", "cruises", "c1")))
    }

    @Test
    fun returnsNullForUnrelatedPath() {
        assertNull(parseCruiseReviewsId(listOf("en", "posts", "p1")))
    }

    @Test
    fun returnsNullForEmptyPath() {
        assertNull(parseCruiseReviewsId(emptyList()))
    }
}
