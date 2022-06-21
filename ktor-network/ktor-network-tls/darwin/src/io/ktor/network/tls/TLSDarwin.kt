/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import kotlin.coroutines.*

public actual suspend fun Connection.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket {
    error("TLS is not supported on Darwin platform.")
}
