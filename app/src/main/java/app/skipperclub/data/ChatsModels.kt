package app.skipperclub.data

import kotlinx.serialization.Serializable

/** Chat type classification. Wire values follow `docs/api/openapi.yaml` (`ChatType`). */
enum class ChatType(val wireValue: String) {
    OneToOne("ONE_TO_ONE"),
    Group("GROUP"),
    CruiseQna("CRUISE_QNA"),
    CruiseGroup("CRUISE_GROUP"),
    ;

    companion object {
        fun fromWire(value: String): ChatType? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class ChatSortField(val wireValue: String) {
    CreatedAt("createdAt"),
    UpdatedAt("updatedAt"),
    Name("name"),
}

/** Query parameters for `GET /v1/chats`. */
data class ChatListQuery(
    val type: ChatType? = null,
    val search: String? = null,
    val sort: ChatSortField = ChatSortField.UpdatedAt,
    val order: SortOrder = SortOrder.Desc,
    val limit: Int = 20,
    val offset: Int = 0,
)

data class ChatUser(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

data class ChatMessage(
    val id: String,
    val chatId: String,
    val text: String,
    val read: Boolean,
    val user: ChatUser,
    val createdAt: String,
    val updatedAt: String,
)

data class Chat(
    val id: String,
    val type: ChatType,
    val name: String? = null,
    val participants: List<ChatUser> = emptyList(),
    val lastMessage: ChatMessage? = null,
    val lastReadMessageId: String? = null,
    val relatedCruiseId: String? = null,
    val unreadCount: Int = 0,
    val updatedAt: String,
)

data class ChatsPage(
    val chats: List<Chat>,
    val total: Int,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean
        get() = offset + chats.size < total
}

data class MessagesPage(
    val messages: List<ChatMessage>,
    val total: Int,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean
        get() = offset + messages.size < total
}

@Serializable
data class CreateChatRequest(
    val participantIds: List<String>,
    val name: String? = null,
)

@Serializable
internal data class SendMessageRequest(
    val text: String,
)

@Serializable
internal data class MessageReadRequest(
    val read: Boolean,
)

enum class ChatBulkAction(val wireValue: String) {
    MarkRead("mark-read"),
    Delete("delete"),
}

@Serializable
internal data class ChatBulkActionRequest(
    val action: String,
    val chatIds: List<String>,
)

@Serializable
internal data class ChatUserDto(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
) {
    fun toDomain(): ChatUser = ChatUser(id = id, name = name, avatarUrl = avatarUrl)
}

@Serializable
internal data class ChatMessageDto(
    val id: String,
    val chatId: String,
    val text: String,
    val read: Boolean = false,
    val user: ChatUserDto,
    val createdAt: String,
    val updatedAt: String,
) {
    fun toDomain(): ChatMessage =
        ChatMessage(
            id = id,
            chatId = chatId,
            text = text,
            read = read,
            user = user.toDomain(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

@Serializable
internal data class ChatDto(
    val id: String,
    val type: String,
    val name: String? = null,
    val participants: List<ChatUserDto> = emptyList(),
    val lastMessage: ChatMessageDto? = null,
    val lastReadMessageId: String? = null,
    val relatedCruiseId: String? = null,
    val unreadCount: Int = 0,
    val updatedAt: String,
) {
    /** Chats with an unknown type are dropped rather than crash the list. */
    fun toDomain(): Chat? {
        val chatType = ChatType.fromWire(type) ?: return null
        return Chat(
            id = id,
            type = chatType,
            name = name,
            participants = participants.map { it.toDomain() },
            lastMessage = lastMessage?.toDomain(),
            lastReadMessageId = lastReadMessageId,
            relatedCruiseId = relatedCruiseId,
            unreadCount = unreadCount,
            updatedAt = updatedAt,
        )
    }
}

@Serializable
internal data class ChatsListDto(
    val chats: List<ChatDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): ChatsPage =
        ChatsPage(
            chats = chats.mapNotNull { it.toDomain() },
            total = total,
            limit = limit,
            offset = offset,
        )
}

@Serializable
internal data class MessagesListDto(
    val messages: List<ChatMessageDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): MessagesPage =
        MessagesPage(
            messages = messages.map { it.toDomain() },
            total = total,
            limit = limit,
            offset = offset,
        )
}

@Serializable
internal data class UnreadCountDto(
    val totalUnread: Int = 0,
)

/** Query parameters for `GET /v1/users` (participant picker). */
data class UserSearchQuery(
    val search: String? = null,
    val limit: Int = 20,
    val offset: Int = 0,
)

data class UsersPage(
    val users: List<ChatUser>,
    val total: Int,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean
        get() = offset + users.size < total
}

@Serializable
internal data class UsersListDto(
    val users: List<ChatUserDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): UsersPage =
        UsersPage(
            users = users.map { it.toDomain() },
            total = total,
            limit = limit,
            offset = offset,
        )
}
