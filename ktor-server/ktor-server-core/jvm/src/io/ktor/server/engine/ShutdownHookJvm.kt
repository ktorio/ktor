/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.server.application.*
import java.util.concurrent.atomic.*

internal actual val SHUTDOWN_HOOK_ENABLED = System.getProperty("io.ktor.server.engine.ShutdownHook", "true") == "true"

/**
 * Registers a JVM shutdown hook. Each call adds an independent hook; all are executed on termination.
 * Hooks run concurrently; execution order is not specified.
 * On normal stop, the hook is removed via [Runtime.removeShutdownHook] when [ApplicationStopping] is raised.
 * The [stop] block runs at most once.
 * Please note that a shutdown hook is only registered when the application is running.
 */
internal actual fun EmbeddedServer<*, *>.platformAddShutdownHook(stop: () -> Unit) {
    val hook = ShutdownHook(stop)
    monitor.subscribe(ApplicationStopping) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook)
        } catch (alreadyShuttingDown: IllegalStateException) {
            // ignore
        }
    }

    Runtime.getRuntime().addShutdownHook(hook)
}

private class ShutdownHook(private val stopFunction: () -> Unit) : Thread("KtorShutdownHook") {
    private val shouldStop = AtomicBoolean(true)

    override fun run() {
        if (shouldStop.compareAndSet(true, false)) {
            stopFunction()
        }
    }
}
