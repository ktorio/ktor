// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.utils.io.*
import org.slf4j.*

/**
 * Builder for configuring the environment of the Ktor application.
 */
@KtorDsl
public actual class ServerEnvironmentBuilder {
    /**
     * Root class loader.
     */
    public var classLoader: ClassLoader = ServerEnvironmentBuilder::class.java.classLoader

    /**
     * Application logger.
     */
    public actual var log: Logger = LoggerFactory.getLogger("io.ktor.server.Application")

    /**
     * Configuration for the application.
     */
    public actual var config: ServerConfig = MapServerConfig()

    /**
     * Builds and returns an instance of the application engine environment based on the configured settings.
     */
    public actual fun build(): ServerEnvironment {
        return ServerEnvironmentImplJvm(classLoader, log, config)
    }
}

internal class ServerEnvironmentImplJvm(
    override val classLoader: ClassLoader,
    override val log: Logger,
    override val config: ServerConfig,
    @Deprecated(
        "Moved to Server",
        replaceWith = ReplaceWith("EmbeddedServer.monitor", "io.ktor.server.engine.EmbeddedServer"),
        level = DeprecationLevel.WARNING
    )
    override val monitor: Events = Events()
) : ServerEnvironment
