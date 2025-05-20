/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servicediscovery.consul

import com.ecwid.consul.ConsulException
import com.ecwid.consul.v1.ConsulClient
import com.ecwid.consul.v1.agent.model.NewService
import io.ktor.server.servicediscovery.*
import io.ktor.servicediscovery.consul.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*

public class ConsulServiceRegistry internal constructor(
    private val consulClient: ConsulClient,
    private val configuration: ConsulConfig,
) : ServiceRegistry<ConsulServiceInstance> {

    public fun add(block: ConsulServiceInstance.Builder.() -> Unit): Boolean {
        val builder = ConsulServiceInstance.Builder()
        builder.block()
        return add(builder.build())
    }

    override fun add(instance: ConsulServiceInstance): Boolean {
        val service = createConsulService(instance)
        return try {
            consulClient.agentServiceRegister(service, configuration.connection.aclToken)
            LOGGER.trace { "Registered service ${service.name} (${service.id}) to Consul at ${service.address}:${service.port}" }
            true
        } catch (e: ConsulException) {
            LOGGER.trace { "Failed to register service with Consul: $e" }
            false
        }
    }

    private fun createConsulService(instance: ConsulServiceInstance): NewService {
        return NewService().apply {
            id = instance.instanceId
            name = instance.serviceId
            address = instance.host
            port = instance.port
            tags = instance.tags
            meta = instance.metadata
            checks = instance.healthChecks.map { createConsulCheck(instance, it) }.toList()
        }
    }

    private fun createConsulCheck(
        instance: ConsulServiceInstance,
        healthCheck: ConsulServiceInstance.HealthCheck
    ): NewService.Check {
        return NewService.Check().apply {
            http = "${instance.host}:${instance.port}/${healthCheck.path}"
            interval = healthCheck.interval.inWholeMilliseconds.toString() + "ms"
            timeout = healthCheck.timeout.inWholeMilliseconds.toString() + "ms"
            method = healthCheck.method.value
            header = healthCheck.headers
            deregisterCriticalServiceAfter =
                healthCheck.deregisterCriticalServiceAfter.inWholeMilliseconds.toString() + "ms"
        }
    }

    override fun remove(instanceId: String): Boolean {
        return try {
            consulClient.agentServiceDeregister(instanceId, configuration.connection.aclToken)
            true
        } catch (e: ConsulException) {
            LOGGER.trace { "Failed to remove service from Consul: $e" }
            false
        }
    }

    @InternalAPI
    override fun autoAddService() {
        val registration = configuration.registration
        add {
            instanceId = registration.instanceId
            serviceId = registration.serviceName
            host = registration.address
            port = registration.port
            tags = registration.tags
            meta = registration.metadata
        }
    }

    @InternalAPI
    override fun autoRemoveService() {
        remove(configuration.registration.instanceId)
    }
}
