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

/**
 * Query parameters for `GET /v1/chats`.
 *
 * Paging is keyset-only: [cursor] is the opaque `nextCursor` of a previous response, passed back
 * verbatim (never constructed client-side). The server accepts it only with the default
 * `sort=updatedAt` + `order=desc` and rejects any combination with `offset` — which is deprecated
 * server-side and no longer sent by this client at all (docs/api/messages/chats.md, "Paging modes").
 */
data class ChatListQuery(
    val type: ChatType? = null,
    val search: String? = null,
    val sort: ChatSortField = ChatSortField.UpdatedAt,
    val order: SortOrder = SortOrder.Desc,
    val limit: Int = 20,
    val cursor: String? = null,
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
    /**
     * Client-generated idempotency key echoed by the backend (v1.5.0+) in message payloads
     * (`message:sent`/`message:new`/`message:received` and the REST send response). Used as an
     * additional dedup key when reconciling realtime arrivals with REST-sent messages; null for
     * messages sent by clients that did not provide one.
     */
    val clientMessageId: String? = null,
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
    /**
     * Opaque keyset cursor built from the last chat of this page: pass it back as
     * [ChatListQuery.cursor] to fetch the next page. `null` on the last page — which is exactly
     * how [hasMore] is derived, never from an offset/total count (parity with [MessagesPage] and
     * the messages migration).
     */
    val nextCursor: String? = null,
) {
    val hasMore: Boolean
        get() = nextCursor != null
}

data class MessagesPage(
    val messages: List<ChatMessage>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    /**
     * Opaque keyset cursor built from the last message of this page: pass it back as
     * `before` to fetch the next older page. `null` on the last page — which is exactly
     * how [hasMore] is derived, never from a post-dedupe count
     * (task_shared_catchup_contract.md §3.1).
     */
    val nextCursor: String? = null,
) {
    val hasMore: Boolean
        get() = nextCursor != null
}

@Serializable
data class CreateChatRequest(
    val participantIds: List<String>,
    val name: String? = null,
)

@Serializable
internal data class SendMessageRequest(
    val text: String,
    /**
     * Client-generated idempotency key (UUID, any version): resending the same value makes the
     * server return the already-created message instead of a duplicate, so an HTTP-level retry
     * after a timeout cannot double-post.
     */
    val clientMessageId: String? = null,
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
    /** Echoed idempotency key (backend v1.5.0+); absent for messages sent without one. */
    val clientMessageId: String? = null,
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
            clientMessageId = clientMessageId,
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
    val nextCursor: String? = null,
) {
    fun toDomain(): ChatsPage =
        ChatsPage(
            chats = chats.mapNotNull { it.toDomain() },
            total = total,
            limit = limit,
            offset = offset,
            nextCursor = nextCursor,
        )
}

@Serializable
internal data class MessagesListDto(
    val messages: List<ChatMessageDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val nextCursor: String? = null,
) {
    fun toDomain(): MessagesPage =
        MessagesPage(
            messages = messages.map { it.toDomain() },
            total = total,
            limit = limit,
            offset = offset,
            nextCursor = nextCursor,
        )
}

@Serializable
internal data class UnreadCountDto(
    val totalUnread: Int = 0,
)

@Serializable
internal data class ChatPresenceEntryDto(
    val userId: String,
    val isOnline: Boolean = false,
    /** Null when the user has never cleanly disconnected since the field shipped; ignored while online. */
    val lastSeen: String? = null,
) {
    fun toDomain(): UserPresence = UserPresence(isOnline = isOnline, lastSeen = lastSeen)
}

/** Response of `GET /v1/chats/presence`: the online state of the caller's chat co-participants. */
@Serializable
internal data class ChatPresenceDto(
    val items: List<ChatPresenceEntryDto> = emptyList(),
) {
    /** Keyed by userId, matching [PresenceStore]'s in-memory shape. */
    fun toDomain(): Map<String, UserPresence> = items.associate { it.userId to it.toDomain() }
}

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
