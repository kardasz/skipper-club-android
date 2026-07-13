package app.skipperclub.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeConnectionManagerTest {

    @Test
    fun holdsConnectionOnlyWhenForegroundedAndAuthenticated() {
        assertTrue(shouldHoldConnection(isForeground = true, isAuthenticated = true))
    }

    @Test
    fun dropsConnectionInBackground() {
        assertFalse(shouldHoldConnection(isForeground = false, isAuthenticated = true))
    }

    @Test
    fun dropsConnectionWhenLoggedOut() {
        assertFalse(shouldHoldConnection(isForeground = true, isAuthenticated = false))
        assertFalse(shouldHoldConnection(isForeground = false, isAuthenticated = false))
    }
}
