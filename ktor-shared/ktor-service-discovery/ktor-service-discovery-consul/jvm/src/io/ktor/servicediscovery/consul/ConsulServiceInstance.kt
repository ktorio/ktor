/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.servicediscovery.consul

import io.ktor.http.*
import io.ktor.servicediscovery.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public class ConsulServiceInstance(
    override val instanceId: String,
    override val serviceId: String,
    override val host: String,
    override val port: Int,
    override val metadata: Map<String, String> = emptyMap(),
    public val tags: List<String> = emptyList(),
    public val healthChecks: List<HealthCheck> = emptyList()
) : ServiceInstance {

    /**
     * Health check configuration
     */
    public class HealthCheck(
        public val path: String = "/health",
        public val interval: Duration = 10.seconds,
        public val timeout: Duration = 5.seconds,
        public val method: HttpMethod = HttpMethod.Get,
        public val headers: Map<String, List<String>> = emptyMap(),
        public val deregisterCriticalServiceAfter: Duration = 30.seconds,
        public val tls: Boolean = false
    )

    /**
     * Builder for ConsulServiceInstance
     */
    public class Builder {
        public var instanceId: String? = null
        public var serviceId: String? = null
        public var scheme: String? = null
        public var host: String? = null
        public var port: Int? = null
        public var tags: List<String> = emptyList()
        public var meta: Map<String, String> = emptyMap()
        private var healthChecks: MutableList<HealthCheck> = mutableListOf()

        /**
         * Configure health check
         */
        public fun healthCheck(block: HealthCheckBuilder.() -> Unit) {
            val builder = HealthCheckBuilder()
            builder.block()
            healthChecks.add(builder.build())
        }

        /**
         * Build a ConsulServiceInstance
         */
        public fun build(): ConsulServiceInstance {
            requireNotNull(instanceId) { "instanceId is required" }
            requireNotNull(serviceId) { "serviceId is required" }
            requireNotNull(host) { "host is required" }
            requireNotNull(port) { "port is required" }

            return ConsulServiceInstance(
                serviceId = serviceId!!,
                instanceId = instanceId!!,
                host = host!!,
                port = port!!,
                tags = tags,
                metadata = meta,
                healthChecks = healthChecks
            )
        }

        /**
         * Builder for health check configuration
         */
        public class HealthCheckBuilder {
            public var path: String = "/health"
            public var interval: Duration = 10.seconds
            public var timeout: Duration = 5.seconds
            public var method: HttpMethod = HttpMethod.Get
            public var headers: MutableMap<String, List<String>> = mutableMapOf()
            public var deregisterCriticalServiceAfter: Duration = 30.seconds
            public var tls: Boolean = false

            public fun build(): HealthCheck {
                return HealthCheck(
                    path = path,
                    interval = interval,
                    timeout = timeout,
                    method = method,
                    headers = headers,
                    deregisterCriticalServiceAfter = deregisterCriticalServiceAfter,
                    tls = tls
                )
            }
        }
    }
}
