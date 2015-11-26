package org.jetbrains.ktor.tests

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.jackson.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.junit.*
import kotlin.test.*

class JacksonSupportTest {
    @Test
    fun testGetDirectUntyped() {
        withTestApplication {
            application.setupJackson()

            application.routing {
                get("/m") {
                    assertEquals(mapOf("title" to "test"), request.content.get<JsonContent>().payload)
                    ApplicationRequestStatus.Handled
                }
            }

            val result = handleRequest(HttpMethod.Get, "/m") {
                body = "{\"title\":\"test\"}"
            }

            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
        }
    }

    @Test
    fun testGetDirectTyped() {
        withTestApplication {
            application.setupJackson()

            application.routing {
                get("/m") {
                    assertEquals(Model("test"), request.content.get<RequestJsonContent>().get<Model>())
                    ApplicationRequestStatus.Handled
                }
            }

            val result = handleRequest(HttpMethod.Get, "/m") {
                body = "{\"title\":\"test\"}"
            }

            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
        }
    }

    @Test
    fun testGetWithJson() {
        withTestApplication {
            application.setupJackson()

            application.routing {
                get("/m") {
                    withJsonContent<Model> {
                        assertEquals(Model("test"), it)
                        ApplicationRequestStatus.Handled
                    }
                }
            }

            val result = handleRequest(HttpMethod.Get, "/m") {
                body = "{\"title\":\"test\"}"
            }

            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
        }
    }

    @Test
    fun testGetNullUntyped() {
        withTestApplication {
            application.setupJackson()

            application.routing {
                get("/m") {
                    assertNull(request.content.get<JsonContent>().payload)
                    ApplicationRequestStatus.Handled
                }
            }

            val result = handleRequest(HttpMethod.Get, "/m") {
                body = "null"
            }

            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
        }
    }

    @Test
    fun testGetNullTyped() {
        withTestApplication {
            application.setupJackson()

            application.routing {
                get("/m") {
                    assertNull(request.content.get<RequestJsonContent>().get<Model>())
                    ApplicationRequestStatus.Handled
                }
            }

            val result = handleRequest(HttpMethod.Get, "/m") {
                body = "null"
            }

            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
        }
    }

    @Test
    fun testGetWithNullTyped() {
        withTestApplication {
            application.setupJackson()

            application.routing {
                get("/m") {
                    withJsonContent<Model> {
                        assertNull(it)
                        ApplicationRequestStatus.Handled
                    }
                }
            }

            val result = handleRequest(HttpMethod.Get, "/m") {
                body = "null"
            }

            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
        }
    }

    @Test
    fun testSendModel() {
        withTestApplication {
            application.setupJackson()

            application.routing {
                get("/m") {
                    response.send(JsonContent(Model("response")))
                }
            }

            val result = handleRequest(HttpMethod.Get, "/m") {
            }

            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
            assertEquals(ContentType.Application.Json, ContentType.parse(result.response.headers[HttpHeaders.ContentType]!!))
            assertEquals("{\"title\":\"response\"}", result.response.content)
        }
    }

    @Test
    fun testSendModelNull() {
        withTestApplication {
            application.setupJackson()

            application.routing {
                get("/m") {
                    response.send(JsonContent(null))
                }
            }

            val result = handleRequest(HttpMethod.Get, "/m") {
            }

            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
            assertEquals(ContentType.Application.Json, ContentType.parse(result.response.headers[HttpHeaders.ContentType]!!))
            assertEquals("null", result.response.content)
        }
    }

    @Test
    fun testRoutingEntry() {
        withTestApplication {
            application.routing {
                route("/api") {
                    setupJackson()

                    get("echo") {
                        response.send(JsonContent(request.content.get<JsonContent>().payload))
                    }
                }

                get("/") {
                    assertFails {
                        val value = request.content.get<JsonContent>()
                        println(value)
                    }
                    ApplicationRequestStatus.Handled
                }
            }

            val content = "{\"f\":1}"
            val result = handleRequest(HttpMethod.Get, "/") {
                body = content
            }
            assertEquals(ApplicationRequestStatus.Handled, result.requestResult)

            val result2 = handleRequest(HttpMethod.Get, "/api/echo") {
                body = content
            }

            assertEquals(ApplicationRequestStatus.Handled, result2.requestResult)
            assertEquals(ContentType.Application.Json, ContentType.parse(result2.response.headers[HttpHeaders.ContentType]!!))
            assertEquals(content, result2.response.content)
        }
    }
}

data class Model(val title: String)