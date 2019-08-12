/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.pipeline

internal actual fun Throwable.withCause(cause: Throwable?): Throwable {
    if (cause == null || this.cause == cause) {
        return this
    }

    val result = tryCopyException(this, cause) ?: return this
    result.stackTrace = stackTrace

    return result
}
