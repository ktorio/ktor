/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.cookies

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.cookies.HttpCookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * [HttpClient] feature that handles sent `Cookie`, and received `Set-Cookie` headers,
 * using a specific [storage] for storing and retrieving cookies.
 *
 * You can configure the [Config.storage] and to provide [Config.default] blocks to set
 * cookies when installing.
 */
public class HttpCookies(
    private val storage: CookiesStorage,
    private val defaults: List<suspend CookiesStorage.() -> Unit>
) : Closeable {
    private val initializer: Job = GlobalScope.launch(Dispatchers.Unconfined) {
        defaults.forEach { it(storage) }
    }

    /**
     * Find all cookies by [requestUrl].
     */
    public suspend fun get(requestUrl: Url): List<Cookie> {
        initializer.join()
        return storage.get(requestUrl)
    }

    internal suspend fun sendCookiesWith(builder: HttpRequestBuilder) {
        val cookies = get(builder.url.clone().build())

        with(builder) {
            if (cookies.isNotEmpty()) {
                headers[HttpHeaders.Cookie] = renderClientCookies(cookies)
            } else {
                headers.remove(HttpHeaders.Cookie)
            }
        }
    }

    internal suspend fun saveCookiesFrom(response: HttpResponse) {
        val url = response.request.url
        response.setCookie().forEach {
            storage.addCookie(url, it)
        }
    }

    override fun close() {
        storage.close()
    }

    /**
     * [HttpCookies] configuration.
     */
    public class Config {
        private val defaults = mutableListOf<suspend CookiesStorage.() -> Unit>()

        /**
         * [CookiesStorage] that will be used at this feature.
         * By default it just uses an initially empty in-memory [AcceptAllCookiesStorage].
         */
        public var storage: CookiesStorage = AcceptAllCookiesStorage()

        /**
         * Registers a [block] that will be called when the configuration is complete the specified [storage].
         * The [block] can potentially add new cookies by calling [CookiesStorage.addCookie].
         */
        public fun default(block: suspend CookiesStorage.() -> Unit) {
            defaults.add(block)
        }

        internal fun build(): HttpCookies = HttpCookies(storage, defaults)
    }

    public companion object : HttpClientFeature<Config, HttpCookies> {
        override fun prepare(block: Config.() -> Unit): HttpCookies = Config().apply(block).build()

        override val key: AttributeKey<HttpCookies> = AttributeKey("HttpCookies")

        override fun install(feature: HttpCookies, scope: HttpClient) {
            scope.sendPipeline.intercept(HttpSendPipeline.State) {
                feature.sendCookiesWith(context)
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                feature.saveCookiesFrom(response)
            }
        }
    }
}

private fun renderClientCookies(cookies: List<Cookie>): String = buildString {
    cookies.forEach {
        append(it.name)
        append('=')
        append(encodeCookieValue(it.value, it.encoding))
        append(';')
    }
}

/**
 * Gets all the cookies for the specified [url] for this [HttpClient].
 */
public suspend fun HttpClient.cookies(url: Url): List<Cookie> = feature(HttpCookies)?.get(url) ?: emptyList()

/**
 * Gets all the cookies for the specified [urlString] for this [HttpClient].
 */
public suspend fun HttpClient.cookies(urlString: String): List<Cookie> =
    feature(HttpCookies)?.get(Url(urlString)) ?: emptyList()

/**
 * Find the [Cookie] by [name]
 */
public operator fun List<Cookie>.get(name: String): Cookie? = find { it.name == name }
