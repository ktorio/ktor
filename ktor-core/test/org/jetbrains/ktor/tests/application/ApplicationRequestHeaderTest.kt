package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class ApplicationRequestHeaderTest {

    Test fun `an application that handles requests to foo`() = withTestApplication {
        on("making an unauthenticated request to /foo") {
            application.routing {
                get("/foo") {
                    handle {
                        it("should map uri to /foo") {
                            assertEquals("/foo", uri)
                        }
                        it("should map authorization to empty string") {
                            assertEquals("", authorization())
                        }
                        it("should return empty string as queryString") {
                            assertEquals("", queryString())
                        }
                        respond {
                            status(HttpStatusCode.OK)
                            ApplicationRequestStatus.Handled
                        }
                    }
                }
            }

            val status = handleRequest {
                uri = "/foo"
                method = HttpMethod.Get
                headers.put("Authorization", "")
            }.response?.code

            it("should handle request") {
                assertEquals(HttpStatusCode.OK.value, status)
            }
        }
    }

    Test fun `an application that handles requests to foo with parameters`() = withTestApplication {
        on("making a request to /foo?key1=value1&key2=value2") {
            application.routing {
                get("/foo") {
                    handle {
                        it("should map uri to /foo?key1=value1&key2=value2") {
                            assertEquals("/foo?key1=value1&key2=value2", uri)
                        }
                        it("should map two parameters key1=value1 and key2=value2") {
                            val params = queryParameters()
                            assertEquals("value1", params["key1"]?.single())
                            assertEquals("value2", params["key2"]?.single())
                        }
                        it("should map queryString to key1=value1&key2=value2") {
                            assertEquals("key1=value1&key2=value2", queryString())
                        }
                        it("should map document to foo") {
                            assertEquals("foo", document())
                        }
                        it("should map path to /foo") {
                            assertEquals("/foo", path())
                        }
                        it("should map host to host.name.com") {
                            assertEquals("host.name.com", host())
                        }
                        it("should map port to 8888") {
                            assertEquals(8888, port())
                        }
                        respond {
                            status(HttpStatusCode.OK)
                            ApplicationRequestStatus.Handled
                        }
                    }
                }
                get("/default-port") {
                    handle {
                        it("should map port to 80") {
                            assertEquals(80, port())
                        }

                        respond {
                            status(HttpStatusCode.OK)
                            ApplicationRequestStatus.Handled
                        }
                    }
                }
            }

            val status = handleRequest {
                uri = "/foo?key1=value1&key2=value2"
                method = HttpMethod.Get
                headers.put("Host", "host.name.com:8888")
            }.response?.code

            it("should handle request") {
                assertEquals(HttpStatusCode.OK.value, status)
            }

            val status2 = handleRequest {
                uri = "/default-port"
                method = HttpMethod.Get
                headers.put("Host", "host.name.com")
            }.response?.code

            it("should handle second request") {
                assertEquals(HttpStatusCode.OK.value, status2)
            }
        }
    }

    Test fun `an application that handles requests to root with parameters`() = withTestApplication {
        on("making a request to /?key1=value1&key2=value2") {
            application.routing {
                get("/") {
                    handle {
                        it("should map uri to /?key1=value1&key2=value2") {
                            assertEquals("/?key1=value1&key2=value2", uri)
                        }
                        it("should map two parameters key1=value1 and key2=value2") {
                            val params = queryParameters()
                            assertEquals("value1", params["key1"]?.single())
                            assertEquals("value2", params["key2"]?.single())
                        }
                        it("should map queryString to key1=value1&key2=value2") {
                            assertEquals("key1=value1&key2=value2", queryString())
                        }
                        it("should map document to empty") {
                            assertEquals("", document())
                        }
                        it("should map path to empty") {
                            assertEquals("/", path())
                        }
                        respond {
                            status(HttpStatusCode.OK)
                            ApplicationRequestStatus.Handled
                        }
                    }
                }
            }

            val status = handleRequest {
                uri = "/?key1=value1&key2=value2"
                method = HttpMethod.Get
            }.response?.code

            it("should handle request") {
                assertEquals(HttpStatusCode.OK.value, status)
            }
        }
    }

}

