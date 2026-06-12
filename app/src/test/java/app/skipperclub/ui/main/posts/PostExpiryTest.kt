package app.skipperclub.ui.main.posts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostExpiryTest {

    private val now = 1_700_000_000_000L

    @Test
    fun remainingMillisComputesDelta() {
        // expiresAt exactly one hour after `now`
        val remaining = PostExpiry.remainingMillis("2023-11-14T23:13:20Z", now)
        assertEquals(60 * 60_000L, remaining)
    }

    @Test
    fun remainingMillisIsNullForMissingOrInvalidDates() {
        assertNull(PostExpiry.remainingMillis(null, now))
        assertNull(PostExpiry.remainingMillis("", now))
        assertNull(PostExpiry.remainingMillis("not-a-date", now))
    }

    @Test
    fun phaseBucketsRemainingTime() {
        assertEquals(PostExpiry.Phase.Expired, PostExpiry.phase(0))
        assertEquals(PostExpiry.Phase.Expired, PostExpiry.phase(-5_000))
        // sub-minute remainder rounds up to one minute so it never shows "0 min"
        assertEquals(PostExpiry.Phase.Minutes(1), PostExpiry.phase(30_000))
        assertEquals(PostExpiry.Phase.Minutes(45), PostExpiry.phase(45 * 60_000L))
        assertEquals(PostExpiry.Phase.Hours(5, 30), PostExpiry.phase((5 * 60 + 30) * 60_000L))
        assertEquals(PostExpiry.Phase.Days(2, 3), PostExpiry.phase((2 * 24 + 3) * 60 * 60_000L))
    }

    @Test
    fun urgencyMatchesIosThresholds() {
        assertEquals(PostExpiry.Urgency.Critical, PostExpiry.urgency(30 * 60_000L))
        assertEquals(PostExpiry.Urgency.Critical, PostExpiry.urgency(60 * 60_000L))
        assertEquals(PostExpiry.Urgency.Warning, PostExpiry.urgency(3 * 60 * 60_000L))
        assertEquals(PostExpiry.Urgency.Normal, PostExpiry.urgency(24 * 60 * 60_000L))
    }
}
