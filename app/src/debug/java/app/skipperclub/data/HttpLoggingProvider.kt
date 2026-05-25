package app.skipperclub.data

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug variant — wires an OkHttp logging interceptor that prints full request /
 * response bodies under the `SkipperHttp` logcat tag. Secrets (`Authorization`,
 * `X-Turnstile-Token`) are redacted.
 *
 * The release variant of this file is a no-op, so neither the interceptor nor the
 * `logging-interceptor` artifact ship to production.
 */
internal object HttpLoggingProvider {
    private const val TAG = "SkipperHttp"

    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val interceptor = HttpLoggingInterceptor { message -> Log.d(TAG, message) }.apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
            redactHeader("X-Turnstile-Token")
        }
        return builder.addInterceptor(interceptor)
    }
}
