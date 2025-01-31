/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.network.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Engine environment configuration builder
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ApplicationEnvironmentBuilder)
 */
@KtorDsl
public expect class ApplicationEnvironmentBuilder() {

    /**
     * Application logger
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ApplicationEnvironmentBuilder.log)
     */
    public var log: Logger

    /**
     * Application config
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ApplicationEnvironmentBuilder.config)
     */
    public var config: ApplicationConfig

    /**
     * Build an application engine environment
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ApplicationEnvironmentBuilder.build)
     */
    public fun build(): ApplicationEnvironment
}

public fun applicationEnvironment(
    block: ApplicationEnvironmentBuilder.() -> Unit = {}
): ApplicationEnvironment {
    return ApplicationEnvironmentBuilder().apply(block).build()
}
