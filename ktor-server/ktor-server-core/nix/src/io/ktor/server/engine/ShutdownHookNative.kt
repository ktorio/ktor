/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.*

private val shutdownHook: AtomicReference<() -> Unit> = AtomicReference {}

/**
 * Adds automatic application shutdown hooks management. Should be used **before** starting the engine.
 * Once application termination noticed, [stop] block will be executed.
 * Please note that a shutdown hook only registered when the application is running. If the application
 * is already stopped then there will be no hook and no [stop] function invocation possible.
 * So [stop] block will be called once or never.
 */
@OptIn(ExperimentalForeignApi::class)
public actual fun ApplicationEngine.addShutdownHook(stop: () -> Unit) {
    shutdownHook.value = stop

    signal(
        SIGINT,
        staticCFunction<Int, Unit> {
            shutdownHook.value()
        }
    )
}
