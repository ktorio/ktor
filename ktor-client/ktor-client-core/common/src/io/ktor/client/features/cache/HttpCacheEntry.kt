/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.cache

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

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
        val call = SavedHttpCall(response.call.client)
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
