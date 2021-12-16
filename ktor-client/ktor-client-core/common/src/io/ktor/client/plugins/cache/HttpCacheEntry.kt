/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.cache

import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.datetime.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAPI::class)
internal suspend fun HttpCacheEntry(response: HttpResponse): HttpCacheEntry {
    val body = response.content.readRemaining().readBytes()
    response.complete()
    return HttpCacheEntry(response.cacheExpires(), response.varyKeys(), response, body)
}

/**
 * Client single response cache with [expires] and [varyKeys].
 */
public class HttpCacheEntry internal constructor(
    public val expires: Instant,
    public val varyKeys: Map<String, String>,
    public val response: HttpResponse,
    public val body: ByteArray
) {
    internal val responseHeaders: Headers = Headers.build {
        appendAll(response.headers)
    }

    internal fun produceResponse(): HttpResponse {
        val currentClient = response.call.client ?: error("Failed to save response in cache in different thread.")
        val call = SavedHttpCall(currentClient, body)
        call.response = SavedHttpResponse(call, body, response)
        call.request = SavedHttpRequest(call, response.call.request)

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

@OptIn(ExperimentalTime::class)
internal fun HttpResponse.cacheExpires(fallback: () -> Instant = { call.clock.now() }): Instant {
    val cacheControl = cacheControl()

    val isPrivate = CacheControl.PRIVATE in cacheControl

    val maxAgeKey = if (isPrivate) "s-max-age" else "max-age"

    val maxAge = cacheControl.firstOrNull { it.value.startsWith(maxAgeKey) }
        ?.value?.split("=")
        ?.get(1)?.toInt()?.seconds

    if (maxAge != null) {
        return call.response.requestTime + maxAge
    }

    val expires = headers[HttpHeaders.Expires]
    return expires?.let {
        // Handle "0" case faster
        if (it == "0" || it.isBlank()) return fallback()

        return try {
            it.fromHttpToGmtDate()
        } catch (e: Throwable) {
            fallback()
        }
    } ?: fallback()
}

internal fun HttpCacheEntry.shouldValidate(): Boolean {
    val cacheControl = responseHeaders[HttpHeaders.CacheControl]?.let { parseHeaderValue(it) } ?: emptyList()
    val isStale = response.call.clock.now() > expires
    // must-revalidate; re-validate once STALE, and MUST NOT return a cached response once stale.
    //  This is how majority of clients implement the RFC
    //  OkHttp Implements this the same: https://github.com/square/okhttp/issues/4043#issuecomment-403679369
    // Disabled for now, as we don't currently return a cached object when there's a network failure; must-revalidate
    // works the same as being stale on the request side. On response side, must-revalidate would not return a cached
    // object if we are stale and couldn't refresh.
    // isStale = isStale && CacheControl.MUST_REVALIDATE in cacheControl
    return isStale || CacheControl.NO_CACHE in cacheControl
}
