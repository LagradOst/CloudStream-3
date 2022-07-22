package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.net.http.SslError
import android.view.View
import android.webkit.*
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.nicehttp.requestCreator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URI
import kotlin.collections.ArrayList


enum class WebViewActions {
    WAIT_FOR_PAGE_LOAD,
    WAIT_FOR_X_SECONDS,
    WAIT_FOR_NETWORK_CALL,
    WAIT_FOR_NETWORK_IDLE,
    WAIT_FOR_ELEMENT,
    WAIT_FOR_ELEMENT_GONE,
    EXECUTE_JAVASCRIPT,
    WAIT_FOR_ELEMENT_TO_BE_CLICKABLE,
    RETURN
}

data class WebViewAction(val actionType: WebViewActions, val parameter: Any = "", val callback: (AdvancedWebView) -> Unit? = {  })

class AdvancedWebView private constructor(
    val url: String,
    val actions: ArrayList<WebViewAction>,
    val referer: String?,
    val method: String,
    val callback: (AdvancedWebView) -> Unit = {  }
) {
    val headers = mapOf<String, String>()
    var webView: WebView? = null
    val remainingActions: ArrayList<WebViewAction> = actions
    var currentHTML: String = ""
    val document by lazy { Jsoup.parse(currentHTML) }
    val Instance = this

    data class Builder(
        var url: String = "",
        var actions: ArrayList<WebViewAction> = arrayListOf(),
        var referer: String? = null,
        var method: String = "GET",
    ) {
        fun setUrl(url: String) = apply { this.url = url }
        fun setReferer(referer: String) = apply { this.referer = referer }
        fun setMethod(method: String) = apply { this.method = method }

        fun addAction(action: WebViewAction) = apply { this.actions.add(action) }
        fun addAllActions(actions: ArrayList<WebViewAction>) = apply { this.actions.addAll(actions) }

        fun waitForElement(selector: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_ELEMENT, selector, cb))
        }
        fun waitForElementGone(selector: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_ELEMENT, selector, cb))
        }
        fun waitForElementToBeClickable(selector: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_ELEMENT_TO_BE_CLICKABLE, selector, cb))
        }
        fun waitForSeconds(seconds: Long, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_X_SECONDS, seconds, cb))
        }
        fun waitForPageLoad(cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_PAGE_LOAD, "", cb))
        }
        fun waitForNetworkIdle(cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_NETWORK_IDLE, "", cb))
        }
        fun waitForNetworkCall(targetResource: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.WAIT_FOR_NETWORK_CALL, targetResource, cb))
        }
        fun executeJavaScript(code: String, cb: (AdvancedWebView) -> Unit = {  }) = apply {
            addAction(WebViewAction(WebViewActions.EXECUTE_JAVASCRIPT, code, cb))
        }
        fun close() = apply { addAction(WebViewAction(WebViewActions.RETURN, "")) }

        fun build(callback: (AdvancedWebView) -> Unit = { }) = AdvancedWebView(this.url, this.actions, this.referer, this.method, callback)
    }

    private var actionExecutionsPaused = false
    private var networkIdleTimestamp = -1;
    private var pageHasLoaded = false;

    private suspend fun tryExecuteAction() {
        if (actionExecutionsPaused) return
        main {
            if (remainingActions.size > 0) {
                val action = remainingActions[0]
                when (action.actionType){
                    WebViewActions.WAIT_FOR_ELEMENT -> {
                        actionExecutionsPaused = true
                        webView?.evaluateJavascript("document.querySelector(\"${action.parameter}\")") {
                            if (it == "{}") {
                                Instance.run(action.callback)
                                remainingActions.remove(action)
                            }
                            actionExecutionsPaused = false
                        }
                    }

                    WebViewActions.WAIT_FOR_ELEMENT_TO_BE_CLICKABLE -> {
                        actionExecutionsPaused = true

                        webView?.evaluateJavascript(
                        """
                            ((selector) => {
                                const elem = document.querySelector(selector)
                                if (elem == undefined) return
                                const attribute = elem.getAttribute("disabled")
                                if (attribute === "true" || attribute === '') return

                                return "" + (!elem.disabled || true)
                            })(`${action.parameter}`);
                        """.trimIndent()) {
                            if (it == "\"true\""){
                                Instance.run(action.callback)
                                remainingActions.remove(action)
                            }
                            actionExecutionsPaused = false
                        }
                    }

                    WebViewActions.WAIT_FOR_ELEMENT_GONE -> {
                        actionExecutionsPaused = true
                        webView?.evaluateJavascript("document.querySelector(\"${action.parameter}\") == undefined") {
                            if (it == "\"true\"") {
                                Instance.run(action.callback)
                                remainingActions.remove(action)
                            }
                            actionExecutionsPaused = false
                        }
                    }

                    WebViewActions.WAIT_FOR_NETWORK_IDLE -> {
                        if (!pageHasLoaded || ((System.currentTimeMillis() / 1000L) - networkIdleTimestamp) < 10) return@main
                        // we need at least 10 seconds of no network calls being done in order to be in an "IDLE" state
                        actionExecutionsPaused = true

                        Instance.run(action.callback)
                        remainingActions.remove(action)

                        actionExecutionsPaused = false
                    }

                    WebViewActions.WAIT_FOR_X_SECONDS -> {
                        actionExecutionsPaused = true

                        println("AdvancedWebView :: Waiting for ${remainingActions[0].parameter} seconds...")
                        delay(action.parameter as Long * 1000)
                        println("AdvancedWebView :: Finished waiting!")
                        Instance.run(action.callback)
                        remainingActions.remove(action)

                        actionExecutionsPaused = false
                    }

                    WebViewActions.EXECUTE_JAVASCRIPT -> {
                        actionExecutionsPaused = true

                        println("AdvancedWebView :: Executing javascript from action...")
                        webView?.evaluateJavascript(action.parameter as String) {
                            println("JavaScript Execution done! Result: <$it>")
                            Instance.run(action.callback)
                            remainingActions.remove(action)

                            actionExecutionsPaused = false
                        }
                    }

                    WebViewActions.RETURN -> {
                        actionExecutionsPaused = true

                        destroyWebView()
                        remainingActions.clear()
                    }

                    else -> return@main
                }
            }
        }
    }

    private fun destroyWebView() {
        main {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            println("Destroyed the WebView!")
        }
    }

    fun start() {
        main {
            try {
                webView = WebView(
                    AcraApplication.context
                        ?: throw RuntimeException("No base context in WebViewResolver")
                ).apply {
                    // Bare minimum to bypass captcha
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = USER_AGENT
                    settings.blockNetworkImage = true
                }
                webView!!.visibility = View.VISIBLE
            } catch (e: Exception) {
                println("Error: Failed to create an Advanced WebView, reason: <${e.message}>")
                println(e.toString())
                destroyWebView()
                callback(this)
            }

            try {
                webView?.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        pageHasLoaded = true
                        networkIdleTimestamp = (System.currentTimeMillis() / 1000).toInt();

                        if (remainingActions.size > 0 && remainingActions[0].actionType == WebViewActions.WAIT_FOR_PAGE_LOAD) {
                            println("PAGE FINISHED!")
                            val action = remainingActions[0]
                            Instance.run(action.callback)
                            remainingActions.remove(action)
                        }

                        runBlocking { tryExecuteAction() }
                    }

                    override fun onLoadResource(view: WebView?, url: String?) {
                        super.onLoadResource(view, url)
                        networkIdleTimestamp = (System.currentTimeMillis() / 1000L).toInt();
                        if (remainingActions.size > 0 && remainingActions[0].actionType == WebViewActions.WAIT_FOR_NETWORK_CALL) {
                            if (URI(url) == URI(remainingActions[0].parameter as String)) {
                                val action = remainingActions[0]
                                Instance.run(action.callback)
                                remainingActions.remove(action)
                            }
                        }
                        runBlocking { tryExecuteAction() }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = runBlocking {
                        networkIdleTimestamp = (System.currentTimeMillis() / 1000L).toInt();
                        tryExecuteAction()

                        val webViewUrl = request.url.toString()

                        val blacklistedFiles = listOf(
                            ".jpg", ".png", ".webp", ".mpg",
                            ".mpeg", ".jpeg", ".webm", ".mp4",
                            ".mp3", ".gifv", ".flv", ".asf",
                            ".mov", ".mng", ".mkv", ".ogg",
                            ".avi", ".wav", ".woff2", ".woff",
                            ".ttf", ".css", ".vtt", ".srt",
                            ".ts", ".gif",
                            // Warning, this might fuck some future sites, but it's used to make Sflix work.
                            "wss://"
                        )

                        return@runBlocking try {
                            when {
                                blacklistedFiles.any { URI(webViewUrl).path.contains(it) } || webViewUrl.endsWith(
                                    "/favicon.ico"
                                ) -> WebResourceResponse(
                                    "image/png",
                                    null,
                                    null
                                )

                                else -> return@runBlocking super.shouldInterceptRequest(
                                    view,
                                    request
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed() // Ignore ssl issues
                    }
                }
                webView?.loadUrl(url, headers.toMap())
            } catch (e: Exception){
                println("Failed to create a WebView client!")
                destroyWebView()
                return@main
            }

            fun setCurrentHTML() {
                webView?.evaluateJavascript("document.documentElement.outerHTML") {
                    currentHTML = it
                        .replace("\\u003C", "<")
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .trimStart('"').trimEnd('"')
                }
            }

            while (remainingActions.size > 0){
                setCurrentHTML()
                delay(300)
                tryExecuteAction()
                setCurrentHTML()
            }
            try {
                callback(this)
            } catch (e: Exception) {
                println("Err: $e")
            }
            destroyWebView()
        }
    }
}



/**
 * When used as Interceptor additionalUrls cannot be returned, use WebViewResolver(...).resolveUsingWebView(...)
 * @param interceptUrl will stop the WebView when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * */
class WebViewResolver(val interceptUrl: Regex, val additionalUrls: List<Regex> = emptyList()) :
    Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            val fixedRequest = resolveUsingWebView(request).first
            return@runBlocking chain.proceed(fixedRequest ?: request)
        }
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ) : Pair<Request?, List<Request>> {
        return resolveUsingWebView(
            requestCreator(method, url, referer = referer), requestCallBack
        )
    }

    /**
     * @param requestCallBack asynchronously return matched requests by either interceptUrl or additionalUrls. If true, destroy WebView.
     * @return the final request (by interceptUrl) and all the collected urls (by additionalUrls).
     * */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean = { false }
    ): Pair<Request?, List<Request>> {
        val url = request.url.toString()
        val headers = request.headers
        println("Initial web-view request: $url")
        var webView: WebView? = null

        fun destroyWebView() {
            main {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
                println("Destroyed webview")
            }
        }

        var fixedRequest: Request? = null
        val extraRequestList = mutableListOf<Request>()

        main {
            // Useful for debugging
//            WebView.setWebContentsDebuggingEnabled(true)
            try {
                webView = WebView(
                    AcraApplication.context
                        ?: throw RuntimeException("No base context in WebViewResolver")
                ).apply {
                    // Bare minimum to bypass captcha
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = USER_AGENT
                    // Blocks unnecessary images, remove if captcha fucks.
                    settings.blockNetworkImage = true
                }

                webView?.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = runBlocking {
                        val webViewUrl = request.url.toString()
//                    println("Loading WebView URL: $webViewUrl")

                        if (interceptUrl.containsMatchIn(webViewUrl)) {
                            fixedRequest = request.toRequest().also {
                                if (requestCallBack(it)) destroyWebView()
                            }
                            println("Web-view request finished: $webViewUrl")
                            destroyWebView()
                            return@runBlocking null
                        }

                        if (additionalUrls.any { it.containsMatchIn(webViewUrl) }) {
                            extraRequestList.add(request.toRequest().also {
                                if (requestCallBack(it)) destroyWebView()
                            })
                        }

                        // Suppress image requests as we don't display them anywhere
                        // Less data, low chance of causing issues.
                        // blockNetworkImage also does this job but i will keep it for the future.
                        val blacklistedFiles = listOf(
                            ".jpg", ".png", ".webp", ".mpg",
                            ".mpeg", ".jpeg", ".webm", ".mp4",
                            ".mp3", ".gifv", ".flv", ".asf",
                            ".mov", ".mng", ".mkv", ".ogg",
                            ".avi", ".wav", ".woff2", ".woff",
                            ".ttf", ".css", ".vtt", ".srt",
                            ".ts", ".gif",
                            // Warning, this might fuck some future sites, but it's used to make Sflix work.
                            "wss://"
                        )

                        /** NOTE!  request.requestHeaders is not perfect!
                         *  They don't contain all the headers the browser actually gives.
                         *  Overriding with okhttp might fuck up otherwise working requests,
                         *  e.g the recaptcha request.
                         * **/

                        return@runBlocking try {
                            when {
                                blacklistedFiles.any { URI(webViewUrl).path.contains(it) } || webViewUrl.endsWith(
                                    "/favicon.ico"
                                ) -> WebResourceResponse(
                                    "image/png",
                                    null,
                                    null
                                )

                                webViewUrl.contains("recaptcha") -> super.shouldInterceptRequest(
                                    view,
                                    request
                                )

                                request.method == "GET" -> app.get(
                                    webViewUrl,
                                    headers = request.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                request.method == "POST" -> app.post(
                                    webViewUrl,
                                    headers = request.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()
                                else -> return@runBlocking super.shouldInterceptRequest(
                                    view,
                                    request
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed() // Ignore ssl issues
                    }
                }
                webView?.loadUrl(url, headers.toMap())
            } catch (e: Exception) {
                logError(e)
            }
        }

        var loop = 0
        // Timeouts after this amount, 60s
        val totalTime = 60000L

        val delayTime = 100L

        // A bit sloppy, but couldn't find a better way
        while (loop < totalTime / delayTime) {
            if (fixedRequest != null) return fixedRequest to extraRequestList
            delay(delayTime)
            loop += 1
        }

        println("Web-view timeout after ${totalTime / 1000}s")
        destroyWebView()
        return null to extraRequestList
    }

    fun WebResourceRequest.toRequest(): Request {
        val webViewUrl = this.url.toString()

        return requestCreator(
            this.method,
            webViewUrl,
            this.requestHeaders,
        )
    }

    fun Response.toWebResourceResponse(): WebResourceResponse {
        val contentTypeValue = this.header("Content-Type")
        // 1. contentType. 2. charset
        val typeRegex = Regex("""(.*);(?:.*charset=(.*)(?:|;)|)""")
        return if (contentTypeValue != null) {
            val found = typeRegex.find(contentTypeValue)
            val contentType = found?.groupValues?.getOrNull(1)?.ifBlank { null } ?: contentTypeValue
            val charset = found?.groupValues?.getOrNull(2)?.ifBlank { null }
            WebResourceResponse(contentType, charset, this.body?.byteStream())
        } else {
            WebResourceResponse("application/octet-stream", null, this.body?.byteStream())
        }
    }
}