/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.utils.io.charsets.*

private val NOT_ACCEPTABLE = HttpStatusCodeContent(HttpStatusCode.NotAcceptable)

internal fun PluginBuilder<ContentNegotiationConfig>.convertResponseBody() = onCallRespond { call, subject ->
    if (subject is OutgoingContent || subject::class in pluginConfig.ignoredTypes) return@onCallRespond
    if (call.response.responseType == null) return@onCallRespond

    val registrations = pluginConfig.registrations
    val checkAcceptHeader = pluginConfig.checkAcceptHeaderCompliance

    transformBody {
        val acceptHeader = call.parseAcceptHeader()

        val acceptItems: List<ContentTypeWithQuality> = this@onCallRespond.pluginConfig
            .acceptContributors
            .fold(acceptHeader) { result, contributor -> contributor(call, result) }
            .distinct()
            .sortedByQuality()

        val suitableConverters = if (acceptItems.isEmpty()) {
            // all converters are suitable since client didn't indicate what it wants
            registrations
        } else {
            // select converters that match specified Accept header, in order of quality
            acceptItems.flatMap { (contentType, _) ->
                registrations.filter { it.contentType.match(contentType) }
            }.distinct()
        }

        val acceptCharset = call.request.headers.suitableCharsetOrNull()

        // Pick the first one that can convert the subject successfully
        val converted: OutgoingContent? = suitableConverters.firstNotNullOfOrNull {
            val contentType = acceptCharset?.let { charset ->
                it.contentType.withCharset(charset)
            }
            it.converter.serialize(
                contentType = contentType ?: it.contentType,
                charset = acceptCharset ?: Charsets.UTF_8,
                typeInfo = call.response.responseType!!,
                value = subject
            )
        }

        val rendered = converted?.let { transformDefaultContent(call, it) } ?: return@transformBody subject

        if (checkAcceptHeader && !checkAcceptHeader(acceptItems, rendered.contentType)) {
            return@transformBody NOT_ACCEPTABLE
        }

        return@transformBody rendered
    }
}

/**
 * Returns a list of content types sorted by the quality, number of asterisks, and number of parameters.
 * @see parseAndSortContentTypeHeader
 */
private fun List<ContentTypeWithQuality>.sortedByQuality(): List<ContentTypeWithQuality> = sortedWith(
    compareByDescending<ContentTypeWithQuality> { it.quality }.thenBy {
        val contentType = it.contentType
        var asterisks = 0
        if (contentType.contentType == "*") {
            asterisks += 2
        }
        if (contentType.contentSubtype == "*") {
            asterisks++
        }
        asterisks
    }.thenByDescending { it.contentType.parameters.size }
)
