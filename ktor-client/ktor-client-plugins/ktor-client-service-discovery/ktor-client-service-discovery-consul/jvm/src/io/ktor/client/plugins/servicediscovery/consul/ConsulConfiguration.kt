/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.servicediscovery.consul

import io.ktor.client.plugins.servicediscovery.*
import io.ktor.servicediscovery.consul.ConsulConnectionConfig
import io.ktor.servicediscovery.consul.ConsulDiscoveryClient
import io.ktor.servicediscovery.consul.ConsulDiscoveryConfig
import io.ktor.servicediscovery.consul.createConsulClient
import io.ktor.util.logging.*
import io.ktor.utils.io.*

@OptIn(InternalAPI::class)
public fun ServiceDiscoveryConfig.consul(configure: ConsulConfig.() -> Unit) {
    val config = ConsulConfig().apply(configure)

    val client = createConsulClient(config.connection)

    discoveryClient = ConsulDiscoveryClient(client, config.discovery, config.connection)
}

@KtorDsl
public class ConsulConfig {
    internal val connection: ConsulConnectionConfig = ConsulConnectionConfig()
    internal val discovery: ConsulDiscoveryConfig = ConsulDiscoveryConfig()

    public fun connection(block: ConsulConnectionConfig.() -> Unit) {
        connection.apply(block)
    }

    public fun discovery(block: ConsulDiscoveryConfig.() -> Unit) {
        discovery.apply(block)
    }
}
