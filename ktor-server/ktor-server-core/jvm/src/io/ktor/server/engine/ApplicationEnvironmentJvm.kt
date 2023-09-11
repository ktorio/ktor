// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.slf4j.*
import kotlin.coroutines.*

/**
 * Engine environment configuration builder
 */
@KtorDsl
public actual class ApplicationEnvironmentBuilder {
    /**
     * Root class loader
     */
    public var classLoader: ClassLoader = ApplicationEnvironmentBuilder::class.java.classLoader

    /**
     * Application logger
     */
    public actual var log: Logger = LoggerFactory.getLogger("io.ktor.server.Application")

    /**
     * Application config
     */
    public actual var config: ApplicationConfig = MapApplicationConfig()

    /**
     * Build an application engine environment
     */
    public actual fun build(): ApplicationEnvironment {
        return ApplicationEnvironmentImplJvm(classLoader, log, config)
    }
}

internal class ApplicationEnvironmentImplJvm(
    override val classLoader: ClassLoader,
    override val log: Logger,
    override val config: ApplicationConfig,
) : ApplicationEnvironment
