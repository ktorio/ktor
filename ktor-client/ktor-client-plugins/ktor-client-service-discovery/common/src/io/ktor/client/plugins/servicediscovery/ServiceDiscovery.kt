/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.servicediscovery

import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.http.*
import io.ktor.servicediscovery.*
import io.ktor.utils.io.*

@KtorDsl
public class ServiceDiscoveryConfig {
    /**
     * The discovery client implementation
     */
    public var discoveryClient: DiscoveryClient<out ServiceInstance>? = null
}

public val ServiceDiscovery: ClientPlugin<ServiceDiscoveryConfig> = createClientPlugin(
    "ServiceDiscovery",
    ::ServiceDiscoveryConfig
) {
    val discoveryClient = pluginConfig.discoveryClient ?: error("No DiscoveryClient provided")
    client.attributes.put(DiscoveryClientKey, discoveryClient)

    onRequest { request, _ ->
        val url = request.url
        if (url.protocol.name == "service") {
            val serviceName = url.host

            val services = discoveryClient.getInstances(serviceName)
            if (services.isEmpty()) {
                throw IllegalStateException("No instances found for service: $serviceName")
            }

            val service = services.first()

            request.url.protocol = URLProtocol.HTTP
            request.url.host = service.host
            request.url.port = service.port
        }
    }
}

public inline fun <reified T : DiscoveryClient<out ServiceInstance>> HttpClient.getDiscoveryClient(): T {
    val client = attributes[DiscoveryClientKey]
    return client as? T
        ?: throw IllegalStateException("Expected discovery client of type ${T::class.simpleName} but found ${client::class.simpleName}")
}
