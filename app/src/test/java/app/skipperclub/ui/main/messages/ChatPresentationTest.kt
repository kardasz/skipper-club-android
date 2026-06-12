package app.skipperclub.ui.main.messages

import app.skipperclub.data.ChatType
import app.skipperclub.data.ChatUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPresentationTest {

    private val me = ChatUser("me", "Current User")
    private val jan = ChatUser("u1", "Jan Kowalski")
    private val anna = ChatUser("u2", "Anna Nowak")
    private val piotr = ChatUser("u3", "Piotr Wiśniewski")
    private val maria = ChatUser("u4", "Maria Lewandowska")
    private val tomek = ChatUser("u5", "Tomek Zieliński")

    @Test
    fun otherParticipantsExcludesCurrentUser() {
        val chat = testChat("c1", participants = listOf(me, jan, anna))

        assertEquals(listOf("u1", "u2"), otherParticipants(chat, "me").map { it.id })
    }

    @Test
    fun explicitNameWinsOverParticipants() {
        val chat = testChat(
            "c1",
            type = ChatType.Group,
            name = "Summer Crew",
            participants = listOf(me, jan, anna),
        )

        assertEquals("Summer Crew", chatTitle(chat, "me"))
    }

    @Test
    fun oneToOneTitleIsOtherParticipantName() {
        val chat = testChat("c1", participants = listOf(me, jan))

        assertEquals("Jan Kowalski", chatTitle(chat, "me"))
    }

    @Test
    fun unnamedGroupTitleJoinsUpToThreeNames() {
        val chat = testChat(
            "c1",
            type = ChatType.Group,
            participants = listOf(me, jan, anna, piotr),
        )

        assertEquals("Jan Kowalski, Anna Nowak, Piotr Wiśniewski", chatTitle(chat, "me"))
    }

    @Test
    fun unnamedGroupTitleAppendsOverflowCount() {
        val chat = testChat(
            "c1",
            type = ChatType.Group,
            participants = listOf(me, jan, anna, piotr, maria, tomek),
        )

        assertEquals("Jan Kowalski, Anna Nowak, Piotr Wiśniewski +2", chatTitle(chat, "me"))
    }

    @Test
    fun titleIsNullWhenNoOtherParticipants() {
        val chat = testChat("c1", participants = listOf(me))

        assertNull(chatTitle(chat, "me"))
    }

    @Test
    fun blankNameFallsBackToParticipants() {
        val chat = testChat("c1", name = " ", participants = listOf(me, jan))

        assertEquals("Jan Kowalski", chatTitle(chat, "me"))
    }

    @Test
    fun initialsUseFirstTwoWords() {
        assertEquals("JK", jan.initials())
        assertEquals("A", ChatUser("x", "Anna").initials())
        assertEquals("?", ChatUser("x", "  ").initials())
    }
}
