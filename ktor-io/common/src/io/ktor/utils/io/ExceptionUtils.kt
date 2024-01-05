/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

/**
 * Print exception stacktrace.
 */
@Deprecated(
    "This function is replaced by the same in the standard library",
    replaceWith = ReplaceWith("printStackTrace()")
)
public expect fun Throwable.printStack()

internal fun Throwable.unwrapCancellationException(): Throwable {
    var exception: Throwable = this
    while (exception is CancellationException) {
        if (exception == exception.cause) {
            return this
        }

        exception = exception.cause ?: return exception
    }

    return exception
}
