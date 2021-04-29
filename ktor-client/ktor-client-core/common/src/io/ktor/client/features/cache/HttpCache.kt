/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.cache

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.features.cache.storage.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

internal object CacheControl {
    internal val NO_STORE = HeaderValue("no-store")
    internal val NO_CACHE = HeaderValue("no-cache")
    internal val PRIVATE = HeaderValue("private")
    internal val MUST_REVALIDATE = HeaderValue("must-revalidate")
}

/**
 * This feature allow to use HTTP cache.
 *
 * For detailed description follow: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control
 */
public class HttpCache(
    public val publicStorage: HttpCacheStorage,
    public val privateStorage: HttpCacheStorage
) {
    /**
     * [HttpCache] configuration.
     */
    public class Config {
        /**
         * Storage for public cache entries.
         *
         * Use [HttpCacheStorage.Unlimited] by default.
         */
        public var publicStorage: HttpCacheStorage = HttpCacheStorage.Unlimited()

        /**
         * Storage for private cache entries.
         *
         * [HttpCacheStorage.Unlimited] by default.
         *
         * Consider using [HttpCacheStorage.Disabled] if the client used as intermediate.
         */
        public var privateStorage: HttpCacheStorage = HttpCacheStorage.Unlimited()
    }

    public companion object : HttpClientFeature<Config, HttpCache> {
        override val key: AttributeKey<HttpCache> = AttributeKey("HttpCache")

        override fun prepare(block: Config.() -> Unit): HttpCache {
            val config = Config().apply(block)

            with(config) {
                return HttpCache(publicStorage, privateStorage)
            }
        }

        override fun install(feature: HttpCache, scope: HttpClient) {
            val CachePhase = PipelinePhase("Cache")
            scope.sendPipeline.insertPhaseAfter(HttpSendPipeline.State, CachePhase)

            scope.sendPipeline.intercept(CachePhase) { content ->
                if (content !is OutgoingContent.NoContent) return@intercept
                if (context.method != HttpMethod.Get || !context.url.protocol.canStore()) return@intercept

                val cache = feature.findResponse(context, content) ?: return@intercept
                if (!cache.shouldValidate()) {
                    finish()
                    proceedWith(cache.produceResponse().call)

                    return@intercept
                }

                cache.responseHeaders[HttpHeaders.ETag]?.let { etag ->
                    context.header(HttpHeaders.IfNoneMatch, etag)
                }

                cache.responseHeaders[HttpHeaders.LastModified]?.let {
                    context.header(HttpHeaders.IfModifiedSince, it)
                }
            }

            scope.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
                if (context.request.method != HttpMethod.Get) return@intercept

                if (response.status.isSuccess()) {
                    val reusableResponse = feature.cacheResponse(response)
                    proceedWith(reusableResponse)
                    return@intercept
                }

                if (response.status == HttpStatusCode.NotModified) {
                    response.complete()
                    val responseFromCache = feature.findAndRefresh(context.request, response)
                        ?: throw InvalidCacheStateException(context.request.url)

                    proceedWith(responseFromCache)
                }
            }
        }
    }

    private suspend fun cacheResponse(response: HttpResponse): HttpResponse {
        val request = response.call.request
        val responseCacheControl: List<HeaderValue> = response.cacheControl()

        val storage = if (CacheControl.PRIVATE in responseCacheControl) privateStorage else publicStorage

        if (CacheControl.NO_STORE in responseCacheControl) {
            return response
        }

        val cacheEntry = storage.store(request.url, response)
        return cacheEntry.produceResponse()
    }

    private fun findAndRefresh(request: HttpRequest, response: HttpResponse): HttpResponse? {
        val url = response.call.request.url
        val cacheControl = response.cacheControl()

        val storage = if (CacheControl.PRIVATE in cacheControl) privateStorage else publicStorage

        val varyKeysFrom304 = response.varyKeys()
        val cache = findResponse(storage, varyKeysFrom304, url, request) ?: return null
        val newVaryKeys = if (varyKeysFrom304.isNullOrEmpty()) cache.varyKeys else varyKeysFrom304
        storage.store(url, HttpCacheEntry(response.cacheExpires(), newVaryKeys, cache.response, cache.body))
        return cache.produceResponse()
    }

    private fun findResponse(
        storage: HttpCacheStorage,
        varyKeys: Map<String, String>,
        url: Url,
        request: HttpRequest
    ): HttpCacheEntry? = when {
        varyKeys.isNotEmpty() -> {
            storage.find(url, varyKeys)
        }
        else -> {
            val requestHeaders = mergedHeadersLookup(request.content, request.headers::get, request.headers::getAll)
            storage.findByUrl(url)
                .sortedByDescending { it.response.responseTime }
                .firstOrNull { cachedResponse ->
                    cachedResponse.varyKeys.all { (key, value) -> requestHeaders(key) == value }
                }
        }
    }

    private fun findResponse(context: HttpRequestBuilder, content: OutgoingContent): HttpCacheEntry? {
        val url = Url(context.url)
        val lookup = mergedHeadersLookup(content, context.headers::get, context.headers::getAll)

        val cachedResponses = privateStorage.findByUrl(url) + publicStorage.findByUrl(url)
        for (item in cachedResponses) {
            val varyKeys = item.varyKeys
            if (varyKeys.isEmpty() || varyKeys.all { (key, value) -> lookup(key) == value }) {
                return item
            }
        }

        return null
    }
}

private fun mergedHeadersLookup(
    content: OutgoingContent,
    headerExtractor: (String) -> String?,
    allHeadersExtractor: (String) -> List<String>?,
): (String) -> String = block@{ header ->
    return@block when (header) {
        HttpHeaders.ContentLength -> content.contentLength?.toString() ?: ""
        HttpHeaders.ContentType -> content.contentType?.toString() ?: ""
        HttpHeaders.UserAgent -> {
            content.headers[HttpHeaders.UserAgent] ?: headerExtractor(HttpHeaders.UserAgent) ?: KTOR_DEFAULT_USER_AGENT
        }
        else -> {
            val value = content.headers.getAll(header) ?: allHeadersExtractor(header) ?: emptyList()
            value.joinToString(";")
        }
    }
}

@Suppress("KDocMissingDocumentation")
public class InvalidCacheStateException(requestUrl: Url) : IllegalStateException(
    "The entry for url: $requestUrl was removed from cache"
)

private fun URLProtocol.canStore(): Boolean = name == "http" || name == "https"
