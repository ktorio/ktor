/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http.content

import io.ktor.server.application.*
import io.ktor.util.*

/**
 * Attribute that could be added to an application call to prevent its response from being compressed
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.SuppressionAttribute)
 */
@Deprecated(
    "Please use suppressCompression() and isCompressionSuppressed) instead",
    level = DeprecationLevel.ERROR
)
public val SuppressionAttribute: AttributeKey<Boolean> = AttributeKey("preventCompression")
internal val DecompressionSuppressionAttribute: AttributeKey<Boolean> = AttributeKey("preventDecompression")

/**
 * Suppress response body compression plugin for this [ApplicationCall].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.suppressCompression)
 */
@Suppress("DEPRECATION_ERROR")
public fun ApplicationCall.suppressCompression() {
    attributes.put(SuppressionAttribute, true)
}

/**
 * Suppresses the decompression for the current application call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.suppressDecompression)
 */
public fun ApplicationCall.suppressDecompression() {
    attributes.put(DecompressionSuppressionAttribute, true)
}

/**
 * Checks if response body compression is suppressed for this [ApplicationCall].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.isCompressionSuppressed)
 */
@Suppress("DEPRECATION_ERROR")
public val ApplicationCall.isCompressionSuppressed: Boolean get() = SuppressionAttribute in attributes

/**
 * Indicates whether decompression is suppressed for the current application call.
 * If decompression is suppressed, the plugin will not decompress the request body.
 *
 * To suppress decompression, use [suppressDecompression].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.http.content.isDecompressionSuppressed)
 */
public val ApplicationCall.isDecompressionSuppressed: Boolean get() = DecompressionSuppressionAttribute in attributes
