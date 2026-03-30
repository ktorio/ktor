/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.healthcheck

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.*

/**
 * A plugin that provides health check endpoints for Kubernetes readiness and liveness probes.
 *
 * Intercepts GET requests matching configured paths and responds with a JSON health status.
 * All checks within an endpoint run concurrently. The overall status is UP only when every
 * individual check reports healthy.
 *
 * Response format:
 * ```json
 * {
 *   "status": "UP",
 *   "checks": [
 *     { "name": "database", "status": "UP" },
 *     { "name": "redis", "status": "DOWN", "error": "Connection refused" }
 *   ]
 * }
 * ```
 *
 * - HTTP 200 when all checks pass (status: UP)
 * - HTTP 503 when any check fails (status: DOWN)
 *
 * Example:
 * ```kotlin
 * install(HealthCheck) {
 *     readiness("/ready") {
 *         check("database") { dataSource.connection.isValid(1) }
 *         check("redis") { redisClient.ping(); true }
 *     }
 *     liveness("/health") {
 *         check("memory") { Runtime.getRuntime().freeMemory() > threshold }
 *     }
 * }
 * ```
 */
public val HealthCheck: ApplicationPlugin<HealthCheckConfig> =
    createApplicationPlugin("HealthCheck", ::HealthCheckConfig) {
        val endpoints = pluginConfig.endpoints.toList()

        onCall { call ->
            if (call.response.isCommitted) return@onCall
            if (call.request.httpMethod != HttpMethod.Get) return@onCall

            val path = call.request.path()
            val endpoint = endpoints.find { it.path == path } ?: return@onCall

            val results = coroutineScope {
                endpoint.checks.map { namedCheck ->
                    async { evaluateCheck(namedCheck) }
                }.awaitAll()
            }

            val overallUp = results.all { it.status == CheckStatus.UP }
            val httpStatus = if (overallUp) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable

            call.respondText(
                buildHealthResponse(overallUp, results),
                ContentType.Application.Json,
                httpStatus
            )
        }
    }

private suspend fun evaluateCheck(namedCheck: NamedCheck): CheckResult =
    try {
        val healthy = namedCheck.check()
        CheckResult(namedCheck.name, if (healthy) CheckStatus.UP else CheckStatus.DOWN)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        CheckResult(namedCheck.name, CheckStatus.DOWN, "Health Check Failed")
    }

internal enum class CheckStatus { UP, DOWN }

internal class CheckResult(val name: String, val status: CheckStatus, val error: String? = null)

private fun buildHealthResponse(overallUp: Boolean, results: List<CheckResult>): String = buildString {
    append("{\"status\":\"")
    append(if (overallUp) "UP" else "DOWN")
    append("\",\"checks\":[")
    results.forEachIndexed { index, result ->
        if (index > 0) append(',')
        append("{\"name\":\"")
        append(result.name.escapeJson())
        append("\",\"status\":\"")
        append(result.status.name)
        append('"')
        if (result.error != null) {
            append(",\"error\":\"")
            append(result.error.escapeJson())
            append('"')
        }
        append('}')
    }
    append("]}")
}

private fun String.escapeJson(): String = buildString(length) {
    for (ch in this@escapeJson) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) {
                append("\\u")
                append(ch.code.toString(16).padStart(4, '0'))
            } else {
                append(ch)
            }
        }
    }
}
