/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.ApplicationStarting

internal expect val SHUTDOWN_HOOK_ENABLED: Boolean

/**
 * Registers a [stop] block that runs when the process receives a termination signal
 * (JVM shutdown hook on JVM, POSIX signal on Native, process event on JS).
 * Should be called **before** starting the server.
 *
 * **Multiple hooks**
 * - On JVM and JS, each call registers an independent callback; all are executed on termination.
 * - On Native, each call replaces the previous callback; only the last registered [stop] block is kept.
 *
 * **Execution order**
 * - On JVM, the order is not specified. Each hook runs on its own thread; the JVM starts all shutdown hooks
 *   concurrently. Do not rely on registration order.
 * - On JS, callbacks run in registration order (FIFO) for each signal type (`SIGINT`, `SIGTERM`).
 *   Each call adds two listeners (one per signal); only listeners for the received signal run.
 * - On Native, the last registered callback wins.
 *
 * **What initiates shutdown**
 * - The external cause is a termination signal from the OS or runtime.
 * - The [stop] block is not a subscriber to lifecycle events. When invoked by the signal handler, it
 *   initiates graceful shutdown (typically by calling [EmbeddedServer.stop]), which then raises stop
 *   lifecycle events. Subscribers to [io.ktor.server.application.ApplicationStopping] and related events
 *   react to shut down already in progress; they do not replace [addShutdownHook] for signal handling.
 *
 * **Normal stop vs. signal-triggered stop**
 * - On JVM, when the application is stopped normally (e.g., by calling [EmbeddedServer.stop] directly),
 *   the shutdown hook is removed and the [stop] block is **not** called.
 * - On Native and JS, the listener is not removed on normal stop, but the [stop] block still runs.
 *
 * Please note that a shutdown hook is only registered when the application is running.
 * If the application has already stopped, there will be no hook and no [stop] function invocation possible.
 * Therefore, the [stop] block will be called **at most once**, or never.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.addShutdownHook)
 */
public fun EmbeddedServer<*, *>.addShutdownHook(stop: () -> Unit) {
    if (SHUTDOWN_HOOK_ENABLED) {
        monitor.subscribe(ApplicationStarting) { platformAddShutdownHook(stop) }
    }
}

/**
 * Platform-specific shutdown hook registration. See [addShutdownHook] for platform behavior details.
 * Please note that a shutdown hook is only registered when the application is running.
 */
internal expect fun EmbeddedServer<*, *>.platformAddShutdownHook(stop: () -> Unit)
