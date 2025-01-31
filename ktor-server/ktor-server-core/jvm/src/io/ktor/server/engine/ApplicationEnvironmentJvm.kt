/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.utils.io.*
import org.slf4j.*

/**
 * Builder for configuring the environment of the Ktor application.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ApplicationEnvironmentBuilder)
 */
@KtorDsl
public actual class ApplicationEnvironmentBuilder {
    /**
     * Root class loader.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ApplicationEnvironmentBuilder.classLoader)
     */
    public var classLoader: ClassLoader = ApplicationEnvironmentBuilder::class.java.classLoader

    /**
     * Application logger.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ApplicationEnvironmentBuilder.log)
     */
    public actual var log: Logger = LoggerFactory.getLogger("io.ktor.server.Application")

    /**
     * Configuration for the application.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ApplicationEnvironmentBuilder.config)
     */
    public actual var config: ApplicationConfig = MapApplicationConfig()

    /**
     * Builds and returns an instance of the application engine environment based on the configured settings.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ApplicationEnvironmentBuilder.build)
     */
    public actual fun build(): ApplicationEnvironment {
        return ApplicationEnvironmentImplJvm(classLoader, log, config)
    }
}

internal class ApplicationEnvironmentImplJvm(
    override val classLoader: ClassLoader,
    override val log: Logger,
    override val config: ApplicationConfig,
    @Deprecated(
        "Moved to Application",
        replaceWith = ReplaceWith("EmbeddedServer.monitor", "io.ktor.server.engine.EmbeddedServer"),
        level = DeprecationLevel.WARNING
    )
    override val monitor: Events = Events()
) : ApplicationEnvironment
