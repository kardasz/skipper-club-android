package app.skipperclub.ui.main.checkin

import app.skipperclub.data.ChatsError
import app.skipperclub.ui.main.messages.FakeChatsGateway
import app.skipperclub.ui.main.messages.testChat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInMessageControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined + Job())
    private val chatsGateway = FakeChatsGateway()
    private val events = mutableListOf<CheckInMessageEvent>()

    private fun controller(userId: String = "other", token: String? = "token"): CheckInMessageController {
        val controller = CheckInMessageController(
            scope = scope,
            userId = userId,
            accessToken = { token },
            chatsGateway = chatsGateway,
        )
        scope.launch { controller.events.collect { events += it } }
        return controller
    }

    @Test
    fun openChatCreatesOneToOneChatAndEmitsOpen() {
        chatsGateway.createdChat = testChat("created-chat")
        val controller = controller(userId = "other")

        controller.openChat()

        assertEquals(listOf("other"), chatsGateway.createChatRequests.single().participantIds)
        assertTrue(events.contains(CheckInMessageEvent.OpenChat("created-chat")))
        assertFalse(controller.state.value.isOpeningChat)
    }

    @Test
    fun openChatDoesNotSendAnyMessage() {
        val controller = controller(userId = "other")

        controller.openChat()

        assertFalse(chatsGateway.calls.any { it.startsWith("sendMessage") })
    }

    @Test
    fun missingTokenEmitsSessionExpired() {
        val controller = controller(token = null)

        controller.openChat()

        assertTrue(events.contains(CheckInMessageEvent.SessionExpired))
        assertTrue(chatsGateway.calls.isEmpty())
        assertFalse(controller.state.value.isOpeningChat)
    }

    @Test
    fun failureEmitsFailedAndClearsOpening() {
        chatsGateway.mutationError = ChatsError.Network(RuntimeException("boom"))
        val controller = controller(userId = "other")

        controller.openChat()

        assertTrue(events.any { it is CheckInMessageEvent.Failed })
        assertFalse(controller.state.value.isOpeningChat)
    }
}
