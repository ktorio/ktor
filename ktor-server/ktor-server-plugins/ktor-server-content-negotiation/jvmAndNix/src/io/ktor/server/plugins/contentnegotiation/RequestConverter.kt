/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

internal fun ApplicationPluginBuilder<ContentNegotiationConfig>.convertRequestBody() {
    onCallReceive { call, receive ->
        val registrations = pluginConfig.registrations
        val requestedType = receive.typeInfo

        if (requestedType.type == ByteReadChannel::class) return@onCallReceive

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
                .takeIf { it.isNotEmpty() } ?: return@transformBody body

            val converted = try {
                // Pick the first one that can convert the subject successfully
                suitableConverters.firstNotNullOfOrNull { registration ->
                    registration.converter.deserialize(
                        charset = call.request.contentCharset() ?: Charsets.UTF_8,
                        typeInfo = requestedType,
                        content = receive.value as ByteReadChannel
                    )
                } ?: return@transformBody body
            } catch (convertException: ContentConvertException) {
                throw BadRequestException(
                    convertException.message ?: "Can't convert parameters",
                    convertException.cause
                )
            }

            return@transformBody ApplicationReceiveRequest(
                typeInfo = requestedType,
                value = converted
            )
        }
    }
}
