package app.skipperclub.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionUserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roleDefaultsToStandardUserWhenAbsent() {
        val user = json.decodeFromString<SessionUser>(
            """{"id":"u1","email":"a@example.com","name":"Anna"}""",
        )

        assertFalse(user.isAdmin)
    }

    @Test
    fun adminRoleParsesToIsAdmin() {
        val user = json.decodeFromString<SessionUser>(
            """{"id":"u1","email":"a@example.com","name":"Anna","role":"admin"}""",
        )

        assertTrue(user.isAdmin)
    }

    @Test
    fun standardUserRoleIsNotAdmin() {
        val user = json.decodeFromString<SessionUser>(
            """{"id":"u1","email":"a@example.com","name":"Anna","role":"user"}""",
        )

        assertFalse(user.isAdmin)
    }
}
