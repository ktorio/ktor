/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import kotlinx.io.*

public actual class SocketTimeoutException(
    message: String,
    cause: Throwable?
) : IOException(message, cause) {
    public actual constructor(message: String) : this(message, null)
}
