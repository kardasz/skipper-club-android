package app.skipperclub.ui.main.settings

import app.skipperclub.data.NotificationSettings
import app.skipperclub.data.SettingsError

/** Configurable in-memory [SettingsGateway]; records calls for assertions. */
internal class FakeSettingsGateway : SettingsGateway {
    var settings: NotificationSettings = NotificationSettings(
        emailNotificationsEnabled = true,
        pushNotificationsEnabled = true,
    )
    var getError: SettingsError? = null
    var updateError: SettingsError? = null

    var getCalls = 0
    var updateCalls = 0
    var lastUpdate: NotificationSettings? = null

    override suspend fun getNotificationSettings(accessToken: String): NotificationSettings {
        getCalls++
        getError?.let { throw it }
        return settings
    }

    override suspend fun updateNotificationSettings(
        accessToken: String,
        settings: NotificationSettings,
    ): NotificationSettings {
        updateCalls++
        lastUpdate = settings
        updateError?.let { throw it }
        this.settings = settings
        return settings
    }
}
