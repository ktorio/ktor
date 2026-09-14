/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.contentnegotiation

import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*

/**
 * Specifies which [converter] to use for a particular [contentType].
 * @param contentType is an instance of [ContentType] for this registration
 * @param converter is an instance of [ContentConverter] for this registration
 */
internal class ConverterRegistration(val contentType: ContentType, val converter: ContentConverter)

internal fun ApplicationCall.parseAcceptHeader(): List<ContentTypeWithQuality> {
    val acceptHeaderContent = request.header(HttpHeaders.Accept)

    return try {
        parseHeaderValue(acceptHeaderContent).map { ContentTypeWithQuality(ContentType.parse(it.value), it.quality) }
    } catch (parseFailure: BadContentTypeFormatException) {
        throw BadRequestException("Illegal Accept header format: $acceptHeaderContent", parseFailure)
    }
}

internal fun checkAcceptHeader(
    acceptItems: List<ContentTypeWithQuality>,
    contentType: ContentType?
): Boolean {
    if (acceptItems.isEmpty() || contentType == null) return true

    // The most specific media range that matches decides, and a qvalue of 0 there
    // means "not acceptable", so it must not make the content type acceptable
    // (RFC 9110, 12.5.1).
    val matching = acceptItems.filter { contentType.match(it.contentType) }
    if (matching.isEmpty()) return false

    val specificity = matching.maxOf { it.specificity }
    return matching.any { it.specificity == specificity && it.quality > 0.0 }
}

// How specific the media range is: a wildcard type is the least specific one,
// then a wildcard subtype, then a fully specified type/subtype.
private val ContentTypeWithQuality.specificity: Int
    get() = when {
        contentType.contentType == "*" -> 0
        contentType.contentSubtype == "*" -> 1
        else -> 2
    }
