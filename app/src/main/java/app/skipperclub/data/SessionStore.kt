package app.skipperclub.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionStore {
    private val _session = MutableStateFlow<SessionResponse?>(null)
    val session: StateFlow<SessionResponse?> = _session.asStateFlow()

    fun save(session: SessionResponse) {
        _session.value = session
    }

    fun clear() {
        _session.value = null
    }
}
