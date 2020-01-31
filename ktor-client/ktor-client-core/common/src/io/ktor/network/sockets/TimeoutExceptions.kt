/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.utils.io.errors.*

/**
 * This exception is thrown in case connect timeout exceeded.
 */
expect class ConnectTimeoutException(message: String, cause: Throwable? = null) : IOException

/**
 * This exception is thrown in case socket timeout (read or write) exceeded.
 */
expect class SocketTimeoutException(message: String, cause: Throwable? = null) : IOException

