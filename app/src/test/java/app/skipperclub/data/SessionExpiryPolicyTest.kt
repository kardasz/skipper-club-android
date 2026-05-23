package app.skipperclub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExpiryPolicyTest {
    @Test
    fun expiresAtEpochSecondsAddsLifetimeToIssuedAt() {
        assertEquals(
            1_900L,
            SessionExpiryPolicy.expiresAtEpochSeconds(
                issuedAtEpochSeconds = 1_000L,
                expiresInSeconds = 900L,
            ),
        )
    }

    @Test
    fun shouldRefreshReturnsFalseWhenTokenIsOutsideRefreshSkew() {
        assertFalse(
            SessionExpiryPolicy.shouldRefresh(
                accessTokenExpiresAtEpochSeconds = 1_900L,
                nowEpochSeconds = 1_800L,
            ),
        )
    }

    @Test
    fun shouldRefreshReturnsTrueWhenTokenIsInsideRefreshSkew() {
        assertTrue(
            SessionExpiryPolicy.shouldRefresh(
                accessTokenExpiresAtEpochSeconds = 1_900L,
                nowEpochSeconds = 1_840L,
            ),
        )
    }

    @Test
    fun shouldRefreshReturnsTrueWhenTokenExpired() {
        assertTrue(
            SessionExpiryPolicy.shouldRefresh(
                accessTokenExpiresAtEpochSeconds = 1_900L,
                nowEpochSeconds = 1_901L,
            ),
        )
    }
}
