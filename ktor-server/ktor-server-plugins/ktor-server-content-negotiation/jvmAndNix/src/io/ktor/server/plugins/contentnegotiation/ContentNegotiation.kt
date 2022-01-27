/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

/**
 * Functional type for accepted content types contributor
 * @see ContentNegotiation.Configuration.accept
 */
public typealias AcceptHeaderContributor = (
    call: ApplicationCall,
    acceptedContentTypes: List<ContentTypeWithQuality>
) -> List<ContentTypeWithQuality>

/**
 * Pair of [ContentType] and [quality] usually parsed from [HttpHeaders.Accept] headers.
 * @param contentType
 * @param quality
 */
public data class ContentTypeWithQuality(val contentType: ContentType, val quality: Double = 1.0) {
    init {
        require(quality in 0.0..1.0) { "Quality should be in range [0, 1]: $quality" }
    }
}

/**
 * This plugin provides automatic content conversion according to Content-Type and Accept headers
 *
 * See normative documents:
 *
 * * https://tools.ietf.org/html/rfc7231#section-5.3
 * * https://developer.mozilla.org/en-US/docs/Web/HTTP/Content_negotiation
 */
public val ContentNegotiation: RouteScopedPlugin<ContentNegotiationConfig, PluginInstance> = createRouteScopedPlugin(
    "ContentNegotiation",
    { ContentNegotiationConfig() }
) {
    convertRequestBody()
    convertResponseBody()
}

/**
 * Detect suitable charset for an application call by `Accept` header or fallback to [defaultCharset]
 */
public fun ApplicationCall.suitableCharset(defaultCharset: Charset = Charsets.UTF_8): Charset {
    for ((charset, _) in request.acceptCharsetItems()) when {
        charset == "*" -> return defaultCharset
        Charset.isSupported(charset) -> return Charset.forName(charset)
    }
    return defaultCharset
}
