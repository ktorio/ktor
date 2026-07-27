/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlin.js.*

internal actual val SHUTDOWN_HOOK_ENABLED = true

/**
 * Registers process event listeners for shutdown. Each call adds independent listeners;
 * all are executed on termination in registration order per signal type.
 * The listeners are not removed on normal stop, but the [stop] block still runs at most once.
 * Please note that a shutdown hook is only registered when the application is running.
 */
internal actual fun EmbeddedServer<*, *>.platformAddShutdownHook(stop: () -> Unit) {
    addProcessShutdownHook(stop)
}

private fun addProcessShutdownHook(block: () -> Unit) {
    var called = false
    val guardedBlock = {
        if (!called) {
            called = true
            block()
        }
    }
    processOn("SIGTERM", guardedBlock)
    processOn("SIGINT", guardedBlock)
}

private fun processOn(signal: String, block: () -> Unit) {
    js("process.on(signal, block)")
}
