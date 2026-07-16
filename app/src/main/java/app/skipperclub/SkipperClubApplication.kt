package app.skipperclub

import android.app.Application
import app.skipperclub.data.PresenceStore
import app.skipperclub.data.RealtimeConnectionManager
import app.skipperclub.data.SessionStore
import app.skipperclub.data.UnreadMessagesStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder

/**
 * Provides a process-wide Coil [ImageLoader] that can decode a poster frame from
 * remote videos (`media.type == "video"`) in addition to images, so the posts
 * feed and the create-post wizard render real video thumbnails instead of a
 * blank box. The OkHttp network fetcher is registered explicitly because the
 * default singleton loader is replaced here.
 */
class SkipperClubApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Initialize here (idempotent) so the app-scoped realtime owner can read session state and
        // refresh tokens before any Activity starts.
        SessionStore.initialize(this)
        RealtimeConnectionManager.start(
            sessionFlow = SessionStore.session,
            accessTokenProvider = { SessionStore.validSession()?.accessToken },
            onAuthClose = { SessionStore.forceRefresh() },
        )
        // App-wide unread badge, driven by the same app-scoped socket so it updates outside the
        // Messages tab (the tab-scoped controller cannot be the source).
        UnreadMessagesStore.start(
            sessionFlow = SessionStore.session,
            accessTokenProvider = { SessionStore.validSession()?.accessToken },
        )
        // App-wide online/offline cache, driven by the same app-scoped socket so presence updates
        // outside the Messages tab too (e.g. while the conversation dialog is not composed).
        PresenceStore.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
