package app.skipperclub.data

import okhttp3.OkHttpClient

/** Release variant — no-op; see the debug source set for the actual logger. */
internal object HttpLoggingProvider {
    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder
}
