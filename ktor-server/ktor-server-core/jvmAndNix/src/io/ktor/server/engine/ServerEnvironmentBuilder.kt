/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*

/**
 * Engine environment configuration builder
 */
@KtorDsl
public expect class ServerEnvironmentBuilder() {

    /**
     * Application logger
     */
    public var log: Logger

    /**
     * Application config
     */
    public var config: ServerConfig

    /**
     * Build an application engine environment
     */
    public fun build(): ServerEnvironment
}

public fun serverEnvironment(
    block: ServerEnvironmentBuilder.() -> Unit = {}
): ServerEnvironment {
    return ServerEnvironmentBuilder().apply(block).build()
}
