/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features

import kotlinx.coroutines.*

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpTimeout", "io.ktor.client.plugins.*")
)
public class HttpTimeout(
    private val requestTimeoutMillis: Long?,
    private val connectTimeoutMillis: Long?,
    private val socketTimeoutMillis: Long?
)

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("timeout(block)", "io.ktor.client.plugins.*")
)
public fun timeout(block: () -> Unit): Unit =
    error("Moved to io.ktor.client.plugins")

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpRequestTimeoutException", "io.ktor.client.plugins.*")
)
public class HttpRequestTimeoutException(request: Any) : CancellationException("")

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ConnectTimeoutException(request, cause)", "io.ktor.client.plugins.*")
)
public fun ConnectTimeoutException(
    request: Any,
    cause: Throwable? = null
): Unit = error("Moved to io.ktor.client.plugins")

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ConnectTimeoutException(url, timeout, cause)", "io.ktor.client.plugins.*")
)
public fun ConnectTimeoutException(
    url: String,
    timeout: Long?,
    cause: Throwable? = null
): Unit = error("Moved to io.ktor.client.plugins")

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("SocketTimeoutException(request, cause)", "io.ktor.client.plugins.*")
)
public fun SocketTimeoutException(
    request: Any,
    cause: Throwable? = null
): Unit = error("Moved to io.ktor.client.plugins")

