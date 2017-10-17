package io.ktor.client.features

import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.call
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.takeFrom
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.HttpResponseBuilder
import io.ktor.client.response.HttpResponsePipeline
import io.ktor.client.utils.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.response.etag
import io.ktor.http.response.expires
import io.ktor.util.AttributeKey
import io.ktor.util.ValuesMapBuilder
import java.util.*
import java.util.concurrent.ConcurrentHashMap


private val IGNORE_CACHE = AttributeKey<Boolean>("IgnoreCache")

// consider about `match all` case
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

class HttpCache(
        val maxAge: Int?
) {
    private val responseCache = ConcurrentHashMap<Url, CacheEntity>()

    private fun load(builder: HttpRequestBuilder): HttpResponse? {
        val now = Date()
        val url = UrlBuilder().takeFrom(builder.url).build()
        return responseCache[url]?.takeIf { it.match(builder.headers) }?.cache
    }

    private fun isValid(response: HttpResponse, builder: HttpRequestBuilder): Boolean {
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

        builder.cacheControl.maxAge?.let {
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
            builder: HttpRequestBuilder,
            scope: HttpClient
    ): HttpClientCall? {
        val request = HttpRequestBuilder().takeFrom(builder)

        val lastModified = cachedResponse.lastModified()
        val etag = cachedResponse.etag()

        if (lastModified == null && etag == null) return null

        etag?.let { request.ifNoneMatch(it) }
        lastModified?.let { request.ifModifiedSince(it) }

        request.flags.put(IGNORE_CACHE, true)

        val call = scope.call(request)
        return when (call.response.statusCode) {
            HttpStatusCode.NotModified -> HttpClientCall(builder.build(), cachedResponse, scope)
            HttpStatusCode.OK -> {
                cacheResponse(call.request, HttpResponseBuilder(call.response))
                call
            }
            else -> null
        }
    }

    private fun cacheResponse(request: HttpRequest, response: HttpResponseBuilder) {
        if (response.statusCode != HttpStatusCode.OK) return
        if (response.expires()?.before(Date()) == true) return
        if (response.payload is InputStreamBody || response.payload is OutputStreamBody) return

        with(request.cacheControl) {
            if (noCache || noStore) return
        }

        with(response.cacheControl) {
            if (noCache || noStore) return
        }

        cache(request.url, request.headers, response.build())
    }

    private fun cache(url: Url, requestHeaders: Headers, response: HttpResponse) {
        val varyHeaders = response.vary() ?: listOf()

        val invariant = varyHeaders.map { key ->
            key to (requestHeaders.getAll(key)?.toSet() ?: setOf())
        }.toMap()

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
            scope.requestPipeline.intercept(HttpRequestPipeline.Send) { builder ->
                if (builder !is HttpRequestBuilder || builder.flags.contains(IGNORE_CACHE) || builder.method != HttpMethod.Get) {
                    return@intercept
                }

                if (feature.maxAge != null && builder.maxAge() == null) {
                    builder.maxAge(feature.maxAge)
                }

                val cacheEntity = feature.load(builder) ?: return@intercept
                if (feature.isValid(cacheEntity, builder)) {
                    proceedWith(HttpClientCall(builder.build(), cacheEntity, scope))
                    return@intercept
                }

                feature.validate(cacheEntity, builder, scope)?.let { proceedWith(it) }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.After) { (_, request, response) ->
                if (request.method != HttpMethod.Get) return@intercept
                feature.cacheResponse(request, response)
            }
        }
    }
}

class NotFoundInCacheException(val request: HttpRequest) : Exception()