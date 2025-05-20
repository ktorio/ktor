/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servicediscovery

import io.ktor.server.application.*
import io.ktor.server.application.hooks.MonitoringEvent
import io.ktor.servicediscovery.*
import io.ktor.utils.io.InternalAPI

@OptIn(InternalAPI::class)
public val ServiceDiscovery: ApplicationPlugin<ServiceDiscoveryPluginConfig> = createApplicationPlugin(
    name = "ServiceDiscovery",
    createConfiguration = ::ServiceDiscoveryPluginConfig
) {
    val config = pluginConfig

    val registry = config.serviceRegistry ?: error("No ServiceRegistry provided")
    val client = config.discoveryClient ?: error("No DiscoveryClient provided")

    application.attributes.put(ServiceRegistryKey, registry)
    application.attributes.put(DiscoveryClientKey, client)

    if (config.addOnStart) {
        on(MonitoringEvent(ApplicationStarted)) {
            registry.autoAddService()
        }
    }

    if (config.removeOnStop) {
        on(MonitoringEvent(ApplicationStopped)) {
            registry.autoRemoveService()
        }
    }
}

public inline fun <reified T : DiscoveryClient<out ServiceInstance>> Application.getDiscoveryClient(): T {
    val client = attributes[DiscoveryClientKey]
    return client as? T
        ?: throw IllegalStateException("Expected discovery client of type ${T::class.simpleName} but found ${client::class.simpleName}")
}

public inline fun <reified T : ServiceRegistry<out ServiceInstance>> Application.getServiceRegistry(): T {
    val registry = attributes[ServiceRegistryKey]
    return registry as? T
        ?: throw IllegalStateException("Expected service registry of type ${T::class.simpleName} but found ${registry::class.simpleName}")
}
