/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*

private val DecompressionListAttribute: AttributeKey<MutableList<String>> = AttributeKey("DecompressionListAttribute")

/**
 * This function should be used for engines which apply decompression but don't drop compression headers
 * (like js and Curl) to make sure all the plugins and checks work with the correct content length and encoding.
 */
@InternalAPI
public fun HeadersBuilder.dropCompressionHeaders(method: HttpMethod, attributes: Attributes) {
    if (method == HttpMethod.Head || method == HttpMethod.Options) return
    val header = get(HttpHeaders.ContentEncoding) ?: return
    attributes.computeIfAbsent(DecompressionListAttribute) { mutableListOf<String>() }.add(header)
    remove(HttpHeaders.ContentEncoding)
    remove(HttpHeaders.ContentLength)
}
