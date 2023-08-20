// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlin.coroutines.*

/**
 * Engine environment configuration builder
 */
@KtorDsl
public actual class ApplicationEnvironmentBuilder {
    /**
     * Parent coroutine context for an application
     */
    public actual var parentCoroutineContext: CoroutineContext = EmptyCoroutineContext

    /**
     * Application logger
     */
    public actual var log: Logger = KtorSimpleLogger("io.ktor.server.Application")

    /**
     * Application config
     */
    public actual var config: ApplicationConfig = MapApplicationConfig()

    /**
     * Build an application engine environment
     */
    public actual fun build(shouldReload: Boolean): ApplicationEnvironment {
        return ApplicationEngineEnvironmentImplNix(log, config, parentCoroutineContext)
    }
}

public class ApplicationEngineEnvironmentImplNix(
    override val log: Logger,
    override val config: ApplicationConfig,
    override val parentCoroutineContext: CoroutineContext,
) : ApplicationEnvironment
