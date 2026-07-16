package app.skipperclub.ui.main.messages

import app.skipperclub.data.Chat
import app.skipperclub.data.ChatBulkAction
import app.skipperclub.data.ChatListQuery
import app.skipperclub.data.ChatMessage
import app.skipperclub.data.ChatsApi
import app.skipperclub.data.ChatsPage
import app.skipperclub.data.CreateChatRequest
import app.skipperclub.data.MessagesPage
import app.skipperclub.data.SortOrder
import app.skipperclub.data.UserSearchQuery
import app.skipperclub.data.UsersPage

/**
 * Seam between the messages UI controllers and [ChatsApi] so state-machine
 * logic stays unit-testable with fakes (no MockWebServer needed at this layer).
 */
interface ChatsGateway {
    suspend fun listChats(accessToken: String, query: ChatListQuery): ChatsPage
    suspend fun createChat(accessToken: String, payload: CreateChatRequest): Chat
    suspend fun getChat(accessToken: String, chatId: String): Chat
    suspend fun deleteChat(accessToken: String, chatId: String)
    suspend fun listMessages(
        accessToken: String,
        chatId: String,
        limit: Int,
        offset: Int,
        order: SortOrder,
    ): MessagesPage

    /** [clientMessageId] is the idempotency key sent as `clientMessageId`; see [ChatsApi.sendMessage]. */
    suspend fun sendMessage(
        accessToken: String,
        chatId: String,
        text: String,
        clientMessageId: String,
    ): ChatMessage

    suspend fun markChatsRead(accessToken: String, chatIds: List<String>)
    suspend fun searchUsers(accessToken: String, query: UserSearchQuery): UsersPage
}

object RealChatsGateway : ChatsGateway {
    override suspend fun listChats(accessToken: String, query: ChatListQuery): ChatsPage =
        ChatsApi.listChats(accessToken, query)

    override suspend fun createChat(accessToken: String, payload: CreateChatRequest): Chat =
        ChatsApi.createChat(accessToken, payload)

    override suspend fun getChat(accessToken: String, chatId: String): Chat =
        ChatsApi.getChat(accessToken, chatId)

    override suspend fun deleteChat(accessToken: String, chatId: String) =
        ChatsApi.deleteChat(accessToken, chatId)

    override suspend fun listMessages(
        accessToken: String,
        chatId: String,
        limit: Int,
        offset: Int,
        order: SortOrder,
    ): MessagesPage = ChatsApi.listMessages(accessToken, chatId, limit, offset, order)

    override suspend fun sendMessage(
        accessToken: String,
        chatId: String,
        text: String,
        clientMessageId: String,
    ): ChatMessage = ChatsApi.sendMessage(accessToken, chatId, text, clientMessageId)

    override suspend fun markChatsRead(accessToken: String, chatIds: List<String>) =
        ChatsApi.bulkAction(accessToken, ChatBulkAction.MarkRead, chatIds)

    override suspend fun searchUsers(accessToken: String, query: UserSearchQuery): UsersPage =
        ChatsApi.searchUsers(accessToken, query)
}
