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
 * Multiple shutdown hooks can be registered by calling this function more than once —
 * each call adds an independent hook. For example, different application modules can each
 * register their own cleanup logic.
 *
 * The [stop] block is **not** a listener for lifecycle events — it is the **cause** of the
 * shutdown sequence. When the OS or runtime delivers a termination signal, the platform
 * calls [stop], which in turn calls [EmbeddedServer.stop]. That call raises
 * [io.ktor.server.application.ApplicationStopPreparing],
 * [io.ktor.server.application.ApplicationStopping], and
 * [io.ktor.server.application.ApplicationStopped] in that order.
 *
 * If the application is stopped normally (e.g., by calling [EmbeddedServer.stop] directly),
 * the hook is deregistered during shutdown and the [stop] block is **not** called.
 *
 * The hook is only registered while the application is running. If the application has already stopped,
 * no hook is registered and [stop] will never be called.
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
 * Adds automatic application shutdown hooks management. Should be used **before** starting the server.
 * Once application termination noticed, [stop] block will be executed.
 * Please note that a shutdown hook is only registered when the application is running. If the application
 * is already stopped then there will be no hook and no [stop] function invocation possible.
 * So [stop] block will be called once or never.
 */
internal expect fun EmbeddedServer<*, *>.platformAddShutdownHook(stop: () -> Unit)
