package app.skipperclub.ui.main.friends

import app.skipperclub.data.FriendListQuery
import app.skipperclub.data.FriendRequest
import app.skipperclub.data.FriendRequestListQuery
import app.skipperclub.data.FriendRequestState
import app.skipperclub.data.FriendRequestsPage
import app.skipperclub.data.FriendsApi
import app.skipperclub.data.FriendsPage

/**
 * Seam between the friends UI controllers and [FriendsApi] so the state-machine
 * logic stays unit-testable with fakes (no MockWebServer needed at this layer).
 */
interface FriendsGateway {
    suspend fun listFriendRequests(accessToken: String, query: FriendRequestListQuery): FriendRequestsPage
    suspend fun sendFriendRequest(accessToken: String, userId: String): FriendRequest
    suspend fun updateFriendRequest(accessToken: String, requestId: String, state: FriendRequestState): FriendRequest
    suspend fun cancelFriendRequest(accessToken: String, requestId: String)
    suspend fun listFriends(accessToken: String, query: FriendListQuery): FriendsPage
    suspend fun removeFriend(accessToken: String, friendId: String)
    suspend fun searchUsers(accessToken: String, query: FriendListQuery): FriendsPage
}

object RealFriendsGateway : FriendsGateway {
    override suspend fun listFriendRequests(accessToken: String, query: FriendRequestListQuery): FriendRequestsPage =
        FriendsApi.listFriendRequests(accessToken, query)

    override suspend fun sendFriendRequest(accessToken: String, userId: String): FriendRequest =
        FriendsApi.sendFriendRequest(accessToken, userId)

    override suspend fun updateFriendRequest(
        accessToken: String,
        requestId: String,
        state: FriendRequestState,
    ): FriendRequest = FriendsApi.updateFriendRequest(accessToken, requestId, state)

    override suspend fun cancelFriendRequest(accessToken: String, requestId: String) =
        FriendsApi.cancelFriendRequest(accessToken, requestId)

    override suspend fun listFriends(accessToken: String, query: FriendListQuery): FriendsPage =
        FriendsApi.listFriends(accessToken, query)

    override suspend fun removeFriend(accessToken: String, friendId: String) =
        FriendsApi.removeFriend(accessToken, friendId)

    override suspend fun searchUsers(accessToken: String, query: FriendListQuery): FriendsPage =
        FriendsApi.searchUsers(accessToken, query)
}
