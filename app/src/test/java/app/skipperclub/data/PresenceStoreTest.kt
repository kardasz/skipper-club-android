package app.skipperclub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceStoreTest {

    @Test
    fun presenceUpdateSetsUserState() {
        val next = presenceAfter(
            emptyMap(),
            ChatRealtimeEvent.PresenceUpdate(userId = "u1", isOnline = true, lastSeen = null),
        )

        assertEquals(UserPresence(isOnline = true, lastSeen = null), next["u1"])
    }

    @Test
    fun presenceUpdateOverwritesPreviousStateForSameUser() {
        val initial = mapOf("u1" to UserPresence(isOnline = true, lastSeen = null))

        val next = presenceAfter(
            initial,
            ChatRealtimeEvent.PresenceUpdate(userId = "u1", isOnline = false, lastSeen = "2026-07-10T12:00:00Z"),
        )

        assertEquals(UserPresence(isOnline = false, lastSeen = "2026-07-10T12:00:00Z"), next["u1"])
    }

    @Test
    fun presenceUpdateLeavesOtherUsersUntouched() {
        val initial = mapOf("u1" to UserPresence(isOnline = true, lastSeen = null))

        val next = presenceAfter(
            initial,
            ChatRealtimeEvent.PresenceUpdate(userId = "u2", isOnline = true, lastSeen = null),
        )

        assertTrue(next["u1"]?.isOnline == true)
        assertTrue(next["u2"]?.isOnline == true)
    }

    @Test
    fun disconnectClearsThePresenceMap() {
        val initial = mapOf(
            "u1" to UserPresence(isOnline = true, lastSeen = null),
            "u2" to UserPresence(isOnline = false, lastSeen = "2026-07-10T12:00:00Z"),
        )

        assertTrue(presenceAfter(initial, ChatRealtimeEvent.Disconnected).isEmpty())
    }

    @Test
    fun otherEventsLeaveThePresenceMapUnchanged() {
        val initial = mapOf("u1" to UserPresence(isOnline = true, lastSeen = null))

        assertEquals(initial, presenceAfter(initial, ChatRealtimeEvent.Connected))
    }

    @Test
    fun startsEmpty() {
        assertNull(presenceAfter(emptyMap(), ChatRealtimeEvent.Disconnected)["u1"])
    }

    @Test
    fun seedAppliesSnapshotForUsersWithoutALiveUpdate() {
        val snapshot = mapOf(
            "u1" to UserPresence(isOnline = true, lastSeen = null),
            "u2" to UserPresence(isOnline = false, lastSeen = "2026-07-10T12:00:00Z"),
        )

        val next = seededPresence(current = emptyMap(), snapshot = snapshot, liveUpdatedSinceOpen = emptySet())

        assertEquals(UserPresence(isOnline = true, lastSeen = null), next["u1"])
        assertEquals(UserPresence(isOnline = false, lastSeen = "2026-07-10T12:00:00Z"), next["u2"])
    }

    @Test
    fun seedDoesNotOverwriteAUserThatAlreadyGotALiveUpdate() {
        // A live event landed for u1 (online) before the snapshot arrived; the snapshot says u1 is
        // offline. The race rule must keep the live value and still seed the untouched u2.
        val current = mapOf("u1" to UserPresence(isOnline = true, lastSeen = null))
        val snapshot = mapOf(
            "u1" to UserPresence(isOnline = false, lastSeen = "2026-07-10T12:00:00Z"),
            "u2" to UserPresence(isOnline = true, lastSeen = null),
        )

        val next = seededPresence(current = current, snapshot = snapshot, liveUpdatedSinceOpen = setOf("u1"))

        assertEquals(UserPresence(isOnline = true, lastSeen = null), next["u1"])
        assertEquals(UserPresence(isOnline = true, lastSeen = null), next["u2"])
    }
}
