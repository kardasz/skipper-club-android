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
    fun otherEventsLeaveThePresenceMapUnchanged() {
        val initial = mapOf("u1" to UserPresence(isOnline = true, lastSeen = null))

        assertEquals(initial, presenceAfter(initial, ChatRealtimeEvent.Disconnected))
        assertEquals(initial, presenceAfter(initial, ChatRealtimeEvent.Connected))
    }

    @Test
    fun startsEmpty() {
        assertNull(presenceAfter(emptyMap(), ChatRealtimeEvent.Disconnected)["u1"])
    }
}
