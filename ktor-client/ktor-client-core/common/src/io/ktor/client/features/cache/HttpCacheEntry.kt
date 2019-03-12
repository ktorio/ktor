package io.ktor.client.features.cache

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import kotlin.coroutines.*

internal suspend fun HttpCacheEntry(response: HttpResponse): HttpCacheEntry = response.use {
    val body = it.content.readRemaining().readBytes()
    return HttpCacheEntry(it.cacheExpires(), it.varyKeys(), it, body)
}

/**
 * Client single response cache with [expires] and [varyKeys].
 */
@KtorExperimentalAPI
@Suppress("KDocMissingDocumentation")
class HttpCacheEntry internal constructor(
    val expires: GMTDate,
    val varyKeys: Map<String, String>,
    val response: HttpResponse,
    val body: ByteArray
) {
    internal val responseHeaders: Headers = Headers.build {
        appendAll(response.headers)
    }

    internal fun produceResponse(): HttpResponse {
        val call = HttpCacheCall(response.call.client)
        call.response = HttpCacheResponse(call, body, response)
        call.request = HttpCacheRequest(call, response.call.request)

        return call.response
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is HttpCacheEntry) return false
        if (other === this) return true
        return varyKeys == other.varyKeys
    }

    override fun hashCode(): Int {
        return varyKeys.hashCode()
    }
}


internal fun HttpResponse.varyKeys(): Map<String, String> {
    val validationKeys = vary() ?: return emptyMap()

    val result = mutableMapOf<String, String>()
    val requestHeaders = call.request.headers

    for (key in validationKeys) {
        result[key] = requestHeaders[key] ?: ""
    }

    return result
}

internal fun HttpResponse.cacheExpires(): GMTDate {
    val cacheControl = cacheControl()

    val isPrivate = CacheControl.PRIVATE in cacheControl

    val maxAgeKey = if (isPrivate) "s-max-age" else "max-age"

    val maxAge = cacheControl.firstOrNull { it.value.startsWith(maxAgeKey) }
        ?.value?.split("=")
        ?.get(1)?.toInt()

    if (maxAge != null) {
        return call.response.requestTime + maxAge * 1000L
    }

    headers[HttpHeaders.Expires]?.fromHttpToGmtDate()?.let { return it }
    return GMTDate()
}

@KtorExperimentalAPI
internal fun HttpCacheEntry.shouldValidate(): Boolean {
    val cacheControl = responseHeaders[HttpHeaders.CacheControl]?.let { parseHeaderValue(it) } ?: emptyList()
    var result = GMTDate() > expires
    result = result || CacheControl.MUST_REVALIDATE in cacheControl
    result = result || CacheControl.NO_CACHE in cacheControl

    return result
}

private class HttpCacheCall(client: HttpClient) : HttpClientCall(client)

private class HttpCacheRequest(
    override val call: HttpCacheCall, origin: HttpRequest
) : HttpRequest by origin

private class HttpCacheResponse(
    override val call: HttpCacheCall, body: ByteArray, origin: HttpResponse
) : HttpResponse by origin {
    override val coroutineContext: CoroutineContext = origin.coroutineContext + Job()

    override val content: ByteReadChannel = ByteReadChannel(body)

    override fun close() {
        (coroutineContext[Job] as CompletableJob).complete()
    }
}
