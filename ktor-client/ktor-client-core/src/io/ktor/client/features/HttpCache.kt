package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import java.util.*
import java.util.concurrent.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set


private val IGNORE_CACHE = AttributeKey<Boolean>("IgnoreCache")

private data class CacheEntity(
        val invariant: Map<String, Set<String>>,
        val cache: HttpResponse
) {
    fun match(headers: HeadersBuilder): Boolean = invariant.entries.all { (name, values) ->
        val requestValues = headers.getAll(name) ?: return false
        if (requestValues.size != values.size) return false
        if (values.size == 1) return values.first() == requestValues.first()

        return requestValues.toSet() == values.toSet()
    }
}

class HttpCache(val maxAge: Int?) {
    private val responseCache = ConcurrentHashMap<Url, CacheEntity>()

    private fun load(request: HttpRequestBuilder): HttpResponse? {
        val url = UrlBuilder().takeFrom(request.url).build()
        return responseCache[url]?.takeIf { it.match(request.headers) }?.cache
    }

    private fun isValid(response: HttpResponse, request: HttpRequestBuilder): Boolean {
        val now = Date().time
        val requestTime = response.requestTime.time
        var hasCacheMarker = false

        with(response.cacheControl) {
            maxAge?.let {
                if (requestTime + it * 1000 < now) return false
                hasCacheMarker = true
            }
            if (mustRevalidate) return false
        }

        request.cacheControl.maxAge?.let {
            if (requestTime + it * 1000 < now) return false
            hasCacheMarker = true
        }

        response.expires()?.let {
            if (it.time < now) return false
            hasCacheMarker = true
        }

        return hasCacheMarker && response.etag() == null && response.lastModified() == null
    }

    private suspend fun validate(
            cachedResponse: HttpResponse,
            request: HttpRequestBuilder,
            scope: HttpClient
    ): HttpClientCall? {
        val validationRequest = HttpRequestBuilder().takeFrom(request)

        val lastModified = cachedResponse.lastModified()
        val etag = cachedResponse.etag()

        if (lastModified == null && etag == null) return null

        etag?.let { validationRequest.ifNoneMatch(it) }
        lastModified?.let { validationRequest.ifModifiedSince(it) }

        val response = scope.call {
            takeFrom(validationRequest)
            flags.put(IGNORE_CACHE, true)
        }

        return when (response.status) {
            HttpStatusCode.NotModified ->
                HttpClientCall(response.call.request, HttpResponseBuilder(cachedResponse), scope)
            HttpStatusCode.OK ->
                response.call
            else -> null
        }
    }

    private fun cacheResponse(request: HttpRequest, response: HttpResponse): Boolean {
        if (response.status != HttpStatusCode.OK) return false
        if (response.expires()?.before(Date()) == true) return false
        if (response.body is ByteReadChannelBody || response.body is ByteWriteChannelBody) return false

        with(request.cacheControl) {
            if (noCache || noStore) return false
        }

        with(response.cacheControl) {
            if (noCache || noStore) return false
        }

        cache(request.url, request.headers, response)
        return true
    }

    private fun cache(url: Url, requestHeaders: Headers, response: HttpResponse) {
        val varyHeaders = response.vary() ?: listOf()
        val invariant = varyHeaders.map { key -> key to (requestHeaders.getAll(key)?.toSet() ?: setOf()) }.toMap()

        responseCache[url] = CacheEntity(invariant, response)
    }

    class Config {
        var maxAge: Int? = null

        fun build(): HttpCache = HttpCache(maxAge)
    }

    companion object Feature : HttpClientFeature<Config, HttpCache> {
        override val key: AttributeKey<HttpCache> = AttributeKey("HttpCache")

        override fun prepare(block: Config.() -> Unit): HttpCache = Config().apply(block).build()

        override fun install(feature: HttpCache, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Send) { request ->
                if (request !is HttpRequestBuilder || request.flags.contains(IGNORE_CACHE) || request.method != HttpMethod.Get) {
                    return@intercept
                }

                if (feature.maxAge != null && request.cacheControl.maxAge == null) {
                    request.maxAge(feature.maxAge)
                }

                val onlyIfCached = request.cacheControl.onlyIfCached
                val cachedResponse = feature.load(request) ?: run {
                    if (onlyIfCached) throw NotFoundInCacheException(request.build())
                    return@intercept
                }

                if (feature.isValid(cachedResponse, request)) {
                    proceedWith(HttpClientCall(request.build(), HttpResponseBuilder(cachedResponse), scope))
                    return@intercept
                }

                if (onlyIfCached) throw NotFoundInCacheException(request.build())
                feature.validate(cachedResponse, request, scope)?.let { proceedWith(it) }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.After) { (type, request, response) ->
                if (request.method != HttpMethod.Get) return@intercept
                val cachedCall = HttpClientCall(request, response, scope)
                if (feature.cacheResponse(request, cachedCall.response)) {
                    proceedWith(HttpResponseContainer(type, request, HttpResponseBuilder(cachedCall.response)))
                }
            }
        }
    }
}

class NotFoundInCacheException(val request: HttpRequest) : Exception()