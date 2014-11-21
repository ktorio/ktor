package ktor.tests.application

import ktor.application.*
import org.jetbrains.spek.api.*
import ktor.tests.*
import ktor.routing.*

class ApplicationRequestHeaderSpek : Spek() {{

    given("an application that handles requests to /foo") {
        val testHost = createTestHost()


        var parsedUri = ""
        var parsedAuthorization: String?
        var parsedQueryString = ""

        testHost.application.routing {
            get("/foo") {
                parsedUri = uri
                parsedAuthorization = authorization()
                parsedQueryString = queryString()

            }
        }
        on("making an unauthenticated request to /foo") {
            val response = testHost.makeRequest {
                uri = "/foo"
                httpMethod = HttpMethod.Get
                headers.add(Pair("Authorization", ""))
            }
            it("should map uri to /foo") {
                shouldEqual("/foo", parsedUri)
            }
            it("should map authorization to empty string") {
                shouldEqual("", parsedAuthorization)
            }
            it("should return empty string as queryString") {
                shouldEqual("", parsedQueryString)
            }
        }

    }

    given("an application that handles requests to /foo with parameters") {
        val testHost = createTestHost()

        var parsedUri = ""
        var parsedQueryString = ""
        var parsedDocument = ""
        var parsedPath = ""

        testHost.application.routing {
            get("/foo") {
                parsedUri = uri
                parsedQueryString = queryString()
                parsedDocument = document()
                parsedPath = path()
            }
        }
        on("making a request to /foo?key1=value1&key2=value2") {
            val response = testHost.makeRequest {
                uri = "/foo?key1=value1&key2=value2"
                httpMethod = HttpMethod.Get
            }
            it("shoud map uri to /foo?key1=value1&key2=value2") {
                shouldEqual("/foo?key1=value1&key2=value2", parsedUri)
            }
            it("should map queryString to key1=value1&key2=value2") {
                shouldEqual("key1=value1&key2=value2", parsedQueryString)
            }
            it("should map document to foo") {
                shouldEqual("foo", parsedDocument)
            }
            it("should map path to /foo") {
                shouldEqual("/foo", parsedPath)
            }
        }
    }

}
}
