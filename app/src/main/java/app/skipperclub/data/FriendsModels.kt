package app.skipperclub.data

import kotlinx.serialization.Serializable

/**
 * Friend-request lifecycle state (`docs/api/friends` → Friend Request States).
 * `Pending` is a request received by the current user (awaiting their response),
 * `Sent` is one the current user sent (awaiting the recipient). [Unknown] is a
 * forward-compatible fallback so an unexpected server value never drops the row.
 */
enum class FriendRequestState(val wireValue: String) {
    Pending("pending"),
    Sent("sent"),
    Accepted("accepted"),
    Rejected("rejected"),
    Canceled("canceled"),
    Unknown(""),
    ;

    companion object {
        fun fromWire(value: String?): FriendRequestState =
            value?.let { wire -> entries.firstOrNull { it.wireValue == wire } } ?: Unknown
    }
}

/** A community member as rendered in friend lists, requests and search results. */
data class FriendUser(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
)

/**
 * A friend request involving the current user and [user] (the other party — the
 * sender when [state] is `Pending`, the recipient when `Sent`).
 */
data class FriendRequest(
    val id: String,
    val user: FriendUser,
    val state: FriendRequestState,
    val createdAt: String,
    val updatedAt: String,
)

/** Query parameters for `GET /v1/friend-requests`. */
data class FriendRequestListQuery(
    val state: FriendRequestState? = null,
    val limit: Int = 50,
    val offset: Int = 0,
)

/** Query parameters for `GET /v1/friends`. */
data class FriendListQuery(
    val search: String? = null,
    val limit: Int = 20,
    val offset: Int = 0,
)

data class FriendRequestsPage(
    val requests: List<FriendRequest>,
    val total: Int,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean get() = offset + requests.size < total
}

data class FriendsPage(
    val friends: List<FriendUser>,
    val total: Int,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean get() = offset + friends.size < total
}

// --- DTOs (wire shapes) ---

@Serializable
internal data class FriendUserDto(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
) {
    fun toDomain(): FriendUser = FriendUser(id = id, name = name, avatarUrl = avatarUrl)
}

@Serializable
internal data class FriendRequestDto(
    val id: String,
    val user: FriendUserDto,
    val state: String,
    val createdAt: String,
    val updatedAt: String,
) {
    fun toDomain(): FriendRequest = FriendRequest(
        id = id,
        user = user.toDomain(),
        state = FriendRequestState.fromWire(state),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

@Serializable
internal data class FriendRequestsListDto(
    val requests: List<FriendRequestDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): FriendRequestsPage = FriendRequestsPage(
        requests = requests.map { it.toDomain() },
        total = total,
        limit = limit,
        offset = offset,
    )
}

@Serializable
internal data class FriendsListDto(
    val friends: List<FriendUserDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): FriendsPage = FriendsPage(
        friends = friends.map { it.toDomain() },
        total = total,
        limit = limit,
        offset = offset,
    )
}

@Serializable
internal data class FriendUserSearchListDto(
    val users: List<FriendUserDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): FriendsPage = FriendsPage(
        friends = users.map { it.toDomain() },
        total = total,
        limit = limit,
        offset = offset,
    )
}

@Serializable
internal data class SendFriendRequestBody(val userId: String)

@Serializable
internal data class UpdateFriendRequestBody(val state: String)
