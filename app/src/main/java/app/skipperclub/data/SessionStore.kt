package app.skipperclub.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.GeneralSecurityException
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val SESSION_DATASTORE_NAME = "auth_session"
private const val KEYSET_PREFS_NAME = "auth_crypto_keyset"
private const val KEYSET_NAME = "session_keyset"
private const val MASTER_KEY_URI = "android-keystore://skipperclub_auth_session_master_key"
private const val ASSOCIATED_DATA = "skipperclub-auth-session-v1"

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SESSION_DATASTORE_NAME,
)

object SessionStore {
    private val encryptedSessionKey = stringPreferencesKey("encrypted_session")
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val refreshMutex = Mutex()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var aead: Aead? = null

    @Volatile
    private var initialized = false

    private val _session = MutableStateFlow<SessionResponse?>(null)
    val session: StateFlow<SessionResponse?> = _session.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
        _isRestoring.value = true
        appScope.launch {
            restoreSession()
            _isRestoring.value = false
        }
    }

    suspend fun save(session: SessionResponse) {
        val storedSession = StoredSession.from(
            session = session,
            issuedAtEpochSeconds = nowEpochSeconds(),
        )
        writeStoredSession(storedSession)
        _session.value = session
    }

    suspend fun clear() {
        clearPersistedSession()
        _session.value = null
    }

    suspend fun validSession(): SessionResponse? = refreshMutex.withLock {
        val storedSession = readStoredSession() ?: run {
            _session.value = null
            return null
        }

        if (!SessionExpiryPolicy.shouldRefresh(
                accessTokenExpiresAtEpochSeconds = storedSession.accessTokenExpiresAtEpochSeconds,
                nowEpochSeconds = nowEpochSeconds(),
            )
        ) {
            return storedSession.toSessionResponse().also { _session.value = it }
        }

        refreshStoredSession(storedSession)
    }

    private suspend fun restoreSession() {
        runCatching { validSession() }
            .onFailure { _session.value = null }
    }

    private suspend fun refreshStoredSession(storedSession: StoredSession): SessionResponse? {
        return try {
            val refreshResponse = AuthApi.refreshSession(
                sessionId = storedSession.id,
                refreshToken = storedSession.refreshToken,
            )
            val refreshedSession = SessionResponse(
                id = storedSession.id,
                accessToken = refreshResponse.accessToken,
                refreshToken = refreshResponse.refreshToken,
                expiresIn = refreshResponse.expiresIn,
                user = storedSession.user,
            )
            save(refreshedSession)
            refreshedSession
        } catch (error: AuthError) {
            if (error.shouldClearPersistedSession()) {
                clear()
            }
            null
        }
    }

    private suspend fun writeStoredSession(storedSession: StoredSession) {
        val payload = json.encodeToString(storedSession).toByteArray(Charsets.UTF_8)
        val encryptedPayload = crypto().encrypt(
            payload,
            ASSOCIATED_DATA.toByteArray(Charsets.UTF_8),
        )
        val encodedPayload = Base64.getEncoder().encodeToString(encryptedPayload)
        dataStore().edit { preferences ->
            preferences[encryptedSessionKey] = encodedPayload
        }
    }

    private suspend fun readStoredSession(): StoredSession? {
        val encodedPayload = dataStore().data.first()[encryptedSessionKey] ?: return null
        return try {
            val encryptedPayload = Base64.getDecoder().decode(encodedPayload)
            val payload = crypto().decrypt(
                encryptedPayload,
                ASSOCIATED_DATA.toByteArray(Charsets.UTF_8),
            )
            json.decodeFromString<StoredSession>(payload.toString(Charsets.UTF_8))
        } catch (error: IllegalArgumentException) {
            clearPersistedSession()
            null
        } catch (error: GeneralSecurityException) {
            clearPersistedSession()
            null
        } catch (error: SerializationException) {
            clearPersistedSession()
            null
        }
    }

    private suspend fun clearPersistedSession() {
        dataStore().edit { preferences ->
            preferences.remove(encryptedSessionKey)
        }
    }

    private fun dataStore(): DataStore<Preferences> =
        requireContext().sessionDataStore

    private fun crypto(): Aead {
        aead?.let { return it }
        return synchronized(this) {
            aead ?: buildAead(requireContext()).also { aead = it }
        }
    }

    private fun requireContext(): Context =
        checkNotNull(appContext) { "SessionStore.initialize(context) must be called first" }

    private fun buildAead(context: Context): Aead {
        AeadConfig.register()
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_NAME)
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun AuthError.shouldClearPersistedSession(): Boolean =
        this is AuthError.InvalidRefreshToken ||
            this is AuthError.RefreshTokenExpired ||
            this is AuthError.AuthenticationRequired ||
            this is AuthError.Validation
}

internal object SessionExpiryPolicy {
    private const val REFRESH_SKEW_SECONDS = 60L

    fun expiresAtEpochSeconds(issuedAtEpochSeconds: Long, expiresInSeconds: Long): Long =
        issuedAtEpochSeconds + expiresInSeconds

    fun shouldRefresh(accessTokenExpiresAtEpochSeconds: Long, nowEpochSeconds: Long): Boolean =
        accessTokenExpiresAtEpochSeconds <= nowEpochSeconds + REFRESH_SKEW_SECONDS
}

@Serializable
private data class StoredSession(
    val id: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val accessTokenExpiresAtEpochSeconds: Long,
    val user: SessionUser,
) {
    fun toSessionResponse(): SessionResponse =
        SessionResponse(
            id = id,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            user = user,
        )

    companion object {
        fun from(session: SessionResponse, issuedAtEpochSeconds: Long): StoredSession =
            StoredSession(
                id = session.id,
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresIn = session.expiresIn,
                accessTokenExpiresAtEpochSeconds = SessionExpiryPolicy.expiresAtEpochSeconds(
                    issuedAtEpochSeconds = issuedAtEpochSeconds,
                    expiresInSeconds = session.expiresIn,
                ),
                user = session.user,
            )
    }
}

private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1_000L
