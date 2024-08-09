/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.request

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import java.io.InputStream

/**
 * Sets the [HttpRequestBuilder.url] from [url].
 */
public fun HttpRequestBuilder.url(url: java.net.URL): URLBuilder = this.url.takeFrom(url)

/**
 * Constructs a [HttpRequestBuilder] from [url].
 */
public operator fun HttpRequestBuilder.Companion.invoke(url: java.net.URL): HttpRequestBuilder =
    HttpRequestBuilder().apply { url(url) }

/**
 * Convert response body to input stream.
 */
@InternalAPI
public suspend fun HttpResponseBody.toInputStream(): InputStream =
    toChannel().toInputStream()
