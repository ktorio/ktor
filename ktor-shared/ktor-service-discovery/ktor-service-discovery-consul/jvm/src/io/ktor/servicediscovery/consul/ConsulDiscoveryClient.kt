/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.servicediscovery.consul

import com.ecwid.consul.v1.ConsulClient
import com.ecwid.consul.v1.QueryParams
import com.ecwid.consul.v1.catalog.CatalogServicesRequest
import com.ecwid.consul.v1.health.HealthServicesRequest
import com.ecwid.consul.v1.health.model.HealthService
import io.ktor.servicediscovery.*

public class ConsulDiscoveryClient(
    private val consulClient: ConsulClient,
    private val discovery: ConsulDiscoveryConfig,
    private val connection: ConsulConnectionConfig,
) : DiscoveryClient<ConsulServiceInstance> {
    override suspend fun getInstances(serviceId: String): List<ConsulServiceInstance> {
        val request = HealthServicesRequest.newBuilder()
            .setToken(connection.aclToken)
            .setPassing(discovery.queryPassingOnly)
            .apply {
                discovery.datacenter?.let { setDatacenter(it) }
                discovery.tags.takeIf { it.isNotEmpty() }?.let { setTags(it.toTypedArray()) }
            }
            .setQueryParams(
                QueryParams.Builder.builder()
                    .build()
            )
            .build()

        val services: List<HealthService> = consulClient
            .getHealthServices(serviceId, request)
            .value

        return services.map { service -> toKtorConsulInstanceService(service) }
    }

    override suspend fun getServiceIds(): List<String> {
        val request = CatalogServicesRequest.newBuilder()
            .setToken(connection.aclToken)
            .apply {
                discovery.datacenter?.let { setDatacenter(it) }
            }
            .build()

        return consulClient
            .getCatalogServices(request)
            .value
            .keys
            .toList()
    }

    public fun toKtorConsulInstanceService(service: HealthService): ConsulServiceInstance {
        return ConsulServiceInstance(
            instanceId = service.service.id,
            serviceId = service.service.id,
            host = service.service.address,
            port = service.service.port,
            metadata = service.service.tags.associate { tag ->
                val (key, value) = tag.split("=", limit = 2)
                key to value
            },
            tags = service.service.tags
        )
    }
}
