/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.cache

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.storage.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

internal object CacheControl {
    internal val NO_STORE = HeaderValue("no-store")
    internal val NO_CACHE = HeaderValue("no-cache")
    internal val PRIVATE = HeaderValue("private")
    internal val ONLY_IF_CACHED = HeaderValue("only-if-cached")
    internal val MUST_REVALIDATE = HeaderValue("must-revalidate")
}

/**
 * A plugin that allows you to save previously fetched resources in an in-memory cache.
 * For example, if you make two consequent requests to a resource with the configured `Cache-Control` header,
 * the client executes only the first request and skips the second one since data is already saved in a cache.
 *
 * You can learn more from [Caching](https://ktor.io/docs/client-caching.html).
 */
public class HttpCache private constructor(
    public val publicStorage: HttpCacheStorage,
    public val privateStorage: HttpCacheStorage
) {
    /**
     * A configuration for the [HttpCache] plugin.
     */
    @KtorDsl
    public class Config {
        /**
         * Specifies a storage for public cache entries.
         *
         * [HttpCacheStorage.Unlimited] by default.
         */
        public var publicStorage: HttpCacheStorage = HttpCacheStorage.Unlimited()

        /**
         * Specifies a storage for private cache entries.
         *
         * [HttpCacheStorage.Unlimited] by default.
         *
         * Consider using [HttpCacheStorage.Disabled] if the client is used as intermediate.
         */
        public var privateStorage: HttpCacheStorage = HttpCacheStorage.Unlimited()
    }

    public companion object : HttpClientPlugin<Config, HttpCache> {
        override val key: AttributeKey<HttpCache> = AttributeKey("HttpCache")

        public val HttpResponseFromCache: EventDefinition<HttpResponse> = EventDefinition()

        override fun prepare(block: Config.() -> Unit): HttpCache {
            val config = Config().apply(block)

            with(config) {
                return HttpCache(publicStorage, privateStorage)
            }
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: HttpCache, scope: HttpClient) {
            val CachePhase = PipelinePhase("Cache")
            scope.sendPipeline.insertPhaseAfter(HttpSendPipeline.State, CachePhase)

            scope.sendPipeline.intercept(CachePhase) { content ->
                if (content !is OutgoingContent.NoContent) return@intercept
                if (context.method != HttpMethod.Get || !context.url.protocol.canStore()) return@intercept

                val cache = plugin.findResponse(context, content)
                if (cache == null) {
                    val header = parseHeaderValue(context.headers[HttpHeaders.CacheControl])
                    if (CacheControl.ONLY_IF_CACHED in header) {
                        proceedWithMissingCache(scope)
                    }
                    return@intercept
                }
                val cachedCall = cache.produceResponse().call
                val validateStatus = cache.shouldValidate(context)

                if (validateStatus == ValidateStatus.ShouldNotValidate) {
                    proceedWithCache(scope, cachedCall)
                    return@intercept
                }

                if (validateStatus == ValidateStatus.ShouldWarn) {
                    proceedWithWarning(cachedCall, scope)
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
                if (response.call.request.method != HttpMethod.Get) return@intercept

                if (response.status.isSuccess()) {
                    val reusableResponse = plugin.cacheResponse(response)
                    proceedWith(reusableResponse)
                    return@intercept
                }

                if (response.status == HttpStatusCode.NotModified) {
                    response.complete()
                    val responseFromCache = plugin.findAndRefresh(response.call.request, response)
                        ?: throw InvalidCacheStateException(response.call.request.url)

                    scope.monitor.raise(HttpResponseFromCache, responseFromCache)
                    proceedWith(responseFromCache)
                }
            }
        }

        private suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithCache(
            scope: HttpClient,
            cachedCall: HttpClientCall
        ) {
            finish()
            scope.monitor.raise(HttpResponseFromCache, cachedCall.response)
            proceedWith(cachedCall)
        }

        @OptIn(InternalAPI::class)
        private suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithWarning(
            cachedCall: HttpClientCall,
            scope: HttpClient
        ) {
            val request = context.build()
            val response = HttpResponseData(
                statusCode = cachedCall.response.status,
                requestTime = cachedCall.response.requestTime,
                headers = Headers.build {
                    appendAll(cachedCall.response.headers);
                    append(HttpHeaders.Warning, "110")
                },
                version = cachedCall.response.version,
                body = cachedCall.response.content,
                callContext = cachedCall.response.coroutineContext
            )
            val call = HttpClientCall(scope, request, response)
            finish()
            scope.monitor.raise(HttpResponseFromCache, call.response)
            proceedWith(call)
        }

        @OptIn(InternalAPI::class)
        private suspend fun PipelineContext<Any, HttpRequestBuilder>.proceedWithMissingCache(
            scope: HttpClient
        ) {
            finish()
            val request = context.build()
            val response = HttpResponseData(
                statusCode = HttpStatusCode.GatewayTimeout,
                requestTime = GMTDate(),
                headers = Headers.Empty,
                version = HttpProtocolVersion.HTTP_1_1,
                body = ByteReadChannel(ByteArray(0)),
                callContext = request.executionContext
            )
            val call = HttpClientCall(scope, request, response)
            proceedWith(call)
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
        val newVaryKeys = varyKeysFrom304.ifEmpty { cache.varyKeys }
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

@OptIn(InternalAPI::class)
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
