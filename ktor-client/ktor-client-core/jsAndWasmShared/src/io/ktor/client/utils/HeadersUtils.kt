/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders

/**
 * The body is already decompressed for wasm and js engine,
 * so we're dropping these headers
 * to make sure all the plugins and checks work with correct content length and encoding.
 */
internal fun HeadersBuilder.dropCompressionHeaders() {
    if (contains(HttpHeaders.ContentEncoding)) {
        remove(HttpHeaders.ContentEncoding)
        remove(HttpHeaders.ContentLength)
    }
}
