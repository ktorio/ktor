/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.fatures.retry

import io.ktor.client.features.*
import io.ktor.util.network.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*


internal fun throwErrorOfCollected(retries: Int, collectedErrors: List<Throwable>): Nothing {
    val cause = Retry.RequestRetriesExceededException(
        "Request $retries retries limit exceeded attempting to request",
        cause = collectedErrors.singleOrNull(),
        retries
    )

    if (collectedErrors.size > 1) {
        collectedErrors.forEach { subCause ->
            cause.addSuppressed(subCause)
        }
    }

    throw cause
}


internal fun Result<*>.checkExceptionOrThrowIfNotWhitelisted(): Throwable? {
    val cause = exceptionOrNull()?.unwrapCancellation() ?: return null

    return when (cause) {
        is IOException,
        is HttpRequestTimeoutException,
        is ResponseException,
        is UnresolvedAddressException -> cause
        else -> throw cause
    }
}

internal fun Throwable.unwrapCancellation(): Throwable = when (this) {
    is CancellationException -> cause?.unwrapCancellation() ?: this
    else -> this
}
