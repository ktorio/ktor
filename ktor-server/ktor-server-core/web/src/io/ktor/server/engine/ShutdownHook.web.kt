/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import kotlin.concurrent.*

internal actual val SHUTDOWN_HOOK_ENABLED = true

/**
 * Adds automatic application shutdown hooks management. Should be used **before** starting the server.
 * Once application termination noticed, [stop] block will be executed.
 * Please note that a shutdown hook only registered when the application is running. If the application
 * is already stopped then there will be no hook and no [stop] function invocation possible.
 * So [stop] block will be called once or never.
 */
internal actual fun EmbeddedServer<*, *>.platformAddShutdownHook(stop: () -> Unit) {
    addProcessShutdownHook(stop)
}

private fun addProcessShutdownHook(block: () -> Unit): Unit = js("process.on('SIGTERM', block)")
