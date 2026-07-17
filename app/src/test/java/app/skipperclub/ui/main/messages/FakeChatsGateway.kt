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

internal fun chatsPage(chats: List<Chat>, total: Int = chats.size, offset: Int = 0) =
    ChatsPage(chats = chats, total = total, limit = 20, offset = offset)

internal fun messagesPage(messages: List<ChatMessage>, total: Int = messages.size, offset: Int = 0) =
    MessagesPage(messages = messages, total = total, limit = 20, offset = offset)

/** Configurable in-memory [ChatsGateway]; records calls for assertions. */
internal class FakeChatsGateway : ChatsGateway {
    var chatPages: List<ChatsPage> = listOf(chatsPage(emptyList()))
    var listChatsError: ChatsError? = null
    val listChatsQueries = mutableListOf<ChatListQuery>()

    var messagePages: List<MessagesPage> = listOf(messagesPage(emptyList()))
    var listMessagesError: ChatsError? = null
    val listMessagesOffsets = mutableListOf<Int>()

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
        val page = chatPages[minOf(listChatsCallCount, chatPages.lastIndex)]
        listChatsCallCount++
        return page
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
        offset: Int,
        order: SortOrder,
    ): MessagesPage {
        calls += "listMessages:$chatId:$offset:${order.wireValue}"
        listMessagesOffsets += offset
        listMessagesError?.let { throw it }
        val page = messagePages[minOf(listMessagesCallCount, messagePages.lastIndex)]
        listMessagesCallCount++
        return page
    }

    override suspend fun sendMessage(
        accessToken: String,
        chatId: String,
        text: String,
        clientMessageId: String,
    ): ChatMessage {
        calls += "sendMessage:$chatId:$text"
        sentClientMessageIds += clientMessageId
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
