/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.servicediscovery.consul

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.servicediscovery.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.servicediscovery.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceDiscoveryPluginTest {
    val mockServiceInstance = object : ServiceInstance {
        override val instanceId = "test-service-1"
        override val serviceId = "test-service"
        override val host = "localhost"
        override val port = 8080
        override val metadata: Map<String, String> = emptyMap()
    }

    val mockDiscoveryClient = object : DiscoveryClient<ServiceInstance> {
        override suspend fun getInstances(serviceName: String): List<ServiceInstance> {
            return listOf(mockServiceInstance)
        }

        override suspend fun getServiceIds(): List<String> {
            TODO("Should not be called")
        }
    }

    @Test
    fun `should resolve service URL to actual HTTP endpoint`() = runBlocking {
        val mockEngine = MockEngine { request ->
            assertEquals("localhost", request.url.host)
            assertEquals(8080, request.url.port)
            respond(
                content = "Mock Response",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ServiceDiscovery) {
                discoveryClient = mockDiscoveryClient
            }
        }

        val response = client.get("service://test-service/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Mock Response", response.bodyAsText())
    }
}

