package io.github.nevaseme.plandmk1.tsunami

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.serialization.json.*

/** Only bundled, explicitly allowlisted assets can reach this bridge. No visible Web UI. */
@SuppressLint("SetJavaScriptEnabled")
class TsunamiComputer(
    context: Context,
    private val onEvent: (Event) -> Unit,
    private val openAsset: (String) -> InputStream = { context.assets.open(it) },
) : AutoCloseable {
    sealed interface Event {
        data object Ready : Event
        data class Progress(val message: String) : Event
        data class Result(val value: TsunamiComputedResult) : Event
        data class Error(val message: String) : Event
    }
    private val handler = Handler(Looper.getMainLooper())
    private var disposed = false
    private var ready = false
    private var requestId = 0L
    private var requestedHeight: Double? = null
    private val timeout = Runnable { if (!disposed && !ready) onEvent(Event.Error("結果の読込を準備できませんでした。津波表示を開き直してください")) }
    private val webView = WebView(context)

    init {
        check(Looper.myLooper() == Looper.getMainLooper())
        webView.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            domStorageEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse = resource(request.url)
        }
        webView.addJavascriptInterface(object {
            @JavascriptInterface fun postMessage(payload: String) {
                // JavascriptInterface runs on WebView's bridge thread: parse and color
                // potentially large polygon results here, before returning to Compose.
                val parsed = parseMessage(payload)
                handler.post { if (!disposed) receive(parsed.first, parsed.second) }
            }
        }, "TsunamiNative")
        handler.postDelayed(timeout, 15_000)
        webView.loadUrl("$ORIGIN/tsunami-runtime/runner.html")
    }

    private fun resource(uri: Uri): WebResourceResponse {
        val name = uri.path?.removePrefix("/tsunami-runtime/")
        if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net" || uri.port != -1 ||
            uri.path != "/tsunami-runtime/$name" || name !in ASSETS || uri.query != null) return denied()
        return try {
            val mime = when { name!!.endsWith(".mjs") -> "text/javascript"; name.endsWith(".json") -> "application/json"; else -> "text/html" }
            WebResourceResponse(mime, "UTF-8", openAsset("tsunami-runtime/$name"))
        } catch (_: Exception) { denied() }
    }

    private fun denied() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))

    private fun parseMessage(payload: String): Pair<Long?, Event> {
        var id: Long? = null
        return try {
            val message = Json.parseToJsonElement(payload).jsonObject
            id = message["id"]?.jsonPrimitive?.longOrNull
            val event = when (message["type"]?.jsonPrimitive?.content) {
                "ready" -> Event.Ready
                "progress" -> Event.Progress(message["message"]?.jsonPrimitive?.content ?: "計算済みの結果を読み込んでいます")
                "result" -> {
                    val json = message.getValue("result").jsonObject
                    Event.Result(TsunamiComputedResult.parse(json, json.getValue("heightM").jsonPrimitive.double))
                }
                "error" -> Event.Error(message["message"]?.jsonPrimitive?.content ?: "計算済みの結果を読み込めませんでした")
                else -> error("Unknown computation event")
            }
            id to event
        } catch (_: Exception) {
            id to Event.Error("計算結果を確認できませんでした。結果は表示していません")
        }
    }

    private fun receive(id: Long?, event: Event) {
        if (event == Event.Ready) {
            ready = true; handler.removeCallbacks(timeout); onEvent(event); return
        }
        if (requestedHeight == null || id != requestId) return
        if (event is Event.Result && event.value.height != requestedHeight) return
        if (event is Event.Result || event is Event.Error) requestedHeight = null
        onEvent(event)
    }

    fun compute(height: Double) {
        check(!disposed && ready)
        require(TsunamiPrecomputedHeight.isSupported(height)) { "津波高は0〜35mの範囲で、1m刻みで入力してください" }
        requestId++; requestedHeight = height
        webView.evaluateJavascript("window.computeTsunami($requestId,$height)", null)
    }

    fun cancel() {
        requestId++; requestedHeight = null
        if (!disposed && ready) webView.evaluateJavascript("window.cancelTsunami()", null)
    }

    override fun close() {
        if (disposed) return
        cancel(); disposed = true; handler.removeCallbacks(timeout)
        webView.removeJavascriptInterface("TsunamiNative")
        webView.stopLoading(); webView.destroy()
    }

    companion object {
        private const val ORIGIN = "https://appassets.androidplatform.net"
        private val ASSETS = setOf("runner.html", "runner.mjs", "tsunami-precomputed.mjs", "tsunami-precomputed-atami.json")
    }
}
