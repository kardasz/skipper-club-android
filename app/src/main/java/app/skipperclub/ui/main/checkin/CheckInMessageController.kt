package app.skipperclub.ui.main.checkin

import app.skipperclub.data.ChatsError
import app.skipperclub.data.CreateChatRequest
import app.skipperclub.ui.main.messages.ChatsGateway
import app.skipperclub.ui.main.messages.RealChatsGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the one-to-one chat is currently being opened. */
data class CheckInMessageState(val isOpeningChat: Boolean = false)

sealed interface CheckInMessageEvent {
    /** The one-to-one chat is ready; the caller opens the conversation on [chatId]. */
    data class OpenChat(val chatId: String) : CheckInMessageEvent
    data object SessionExpired : CheckInMessageEvent
    data class Failed(val error: Exception) : CheckInMessageEvent
}

/**
 * Opens a conversation with a checked-in member straight from the map's check-in
 * sheet: creates (or reuses) the one-to-one chat via `POST /chats`, then emits
 * [CheckInMessageEvent.OpenChat] so the UI can hand off to the full conversation —
 * the same flow the public profile uses for "Send message". Plain class owned by the
 * composable via `remember`; unit-tested with a fake gateway.
 */
class CheckInMessageController(
    private val scope: CoroutineScope,
    private val userId: String,
    private val accessToken: suspend () -> String?,
    private val chatsGateway: ChatsGateway = RealChatsGateway,
) {
    private val _state = MutableStateFlow(CheckInMessageState())
    val state: StateFlow<CheckInMessageState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CheckInMessageEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CheckInMessageEvent> = _events.asSharedFlow()

    fun openChat() {
        if (_state.value.isOpeningChat) return
        _state.update { it.copy(isOpeningChat = true) }
        scope.launch {
            val token = requireToken() ?: run {
                _state.update { it.copy(isOpeningChat = false) }
                return@launch
            }
            try {
                val chat = chatsGateway.createChat(token, CreateChatRequest(participantIds = listOf(userId)))
                _state.update { it.copy(isOpeningChat = false) }
                _events.tryEmit(CheckInMessageEvent.OpenChat(chat.id))
            } catch (error: ChatsError) {
                _state.update { it.copy(isOpeningChat = false) }
                _events.tryEmit(CheckInMessageEvent.Failed(error))
            }
        }
    }

    private suspend fun requireToken(): String? {
        val token = runCatching { accessToken() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (token == null) _events.tryEmit(CheckInMessageEvent.SessionExpired)
        return token
    }
}
