package app.skipperclub.ui.main.messages

import app.skipperclub.data.Chat
import app.skipperclub.data.ChatListQuery
import app.skipperclub.data.ChatMessage
import app.skipperclub.data.ChatType
import app.skipperclub.data.ChatUser
import app.skipperclub.data.ChatsError
import app.skipperclub.data.ChatsPage
import app.skipperclub.data.CreateChatRequest
import app.skipperclub.data.MessagesPage
import app.skipperclub.data.SortOrder
import app.skipperclub.data.UserSearchQuery
import app.skipperclub.data.UsersPage
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred

internal fun testUser(id: String, name: String = "User $id") = ChatUser(id = id, name = name)

internal fun testMessage(
    id: String,
    chatId: String = "chat-1",
    text: String = "message $id",
    userId: String = "other",
    createdAt: String = "2026-06-12T10:00:00Z",
    clientMessageId: String? = null,
) = ChatMessage(
    id = id,
    chatId = chatId,
    text = text,
    read = false,
    user = testUser(userId),
    createdAt = createdAt,
    updatedAt = createdAt,
    clientMessageId = clientMessageId,
)

internal fun testChat(
    id: String,
    type: ChatType = ChatType.OneToOne,
    name: String? = null,
    participants: List<ChatUser> = listOf(testUser("me"), testUser("other")),
    lastMessage: ChatMessage? = null,
    unreadCount: Int = 0,
) = Chat(
    id = id,
    type = type,
    name = name,
    participants = participants,
    lastMessage = lastMessage,
    unreadCount = unreadCount,
    updatedAt = "2026-06-12T10:00:00Z",
)

internal fun chatsPage(
    chats: List<Chat>,
    total: Int = chats.size,
    offset: Int = 0,
    // Preserve the pre-cursor "has more" intent (offset + size < total) by mapping it onto a
    // non-null nextCursor, so tests keep expressing "more pages" via `total` while the client now
    // reads `hasMore` from the cursor (same bridge as messagesPage below).
    nextCursor: String? = if (offset + chats.size < total) "chats-cursor-${offset + chats.size}" else null,
) = ChatsPage(chats = chats, total = total, limit = 20, offset = offset, nextCursor = nextCursor)

internal fun messagesPage(
    messages: List<ChatMessage>,
    total: Int = messages.size,
    offset: Int = 0,
    // Preserve the pre-cursor "has more" intent (offset + size < total) by mapping it onto a
    // non-null nextCursor, so tests keep expressing "more pages" via `total` while the client now
    // reads `hasMore` from the cursor.
    nextCursor: String? = if (offset + messages.size < total) "cursor-${offset + messages.size}" else null,
) = MessagesPage(messages = messages, total = total, limit = 20, offset = offset, nextCursor = nextCursor)

/** Configurable in-memory [ChatsGateway]; records calls for assertions. */
internal class FakeChatsGateway : ChatsGateway {
    var chatPages: List<ChatsPage> = listOf(chatsPage(emptyList()))
    var listChatsError: ChatsError? = null
    val listChatsQueries = mutableListOf<ChatListQuery>()

    var messagePages: List<MessagesPage> = listOf(messagesPage(emptyList()))
    var listMessagesError: ChatsError? = null
    /** The `before` keyset cursor each `listMessages` call carried; `null` for a first/newest page. */
    val listMessagesBefores = mutableListOf<String?>()
    val listMessagesLimits = mutableListOf<Int>()

    /**
     * When set, `listMessages`/`sendMessage` park on it after recording the call, so a test can hold
     * a request in flight and observe what the controller does meanwhile. Everything else stays
     * non-suspending, which is what keeps the Unconfined-scope tests synchronous.
     */
    var listMessagesGate: CompletableDeferred<Unit>? = null
    var sendMessageGate: CompletableDeferred<Unit>? = null

    var chat: Chat = testChat("chat-1")
    var getChatError: ChatsError? = null

    var createdChat: Chat = testChat("created")
    var createChatRequests = mutableListOf<CreateChatRequest>()

    var sentMessage: ChatMessage? = null
    var mutationError: ChatsError? = null
    val sentClientMessageIds = mutableListOf<String>()

    var usersPage: UsersPage = UsersPage(emptyList(), total = 0, limit = 20, offset = 0)
    var searchUsersError: ChatsError? = null
    val userSearchQueries = mutableListOf<UserSearchQuery>()

    // Synchronized: the debounced chat-list reload resumes on the coroutine delay-scheduler thread,
    // so a test polling this list reads it off the thread that records the call. Reads that iterate
    // (count/any/…) must lock on the list itself.
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private var listChatsCallCount = 0
    private var listMessagesCallCount = 0

    override suspend fun listChats(accessToken: String, query: ChatListQuery): ChatsPage {
        calls += "listChats"
        listChatsQueries += query
        listChatsError?.let { throw it }
        val servedIndex = listChatsCallCount
        val basePage = chatPages[minOf(listChatsCallCount, chatPages.lastIndex)]
        listChatsCallCount++
        // Give every "has more" page a distinct, call-indexed cursor so the controller threads a
        // distinct `cursor` on the next call (same rationale as listMessages below). A last page
        // keeps its null cursor.
        return if (basePage.nextCursor != null) basePage.copy(nextCursor = "chats-cursor-$servedIndex") else basePage
    }

    override suspend fun createChat(accessToken: String, payload: CreateChatRequest): Chat {
        calls += "createChat"
        createChatRequests += payload
        mutationError?.let { throw it }
        return createdChat
    }

    override suspend fun getChat(accessToken: String, chatId: String): Chat {
        calls += "getChat:$chatId"
        getChatError?.let { throw it }
        return chat
    }

    override suspend fun deleteChat(accessToken: String, chatId: String) {
        calls += "deleteChat:$chatId"
        mutationError?.let { throw it }
    }

    override suspend fun listMessages(
        accessToken: String,
        chatId: String,
        limit: Int,
        before: String?,
        order: SortOrder,
    ): MessagesPage {
        calls += "listMessages:$chatId:$before:${order.wireValue}"
        listMessagesBefores += before
        listMessagesLimits += limit
        listMessagesGate?.await()
        listMessagesError?.let { throw it }
        val servedIndex = listMessagesCallCount
        val basePage = messagePages[minOf(listMessagesCallCount, messagePages.lastIndex)]
        listMessagesCallCount++
        // Give every "has more" page a distinct, call-indexed cursor so the controller threads a
        // distinct `before` on the next call (the fake serves pages by call order, not by cursor, so
        // without this every page would hand back the same cursor and the threaded sequence would be
        // indistinguishable). A last page keeps its null cursor.
        return if (basePage.nextCursor != null) basePage.copy(nextCursor = "cursor-$servedIndex") else basePage
    }

    override suspend fun sendMessage(
        accessToken: String,
        chatId: String,
        text: String,
        clientMessageId: String,
    ): ChatMessage {
        calls += "sendMessage:$chatId:$text"
        sentClientMessageIds += clientMessageId
        sendMessageGate?.await()
        mutationError?.let { throw it }
        return sentMessage ?: testMessage("sent", chatId = chatId, text = text, userId = "me")
    }

    override suspend fun markChatsRead(accessToken: String, chatIds: List<String>) {
        calls += "markChatsRead:${chatIds.joinToString(",")}"
        mutationError?.let { throw it }
    }

    override suspend fun searchUsers(accessToken: String, query: UserSearchQuery): UsersPage {
        calls += "searchUsers:${query.search.orEmpty()}"
        userSearchQueries += query
        searchUsersError?.let { throw it }
        return usersPage
    }
}
