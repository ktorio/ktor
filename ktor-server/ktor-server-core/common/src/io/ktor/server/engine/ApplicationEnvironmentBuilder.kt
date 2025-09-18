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

/**
 * Configures the application environment using the provided configuration file paths.
 *
 * If no paths are provided, the default configuration is loaded.
 * If one path is provided, the corresponding configuration file is loaded.
 * If multiple paths are provided, the configurations are merged in the given order.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.configure)
 *
 * @param configPaths Optional paths to configuration files.
 */
public fun ApplicationEnvironmentBuilder.configure(vararg configPaths: String) {
    config = ConfigLoader.loadAll(*configPaths)
}

/**
 * Configures the application environment builder by merging the provided configurations.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.configure)
 *
 * @param configs A variable number of [ApplicationConfig] instances to be merged and set as the builder's configuration.
 */
public fun ApplicationEnvironmentBuilder.configure(vararg configs: ApplicationConfig) {
    config = configs.reduce(ApplicationConfig::mergeWith)
}
