package app.skipperclub.ui.main.friends

import app.skipperclub.data.FriendListQuery
import app.skipperclub.data.FriendRequest
import app.skipperclub.data.FriendRequestListQuery
import app.skipperclub.data.FriendRequestState
import app.skipperclub.data.FriendRequestsPage
import app.skipperclub.data.FriendUser
import app.skipperclub.data.FriendsError
import app.skipperclub.data.FriendsPage

internal fun testFriendUser(id: String, name: String = "User $id") =
    FriendUser(id = id, name = name, avatarUrl = null)

internal fun testRequest(
    id: String,
    state: FriendRequestState,
    user: FriendUser = testFriendUser("user-$id"),
) = FriendRequest(
    id = id,
    user = user,
    state = state,
    createdAt = "2026-06-13T09:00:00Z",
    updatedAt = "2026-06-13T09:00:00Z",
)

internal fun requestsPage(requests: List<FriendRequest>, total: Int = requests.size, offset: Int = 0) =
    FriendRequestsPage(requests = requests, total = total, limit = 50, offset = offset)

internal fun friendsPage(friends: List<FriendUser>, total: Int = friends.size, offset: Int = 0) =
    FriendsPage(friends = friends, total = total, limit = 20, offset = offset)

/** Configurable in-memory [FriendsGateway]; records calls for assertions. */
internal class FakeFriendsGateway : FriendsGateway {
    var receivedRequests: FriendRequestsPage = requestsPage(emptyList())
    var sentRequests: FriendRequestsPage = requestsPage(emptyList())

    /** Friends pages returned in order across successive `listFriends` calls. */
    var friendsPages: List<FriendsPage> = listOf(friendsPage(emptyList()))
    private var friendsCallIndex = 0

    var searchPage: FriendsPage = friendsPage(emptyList())

    var listError: FriendsError? = null
    var mutationError: FriendsError? = null
    var sendError: FriendsError? = null
    var searchError: FriendsError? = null

    val calls = mutableListOf<String>()
    val searchQueries = mutableListOf<FriendListQuery>()
    val friendsQueries = mutableListOf<FriendListQuery>()

    override suspend fun listFriendRequests(
        accessToken: String,
        query: FriendRequestListQuery,
    ): FriendRequestsPage {
        calls += "listRequests:${query.state?.wireValue}"
        listError?.let { throw it }
        return when (query.state) {
            FriendRequestState.Sent -> sentRequests
            else -> receivedRequests
        }
    }

    override suspend fun sendFriendRequest(accessToken: String, userId: String): FriendRequest {
        calls += "send:$userId"
        sendError?.let { throw it }
        return testRequest("req-$userId", FriendRequestState.Sent, testFriendUser(userId))
    }

    override suspend fun updateFriendRequest(
        accessToken: String,
        requestId: String,
        state: FriendRequestState,
    ): FriendRequest {
        calls += "update:$requestId:${state.wireValue}"
        mutationError?.let { throw it }
        return testRequest(requestId, state)
    }

    override suspend fun cancelFriendRequest(accessToken: String, requestId: String) {
        calls += "cancel:$requestId"
        mutationError?.let { throw it }
    }

    override suspend fun listFriends(accessToken: String, query: FriendListQuery): FriendsPage {
        calls += "listFriends:${query.offset}"
        friendsQueries += query
        listError?.let { throw it }
        val page = friendsPages[minOf(friendsCallIndex, friendsPages.lastIndex)]
        friendsCallIndex++
        return page
    }

    override suspend fun removeFriend(accessToken: String, friendId: String) {
        calls += "removeFriend:$friendId"
        mutationError?.let { throw it }
    }

    override suspend fun searchUsers(accessToken: String, query: FriendListQuery): FriendsPage {
        calls += "search:${query.search}"
        searchQueries += query
        searchError?.let { throw it }
        return searchPage
    }
}
