package io.ktor.server.htmx

import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlin.jvm.JvmInline

/**
 * Provides typed access to the HTMX request headers of this request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.hx)
 */
public val RoutingRequest.hx: HXRequestHeaders get() = HXRequestHeaders(headers)

/**
 * Provides typed access to the HTMX response headers that will be sent with this response.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.hx)
 */
public val RoutingResponse.hx: HXResponseHeaders get() = HXResponseHeaders(headers)

/**
 * Whether this request was made by htmx, i.e. the `HX-Request` header is set to `true`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.isHtmx)
 */
public val RoutingRequest.isHtmx: Boolean get() = headers[HxRequestHeaders.Request] == "true"

/**
 * Typed accessors for the HTMX request headers sent by the htmx.org client library.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXRequestHeaders)
 *
 * @see [Official documentation](https://htmx.org/reference/#request_headers)
 */
@JvmInline
public value class HXRequestHeaders(private val headers: Headers) {

    /**
     * Indicates that the request is via an element using hx-boost
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXRequestHeaders.isBoosted)
     */
    public val isBoosted: Boolean get() = headers[HxRequestHeaders.Boosted]?.toBoolean() == true

    /**
     * "true" if the request is for history restoration after a miss in the local history cache
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXRequestHeaders.isHistoryRestore)
     */
    public val isHistoryRestore: Boolean get() = headers[HxRequestHeaders.HistoryRestoreRequest]?.toBoolean() == true

    /**
     * The current URL of the browser
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXRequestHeaders.currentUrl)
     */
    public val currentUrl: Url? get() = headers[HxRequestHeaders.CurrentUrl]?.let { Url(it) }

    /**
     * The user response to an hx-prompt
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXRequestHeaders.prompt)
     */
    public val prompt: String? get() = headers[HxRequestHeaders.Prompt]

    /**
     * The id of the target element if it exists
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXRequestHeaders.targetId)
     */
    public val targetId: String? get() = headers[HxRequestHeaders.Target]

    /**
     * The id of the triggered element if it exists
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXRequestHeaders.triggerId)
     */
    public val triggerId: String? get() = headers[HxRequestHeaders.Trigger]

    /**
     * The name of the triggered element if it exists
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXRequestHeaders.triggerName)
     */
    public val triggerName: String? get() = headers[HxRequestHeaders.TriggerName]
}

/**
 * Typed accessors for setting the HTMX response headers understood by the htmx.org client library.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXResponseHeaders)
 *
 * @see [Official documentation](https://htmx.org/reference/#response_headers)
 */
@OptIn(InternalAPI::class)
public class HXResponseHeaders(private val headers: ResponseHeaders) : StringMap {

    /**
     * Allows you to do a client-side redirect that does not do a full page reload.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXResponseHeaders.location)
     */
    public var location: String? by HxResponseHeaders.Location

    /**
     * Pushes a new URL into the history stack.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXResponseHeaders.pushUrl)
     */
    public var pushUrl: String? by HxResponseHeaders.PushUrl

    /**
     * Can be used to do a client-side redirect to a new location.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXResponseHeaders.redirect)
     */
    public var redirect: String? by HxResponseHeaders.Redirect

    /**
     * If set to `true`, the client-side will do a full refresh of the page.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXResponseHeaders.refresh)
     */
    public var refresh: Boolean? by HxResponseHeaders.Refresh.asBoolean()

    /**
     * Replaces the current URL in the location bar.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.htmx.HXResponseHeaders.replaceUrl)
     */
    public val replaceUrl: String? by HxResponseHeaders.ReplaceUrl

    override fun set(key: String, value: String): Unit =
        headers.append(key, value)

    override fun get(key: String): String? =
        headers[key]

    override fun remove(key: String): String? =
        throw IllegalStateException("Not implemented")
}
