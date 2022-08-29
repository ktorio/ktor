/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

internal fun PluginBuilder<ContentNegotiationConfig>.convertRequestBody() {
    onCallReceive { call ->
        val registrations = pluginConfig.registrations
        val requestedType = call.receiveType

        if (requestedType.type in pluginConfig.ignoredTypes) return@onCallReceive

        transformBody { body: ByteReadChannel ->
            val requestContentType = try {
                call.request.contentType().withoutParameters()
            } catch (parseFailure: BadContentTypeFormatException) {
                throw BadRequestException(
                    "Illegal Content-Type header format: ${call.request.headers[HttpHeaders.ContentType]}",
                    parseFailure
                )
            }
            val suitableConverters = registrations
                .filter { converter -> requestContentType.match(converter.contentType) }
                .map { it.converter }
                .takeIf { it.isNotEmpty() } ?: return@transformBody body

            try {
                @OptIn(InternalAPI::class)
                suitableConverters.deserialize(body, requestedType, call.request.contentCharset() ?: Charsets.UTF_8)
            } catch (convertException: ContentConvertException) {
                throw BadRequestException(
                    convertException.message ?: "Can't convert parameters",
                    convertException.cause
                )
            }
        }
    }
}
