/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.healthcheck

import io.ktor.server.application.*
import io.ktor.utils.io.*

/**
 * Configuration for the [HealthCheck] plugin.
 *
 * Use [readiness] and [liveness] to define health check endpoints with named checks.
 *
 * Example:
 * ```kotlin
 * install(HealthCheck) {
 *     readiness("/ready") {
 *         check("database") { dataSource.connection.isValid(1) }
 *     }
 *     liveness("/health") {
 *         check("heartbeat") { true }
 *     }
 * }
 * ```
 */
@KtorDsl
public class HealthCheckConfig {
    internal val endpoints = mutableListOf<HealthCheckEndpoint>()

    private fun addEndpoint(path: String, block: HealthCheckBuilder.() -> Unit) {
        val normalizedPath = path.ensureLeadingSlash()
        require(endpoints.none { it.path == normalizedPath }) {
            "Health check endpoint path '$normalizedPath' is already configured"
        }
        endpoints += HealthCheckEndpoint(normalizedPath, HealthCheckBuilder().apply(block).checks.toList())
    }

    /**
     * Configures a readiness endpoint at the given [path].
     *
     * Readiness checks determine whether the application is ready to serve requests.
     * Failed readiness checks prevent Kubernetes from routing traffic to the pod.
     *
     * @param path the URL path for the readiness endpoint (e.g., "/ready")
     * @param block builder for adding individual health checks
     */
    public fun readiness(path: String, block: HealthCheckBuilder.() -> Unit) {
        addEndpoint(path, block)
    }

    /**
     * Configures a liveness endpoint at the given [path].
     *
     * Liveness checks determine whether the application is still running.
     * Failed liveness checks cause Kubernetes to restart the container.
     *
     * @param path the URL path for the liveness endpoint (e.g., "/health")
     * @param block builder for adding individual health checks
     */
    public fun liveness(path: String, block: HealthCheckBuilder.() -> Unit) {
        addEndpoint(path, block)
    }
}

/**
 * Builder for configuring individual health checks within a readiness or liveness endpoint.
 */
@KtorDsl
public class HealthCheckBuilder {
    internal val checks = mutableListOf<NamedCheck>()

    /**
     * Adds a named health check.
     *
     * The [block] should return `true` if the component is healthy, `false` if unhealthy.
     * If [block] throws an exception, the check is treated as unhealthy and the exception
     * message is included in the JSON response.
     *
     * @param name a human-readable identifier for this check (e.g., "database", "redis")
     * @param block a suspend function returning `true` for healthy, `false` for unhealthy
     */
    public fun check(name: String, block: suspend () -> Boolean) {
        checks += NamedCheck(name, block)
    }
}

internal class NamedCheck(val name: String, val check: suspend () -> Boolean)

internal class HealthCheckEndpoint(val path: String, val checks: List<NamedCheck>)

private fun String.ensureLeadingSlash(): String = if (startsWith("/")) this else "/$this"
