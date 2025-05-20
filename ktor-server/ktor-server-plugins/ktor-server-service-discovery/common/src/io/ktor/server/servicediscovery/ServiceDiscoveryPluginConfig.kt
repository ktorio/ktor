/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servicediscovery

import io.ktor.servicediscovery.*

public class ServiceDiscoveryPluginConfig {
    /**
     * The discovery client implementation
     */
    public var discoveryClient: DiscoveryClient<out ServiceInstance>? = null

    /**
     * The service registry implementation
     */
    public var serviceRegistry: ServiceRegistry<out ServiceInstance>? = null

    public var addOnStart: Boolean = true
    public var removeOnStop: Boolean = true

    public fun register(
        registryFactory: ServiceRegistry<out ServiceInstance>,
        clientFactory: DiscoveryClient<out ServiceInstance>
    ) {
        serviceRegistry = registryFactory
        discoveryClient = clientFactory
    }
}
