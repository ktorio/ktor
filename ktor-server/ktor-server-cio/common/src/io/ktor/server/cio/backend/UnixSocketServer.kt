/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.backend

import io.ktor.network.sockets.*
import io.ktor.server.cio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Duration.Companion.seconds

/**
 * Start an http server with [settings] invoking [handler] for every request
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.backend.unixSocketServer)
 */
public fun CoroutineScope.unixSocketServer(
    settings: UnixSocketServerSettings,
    handler: HttpRequestHandler
): HttpServer = launchServer(
    settings.socketPath,
    settings.connectionIdleTimeoutSeconds.seconds,
    handler,
) { selector ->
    val socketFile = Path(settings.socketPath)
    if (SystemFileSystem.exists(socketFile)) {
        SystemFileSystem.delete(socketFile)
    }

    aSocket(selector).tcp().bind(UnixSocketAddress(settings.socketPath))
}
