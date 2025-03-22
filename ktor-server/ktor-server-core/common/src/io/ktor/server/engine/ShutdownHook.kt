/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.ApplicationStarting

internal expect val SHUTDOWN_HOOK_ENABLED: Boolean

/**
 * Adds automatic application shutdown hooks management. Should be used **before** starting the server.
 * Once application termination noticed, [stop] block will be executed.
 * Please note that a shutdown hook only registered when the application is running. If the application
 * is already stopped then there will be no hook and no [stop] function invocation possible.
 * So [stop] block will be called once or never.
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
 * Please note that a shutdown hook only registered when the application is running. If the application
 * is already stopped then there will be no hook and no [stop] function invocation possible.
 * So [stop] block will be called once or never.
 */
internal expect fun EmbeddedServer<*, *>.platformAddShutdownHook(stop: () -> Unit)
