package app.skipperclub.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The connection is held only while the app is both foregrounded and logged in. */
internal fun shouldHoldConnection(isForeground: Boolean, isAuthenticated: Boolean): Boolean =
    isForeground && isAuthenticated

/**
 * Owns the single app-wide realtime connection ([WebSocketChatRealtimeClient]), independent of the
 * Messages tab. It connects when the user is authenticated **and** the app is foregrounded and
 * disconnects on background or logout, so `message:received` and `notification:new` keep flowing
 * everywhere in the app (see the target connection model in the socket.io→WebSocket migration
 * guide, step 5). Started once from [app.skipperclub.SkipperClubApplication].
 */
object RealtimeConnectionManager : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var realtime: ChatRealtimeClient
    private var accessTokenProvider: suspend () -> String? = { null }
    private var onAuthClose: suspend () -> Unit = {}

    @Volatile
    private var isForeground = false

    @Volatile
    private var isAuthenticated = false

    @Volatile
    private var started = false

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * @param context used to look up [ConnectivityManager] for the network-return fast path.
     * @param sessionFlow authentication signal — a non-null value means logged in.
     * @param accessTokenProvider fresh token per (re)connect (e.g. [SessionStore.validSession]).
     * @param onAuthClose forced refresh on a `1008`/`4401` close (e.g. [SessionStore.forceRefresh]).
     */
    fun start(
        context: Context,
        realtime: ChatRealtimeClient = WebSocketChatRealtimeClient,
        sessionFlow: StateFlow<SessionResponse?>,
        accessTokenProvider: suspend () -> String?,
        onAuthClose: suspend () -> Unit,
    ) {
        if (started) return
        started = true
        this.realtime = realtime
        this.accessTokenProvider = accessTokenProvider
        this.onAuthClose = onAuthClose
        connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            sessionFlow.collect { session ->
                isAuthenticated = session != null
                reconcile()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        isForeground = true
        reconcile()
    }

    override fun onStop(owner: LifecycleOwner) {
        isForeground = false
        reconcile()
    }

    @Synchronized
    private fun reconcile() {
        if (shouldHoldConnection(isForeground, isAuthenticated)) {
            // connect() is idempotent (guards on an existing scope).
            realtime.connect(accessTokenProvider, onAuthClose)
            registerNetworkCallback()
        } else {
            unregisterNetworkCallback()
            realtime.disconnect()
        }
    }

    /**
     * While the client is meant to be connected, listen for connectivity returning and cut a
     * pending reconnect backoff short ([ChatRealtimeClient.onNetworkAvailable]) — otherwise a drop
     * near the 30s backoff cap leaves realtime dead for up to half a minute after the network is
     * already back. Registered/unregistered from [reconcile] so the callback is only alive while a
     * connection is held (requires `ACCESS_NETWORK_STATE`). Idempotent via [networkCallback].
     */
    private fun registerNetworkCallback() {
        val manager = connectivityManager ?: return
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                realtime.onNetworkAvailable()
            }
        }
        // registerDefaultNetworkCallback can throw (e.g. TooManyRequestsException); losing the
        // fast path degrades to the plain bounded backoff, so never let it crash the app.
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
    }
}
