package app.skipperclub.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatsModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun chatsListDecodesPerOpenApiSchema() {
        val payload = """
            {
              "chats": [
                {
                  "id": "chat-1",
                  "type": "ONE_TO_ONE",
                  "name": null,
                  "participants": [
                    {"id": "u1", "name": "Jan Kowalski", "avatarUrl": "https://cdn/jan.jpg"},
                    {"id": "u2", "name": "Anna Nowak", "avatarUrl": null}
                  ],
                  "lastMessage": {
                    "id": "m1",
                    "chatId": "chat-1",
                    "text": "See you at the marina!",
                    "read": true,
                    "createdAt": "2025-11-23T14:30:00Z",
                    "updatedAt": "2025-11-23T14:30:00Z",
                    "user": {"id": "u1", "name": "Jan Kowalski", "avatarUrl": null}
                  },
                  "lastReadMessageId": "m1",
                  "relatedCruiseId": null,
                  "unreadCount": 2,
                  "updatedAt": "2025-11-23T14:30:00Z"
                }
              ],
              "total": 5,
              "limit": 20,
              "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<ChatsListDto>(payload).toDomain()

        val chat = page.chats.single()
        assertEquals("chat-1", chat.id)
        assertEquals(ChatType.OneToOne, chat.type)
        assertNull(chat.name)
        assertEquals(listOf("u1", "u2"), chat.participants.map { it.id })
        assertEquals("See you at the marina!", chat.lastMessage?.text)
        assertEquals("m1", chat.lastReadMessageId)
        assertEquals(2, chat.unreadCount)
        assertEquals(5, page.total)
        assertTrue(page.hasMore)
    }

    @Test
    fun chatsWithUnknownTypeAreDropped() {
        val payload = """
            {
              "chats": [
                {"id": "c1", "type": "ONE_TO_ONE", "updatedAt": "2025-11-23T14:30:00Z"},
                {"id": "c2", "type": "SOMETHING_NEW", "updatedAt": "2025-11-23T14:30:00Z"}
              ],
              "total": 2,
              "limit": 20,
              "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<ChatsListDto>(payload).toDomain()

        assertEquals(listOf("c1"), page.chats.map { it.id })
    }

    @Test
    fun messagesListDecodesAndComputesHasMore() {
        val payload = """
            {
              "messages": [
                {
                  "id": "m1",
                  "chatId": "chat-1",
                  "text": "Hello",
                  "read": false,
                  "createdAt": "2025-11-23T14:30:00Z",
                  "updatedAt": "2025-11-23T14:30:00Z",
                  "user": {"id": "u1", "name": "Jan"}
                }
              ],
              "total": 1,
              "limit": 20,
              "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<MessagesListDto>(payload).toDomain()

        assertEquals("Hello", page.messages.single().text)
        assertFalse(page.messages.single().read)
        assertFalse(page.hasMore)
    }

    @Test
    fun hasMoreAccountsForOffset() {
        val page = ChatsPage(
            chats = listOf(),
            total = 40,
            limit = 20,
            offset = 40,
        )

        assertFalse(page.hasMore)
        assertTrue(page.copy(offset = 20).hasMore)
    }

    @Test
    fun chatTypeWireValuesRoundTrip() {
        ChatType.entries.forEach { type ->
            assertEquals(type, ChatType.fromWire(type.wireValue))
        }
        assertNull(ChatType.fromWire("UNKNOWN"))
    }

    @Test
    fun usersListDecodes() {
        val payload = """
            {
              "users": [{"id": "u1", "name": "Jan", "avatarUrl": null}],
              "total": 30,
              "limit": 20,
              "offset": 0
            }
        """.trimIndent()

        val page = json.decodeFromString<UsersListDto>(payload).toDomain()

        assertEquals("Jan", page.users.single().name)
        assertTrue(page.hasMore)
    }
}
