/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.request.*
import io.ktor.utils.io.errors.*

/**
 * HTTP connect timeout exception.
 */
@Suppress("ACTUAL_WITHOUT_EXPECT")
actual class HttpConnectTimeoutException actual constructor(request: HttpRequestData) :
    IOException(
        "Connect timeout has been expired [url=${request.url}, connect_timeout=${request.getCapabilityOrNull(
            HttpTimeout
        )?.connectTimeoutMillis ?: "unknown"} ms]"
    )

/**
 * HTTP socket timeout exception.
 */
@Suppress("ACTUAL_WITHOUT_EXPECT")
actual class HttpSocketTimeoutException actual constructor(request: HttpRequestData) :
    IOException(
        "Socket timeout has been expired [url=${request.url}, socket_timeout=${request.getCapabilityOrNull(
            HttpTimeout
        )?.socketTimeoutMillis ?: "unknown"}] ms"
    )
