/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.util.*

internal actual fun Throwable.mapToKtor(request: HttpRequestData): Throwable = when (cause?.rootCause) {
    is java.net.SocketTimeoutException -> SocketTimeoutException(request, cause)
    else -> cause
} ?: this
