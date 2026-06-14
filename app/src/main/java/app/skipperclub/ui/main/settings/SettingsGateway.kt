package app.skipperclub.ui.main.settings

import app.skipperclub.data.NotificationSettings
import app.skipperclub.data.SettingsApi

/**
 * Seam between the settings UI controller and [SettingsApi] so the state-machine
 * logic stays unit-testable with fakes (no MockWebServer needed at this layer).
 */
interface SettingsGateway {
    suspend fun getNotificationSettings(accessToken: String): NotificationSettings

    suspend fun updateNotificationSettings(
        accessToken: String,
        settings: NotificationSettings,
    ): NotificationSettings
}

object RealSettingsGateway : SettingsGateway {
    override suspend fun getNotificationSettings(accessToken: String): NotificationSettings =
        SettingsApi.getNotificationSettings(accessToken)

    override suspend fun updateNotificationSettings(
        accessToken: String,
        settings: NotificationSettings,
    ): NotificationSettings = SettingsApi.updateNotificationSettings(accessToken, settings)
}
