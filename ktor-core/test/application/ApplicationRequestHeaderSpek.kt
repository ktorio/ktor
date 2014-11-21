package ktor.tests.application

import ktor.application.*
import org.jetbrains.spek.api.*
import ktor.tests.*
import ktor.routing.*

class ApplicationRequestHeaderSpek : Spek() {{

    given("an application that handles requests to /foo") {
        val testHost = createTestHost()
        on("making an unauthenticated request to /foo") {
            testHost.application.routing {
                get("/foo") {
                    it("should map uri to /foo") {
                        shouldEqual("/foo", uri)
                    }
                    it("should map authorization to empty string") {
                        shouldEqual("", authorization())
                    }
                    it("should return empty string as queryString") {
                        shouldEqual("", queryString())
                    }
                    response { status(HttpStatusCode.OK) }
                }
            }

            val status = testHost.makeRequest {
                uri = "/foo"
                httpMethod = HttpMethod.Get
                headers.add(Pair("Authorization", ""))
            }.response?.status

            it("should handle request") {
                shouldEqual(HttpStatusCode.OK.value, status)
            }
        }
    }

    given("an application that handles requests to /foo with parameters") {
        val testHost = createTestHost()
        on("making a request to /foo?key1=value1&key2=value2") {
            testHost.application.routing {
                get("/foo") {
                    it("shoud map uri to /foo?key1=value1&key2=value2") {
                        shouldEqual("/foo?key1=value1&key2=value2", uri)
                    }
                    it("shoud map two parameters key1=value1 and key2=value2") {
                        val params = queryParameters()
                        shouldEqual("value1", params["key1"]?.single())
                        shouldEqual("value2", params["key2"]?.single())
                    }
                    it("should map queryString to key1=value1&key2=value2") {
                        shouldEqual("key1=value1&key2=value2", queryString())
                    }
                    it("should map document to foo") {
                        shouldEqual("foo", document())
                    }
                    it("should map path to /foo") {
                        shouldEqual("/foo", path())
                    }
                    it("should map host to host.name.com") {
                        shouldEqual("host.name.com", host())
                    }
                    it("should map port to 8888") {
                        shouldEqual(8888, port())
                    }
                    response { status(HttpStatusCode.OK) }
                }
            }

            val status = testHost.makeRequest {
                uri = "/foo?key1=value1&key2=value2"
                httpMethod = HttpMethod.Get
                headers.add("Host" to "host.name.com:8888")
            }.response?.status

            it("should handle request") {
                shouldEqual(HttpStatusCode.OK.value, status)
            }
        }
    }

    given("an application that handles requests to root with parameters") {
        val testHost = createTestHost()
        on("making a request to /?key1=value1&key2=value2") {
            testHost.application.routing {
                get("/") {
                    it("shoud map uri to /?key1=value1&key2=value2") {
                        shouldEqual("/?key1=value1&key2=value2", uri)
                    }
                    it("shoud map two parameters key1=value1 and key2=value2") {
                        val params = queryParameters()
                        shouldEqual("value1", params["key1"]?.single())
                        shouldEqual("value2", params["key2"]?.single())
                    }
                    it("should map queryString to key1=value1&key2=value2") {
                        shouldEqual("key1=value1&key2=value2", queryString())
                    }
                    it("should map document to empty") {
                        shouldEqual("", document())
                    }
                    it("should map path to empty") {
                        shouldEqual("/", path())
                    }
                    response { status(HttpStatusCode.OK) }
                }
            }

            val status = testHost.makeRequest {
                uri = "/?key1=value1&key2=value2"
                httpMethod = HttpMethod.Get
            }.response?.status

            it("should handle request") {
                shouldEqual(HttpStatusCode.OK.value, status)
            }
        }
    }

}
}
