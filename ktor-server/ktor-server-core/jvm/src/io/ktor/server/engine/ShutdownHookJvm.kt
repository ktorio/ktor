/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.server.application.*
import java.util.concurrent.atomic.*

internal actual val SHUTDOWN_HOOK_ENABLED = System.getProperty("io.ktor.server.engine.ShutdownHook", "true") == "true"

/**
 * Configures automatic management of JVM shutdown hooks for terminating an application.
 * This function should be invoked before starting the server.
 * Once the JVM termination is detected, the [stop] block will be executed.
 * Please note that a shutdown hook is registered only while the application is running.
 * If the application has already stopped, the hook won't be registered, and invoking [stop] will have no effect.
 * Therefore, the [stop] block will be called either once or not at all.
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
