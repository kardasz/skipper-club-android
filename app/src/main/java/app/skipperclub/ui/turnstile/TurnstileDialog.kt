package app.skipperclub.ui.turnstile

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipperclub.BuildConfig
import app.skipperclub.R
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class TurnstileMessage(
    val event: String,
    val token: String? = null,
    val code: String? = null,
)

@Composable
fun TurnstileDialog(
    action: String,
    onSuccess: (token: String) -> Unit,
    onError: (code: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val onSuccessState by rememberUpdatedState(onSuccess)
    val onErrorState by rememberUpdatedState(onError)
    val parser = remember { Json { ignoreUnknownKeys = true } }
    var loading by remember { mutableStateOf(value = true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(min = 220.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.turnstile_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.turnstile_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 110.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) {
                        CircularProgressIndicator()
                    }
                    TurnstileWebView(action = action, onLoaded = { loading = false }) { raw ->
                        val message = runCatching {
                            parser.decodeFromString<TurnstileMessage>(raw)
                        }.getOrNull() ?: return@TurnstileWebView
                        when (message.event) {
                            "turnstile-success" -> message.token?.let(onSuccessState)
                            "turnstile-error" -> onErrorState(message.code)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TurnstileWebView(
    action: String,
    onLoaded: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val onMessageState by rememberUpdatedState(onMessage)
    val onLoadedState by rememberUpdatedState(onLoaded)

    val webView = remember {
        object {
            var instance: WebView? = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.instance?.apply {
                stopLoading()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
            webView.instance = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(0)

                addJavascriptInterface(
                    object {
                        @Suppress("unused")
                        @JavascriptInterface
                        fun onTurnstileMessage(payload: String) {
                            mainHandler.post { onMessageState(payload) }
                        }
                    },
                    JS_INTERFACE_NAME,
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view.evaluateJavascript(BRIDGE_SCRIPT, null)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(BRIDGE_SCRIPT, null)
                        mainHandler.post { onLoadedState() }
                    }
                }

                webView.instance = this
                loadUrl(buildTurnstileUrl(action))
            }
        },
    )
}

private const val JS_INTERFACE_NAME = "SkipperBridge"

private val BRIDGE_SCRIPT = """
    (function() {
      if (window.__skipperBridgeInstalled) return;
      window.__skipperBridgeInstalled = true;
      var send = function(data) {
        try {
          var payload = typeof data === 'string' ? data : JSON.stringify(data);
          window.$JS_INTERFACE_NAME && window.$JS_INTERFACE_NAME.onTurnstileMessage(payload);
        } catch (e) {}
      };
      window.postMessage = function(data) { send(data); };
      try {
        Object.defineProperty(window, 'parent', {
          configurable: true,
          get: function() {
            return { postMessage: function(data) { send(data); } };
          }
        });
      } catch (e) {}
    })();
""".trimIndent()

private fun buildTurnstileUrl(action: String): String {
    val language = Locale.getDefault().language.ifBlank { "auto" }
    return BuildConfig.TURNSTILE_URL +
        "?theme=auto" +
        "&language=$language" +
        "&action=$action"
}
