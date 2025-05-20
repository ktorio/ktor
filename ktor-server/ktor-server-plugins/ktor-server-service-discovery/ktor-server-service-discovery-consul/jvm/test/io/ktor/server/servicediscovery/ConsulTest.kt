package io.ktor.server.servicediscovery

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.servicediscovery.consul.*
import io.ktor.server.testing.*
import io.ktor.servicediscovery.consul.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsulServiceDiscoveryTest {

    @Test
    fun testServiceRegistrationAndDiscovery() = testApplication {
        application {
            install(ServiceDiscovery) {
                addOnStart = true
                removeOnStop = true

                consul {
                    connection {
                        host = "localhost"
                        port = 8500
                        aclToken = "default"
                    }
                }
            }

            routing {
                get("/register") {
                    val registry = getServiceRegistry<ConsulServiceRegistry>()
                    val instance = ConsulServiceInstance(
                        instanceId = "test-instance",
                        serviceId = "test-service",
                        host = "localhost",
                        port = 8080
                    )
                    val success = registry.add(instance)
                    call.respondText(success.toString())
                }

                get("/discover") {
                    val client = getDiscoveryClient<ConsulDiscoveryClient>()
                    val instances = client.getInstances("test-service")
                    call.respondText(instances.size.toString())
                }

                get("/deregister") {
                    val registry = getServiceRegistry<ConsulServiceRegistry>()
                    val success = registry.remove("test-instance")
                    call.respondText("$success")
                }
            }
        }

        val registerResponse = client.get("/register")
        assertEquals(HttpStatusCode.OK, registerResponse.status)
        assertEquals("true", registerResponse.bodyAsText())

        val discoverResponse = client.get("/discover")
        assertEquals(HttpStatusCode.OK, discoverResponse.status)
        assertEquals("1", discoverResponse.bodyAsText())

        val deregisterResponse = client.get("/deregister")
        assertEquals(HttpStatusCode.OK, deregisterResponse.status)
        assertEquals("true", deregisterResponse.bodyAsText())
    }
}
