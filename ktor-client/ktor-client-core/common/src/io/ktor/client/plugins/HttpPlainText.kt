/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.math.roundToInt

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.HttpPlainText")

/**
 * Charset configuration for [HttpPlainText] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpPlainTextConfig)
 */
@KtorDsl
public class HttpPlainTextConfig {
    internal val charsets: MutableSet<Charset> = mutableSetOf()
    internal val charsetQuality: MutableMap<Charset, Float> = mutableMapOf()

    /**
     * Add [charset] to allowed list with selected [quality].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpPlainTextConfig.register)
     */
    public fun register(charset: Charset, quality: Float? = null) {
        quality?.let { check(it in 0.0..1.0) }

        charsets.add(charset)

        if (quality == null) {
            charsetQuality.remove(charset)
        } else {
            charsetQuality[charset] = quality
        }
    }

    /**
     * Explicit [Charset] for sending content.
     *
     * Use first with the highest quality from [register] charset if null.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpPlainTextConfig.sendCharset)
     */
    public var sendCharset: Charset? = null

    /**
     * Fallback charset for the response.
     * Use it if no charset specified.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpPlainTextConfig.responseCharsetFallback)
     */
    public var responseCharsetFallback: Charset = Charsets.UTF_8
}

/**
 * [HttpClient] plugin that encodes [String] request bodies to [TextContent]
 * and processes the response body as [String].
 *
 * To configure charsets, use properties in [HttpPlainTextConfig].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.HttpPlainText)
 */
public val HttpPlainText: ClientPlugin<HttpPlainTextConfig> =
    createClientPlugin("HttpPlainText", ::HttpPlainTextConfig) {

        val withQuality = pluginConfig.charsetQuality.toList().sortedByDescending { it.second }
        val responseCharsetFallback = pluginConfig.responseCharsetFallback
        val withoutQuality = pluginConfig.charsets
            .filter { !pluginConfig.charsetQuality.containsKey(it) }
            .sortedBy { it.name }

        // RFC-9110 assumes UTF-8 as the default encoding. The Accept-Charset header is deprecated,
        // so we shouldn't add it if the user didn't specify any charset configuration
        val hasRegisteredCharsets = pluginConfig.charsets.any { it != Charsets.UTF_8 } ||
            pluginConfig.charsetQuality.keys.any { it != Charsets.UTF_8 }
        val acceptCharsetHeader = if (hasRegisteredCharsets) {
            buildString {
                withoutQuality.forEach {
                    if (isNotEmpty()) append(",")
                    append(it.name)
                }

                withQuality.forEach { (charset, quality) ->
                    if (isNotEmpty()) append(",")

                    check(quality in 0.0..1.0)

                    val truncatedQuality = (100 * quality).roundToInt() / 100.0
                    append("${charset.name};q=$truncatedQuality")
                }
            }
        } else {
            null
        }

        val requestCharset = pluginConfig.sendCharset
            ?: withoutQuality.firstOrNull() ?: withQuality.firstOrNull()?.first ?: Charsets.UTF_8

        fun wrapContent(
            request: HttpRequestBuilder,
            content: String,
            requestContentType: ContentType?
        ): OutgoingContent {
            val contentType: ContentType = requestContentType ?: ContentType.Text.Plain
            val charset = requestContentType?.charset() ?: requestCharset

            LOGGER.trace("Sending request body to ${request.url} as text/plain with charset $charset")
            return TextContent(content, contentType.withCharset(charset))
        }

        fun read(call: HttpClientCall, body: Input): String {
            val actualCharset = call.response.charset() ?: responseCharsetFallback
            LOGGER.trace("Reading response body for ${call.request.url} as String with charset $actualCharset")
            return body.readText(charset = actualCharset)
        }

        fun HttpRequestBuilder.addAcceptCharsetHeader(value: String?) {
            if (value == null || headers[HttpHeaders.AcceptCharset] != null) return
            LOGGER.trace("Adding Accept-Charset=$value to $url")
            headers[HttpHeaders.AcceptCharset] = value
        }

        on(RenderRequestHook) { request, content ->
            request.addAcceptCharsetHeader(acceptCharsetHeader)

            if (content !is String) return@on null

            val contentType = request.contentType()
            if (contentType != null && contentType.contentType != ContentType.Text.Plain.contentType) {
                return@on null
            }

            wrapContent(request, content, contentType)
        }

        transformResponseBody { response, content, requestedType ->
            if (requestedType.type != String::class) return@transformResponseBody null

            val bodyBytes = content.readRemaining()
            read(response.call, bodyBytes)
        }
    }

internal object RenderRequestHook : ClientHook<suspend (HttpRequestBuilder, Any) -> OutgoingContent?> {
    override fun install(client: HttpClient, handler: suspend (HttpRequestBuilder, Any) -> OutgoingContent?) {
        client.requestPipeline.intercept(HttpRequestPipeline.Render) { content ->
            val result = handler(context, content)
            if (result != null) proceedWith(result)
        }
    }
}

/**
 * Configure client charsets.
 *
 * ```kotlin
 * val client = HttpClient {
 *     Charsets {
 *         register(Charsets.UTF_8)
 *         register(Charsets.ISO_8859_1, quality = 0.1)
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.Charsets)
 */
@Suppress("FunctionName")
public fun HttpClientConfig<*>.Charsets(block: HttpPlainTextConfig.() -> Unit) {
    install(HttpPlainText, block)
}
