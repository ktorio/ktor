/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servicediscovery.consul

import io.ktor.server.servicediscovery.*
import io.ktor.servicediscovery.consul.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.servicediscovery.consul")

@OptIn(InternalAPI::class)
public fun ServiceDiscoveryPluginConfig.consul(configure: ConsulConfig.() -> Unit) {
    val config = ConsulConfig().apply(configure)

    val client = createConsulClient(config.connection)

    val serviceRegistry = ConsulServiceRegistry(client, config)
    val discoveryClient = ConsulDiscoveryClient(client, config.discovery, config.connection)

    LOGGER.trace { "Installed Consul service discovery plugin for service: ${config.registration.serviceName}" }
    register(serviceRegistry, discoveryClient)
}

/**
 * Root configuration for the Consul integration.
 * This class groups all major settings: connection, registration, and discovery behavior.
 *
 * Usage:
 * ```
 * consul {
 *     connection { ... }
 *     registration { ... }
 *     discovery { ... }
 * }
 * ```
 */
@KtorDsl
public class ConsulConfig {
    public val connection: ConsulConnectionConfig = ConsulConnectionConfig()
    public val registration: ConsulRegistrationConfig = ConsulRegistrationConfig()
    public val discovery: ConsulDiscoveryConfig = ConsulDiscoveryConfig()

    public fun connection(block: ConsulConnectionConfig.() -> Unit) {
        connection.apply(block)
    }

    public fun registration(block: ConsulRegistrationConfig.() -> Unit) {
        registration.apply(block)
    }

    public fun discovery(block: ConsulDiscoveryConfig.() -> Unit) {
        discovery.apply(block)
    }
}
