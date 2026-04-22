/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.*

internal actual val SHUTDOWN_HOOK_ENABLED = true

private val shutdownHook: AtomicReference<() -> Unit> = AtomicReference {}

/**
 * Registers a POSIX signal handler for shutdown. Each call replaces the previous callback;
 * only the last registered [stop] block is kept; the built-in [EmbeddedServer.start] hook typically wins.
 * The listener is not removed on normal stop, but the [stop] block still runs at most once.
 * Please note that a shutdown hook is only registered when the application is running.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun EmbeddedServer<*, *>.platformAddShutdownHook(stop: () -> Unit) {
    val shouldStop = AtomicReference(true)
    shutdownHook.value = {
        if (shouldStop.compareAndSet(true, false)) {
            stop()
        }
    }

    signal(
        SIGINT,
        staticCFunction<Int, Unit> {
            shutdownHook.value()
        }
    )
    signal(
        SIGTERM,
        staticCFunction<Int, Unit> {
            shutdownHook.value()
        }
    )
}
