package io.ktor.tests.graphql

import graphql.ExceptionWhileDataFetching
import graphql.GraphqlErrorHelper
import io.ktor.application.Application
import io.ktor.graphql.config
import io.ktor.graphql.graphQL
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

class ErrorHandlingTest {


    @Test
    fun `it handles field errors caught by graphql`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(Pair("query", "{ thrower }"))
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(
                expected = removeWhitespace("""
                {
                    "data": {
                        "thrower": null
                    },
                    "errors": [
                        {
                            "message": "Exception while fetching data (/thrower) : Throws!",
                            "locations":[{"line":1,"column":3}],
                            "path":["thrower"]
                        }
                    ]
                }
                """),
                actual = response.content
            )
        }
    }

    @Test
    fun `handles query errors from non-null top field errors`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(Pair("query", "{ nonNullThrower }"))
            method = HttpMethod.Get
        }) {

            assertEquals(expected = HttpStatusCode.InternalServerError, actual = response.status())

            assertEquals(
                    expected = removeWhitespace("""
                        {
                            "data": null,
                            "errors": [
                                {
                                    "message":"Exception while fetching data (/nonNullThrower) : Throws!",
                                    "locations":[{"line":1,"column":3}],
                                    "path":["nonNullThrower"]
                                }
                            ]
                        }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `allows for custom error formatting to sanitize`() = withTestApplication {
        application.routing {

                graphQL(urlString(), schema) {
                    config {
                        context = "hello"
                        formatError = {
                            val message = if (this is ExceptionWhileDataFetching) {
                                exception.message
                            } else {
                                message
                            }
                            mapOf(
                                Pair("message", "Custom error format: $message")
                            )
                        }
                    }
                }
        }

        with(handleRequest {
            uri = urlString(Pair("query", "{ thrower }"))
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = removeWhitespace("""
                {
                    "data": {
                        "thrower": null
                    },
                    "errors": [
                        {
                            "message": "Custom error format: Throws!"
                        }
                    ]
                }
                """), actual = response.content)
        }
    }

    @Test
    fun `allows for custom error formatting to elaborate`() = withTestApplication {
        application.routing {
                graphQL(urlString(), schema) {
                    config {
                        formatError = {
                            mapOf(
                                    Pair("message", message),
                                    Pair("locations", GraphqlErrorHelper.locations(locations)),
                                    Pair("stack", "stack trace")
                            )
                        }
                    }
                }

        }

        with(handleRequest {
            uri = urlString(Pair("query", "{ thrower }"))
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = removeWhitespace("""
                {
                    "data": {
                        "thrower": null
                    },
                    "errors": [
                        {
                            "message":"Exception while fetching data (/thrower) : Throws!",
                            "locations":[{"line":1,"column":3}],
                            "stack":"stack trace"
                        }
                    ]
                }
                """), actual = response.content)
        }
    }

    @Test
    fun `handles syntax errors caught by GraphL`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(Pair("query", "syntaxerror"))
            method = HttpMethod.Get
        }) {
            assertEquals(
                expected = HttpStatusCode.BadRequest,
                actual = response.status()
            )

            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "errors": [
                            {
                                "message": "Invalid Syntax",
                                "locations": [{ "line": 1, "column": 0 }]
                            }
                        ]
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `handles errors caused by a lack of query`() = withTestApplication(Application::testGraphQLRoute) {

        with(handleRequest{
            uri = urlString()
            method = HttpMethod.Get
        }) {
            assertEquals(
                    expected = HttpStatusCode.BadRequest,
                    actual = response.status()
            )

            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "errors": [
                            {
                                "message": "Must provide query string."
                            }
                        ]
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `handles invalid JSON bodies`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString()
            method = HttpMethod.Post
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("[]")
        }) {
            assertEquals(HttpStatusCode.BadRequest, response.status())
            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "errors": [
                            {
                                "message": "POST body sent invalid JSON."
                            }
                         ]
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `handles incomplete JSON bodies`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString()
            method = HttpMethod.Post
            addHeader(HttpHeaders.ContentType, "application/json")
            setBody("""{"query":""")
        }) {
            assertEquals(HttpStatusCode.BadRequest, response.status())
            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "errors": [
                            {
                                "message": "POST body sent invalid JSON."
                            }
                         ]
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `handles plain post text`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString()
            method = HttpMethod.Post
            addHeader(HttpHeaders.ContentType, "text/plain")
            setBody("query helloWho(${"$"}who: String){ test(who: ${"$"}who) }")


        }) {
            assertEquals(
                    expected = HttpStatusCode.BadRequest,
                    actual = response.status()
            )

            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "errors": [
                            {
                                "message": "Must provide query string."
                            }
                        ]
                    }
                    """),
                    actual = response.content
            )
        }
    }


    @Test
    fun `handles poorly formed variables`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(
                    Pair("variables", "who:you"),
                    Pair("query", "query helloWho(${"$"}who: String){ test(who: ${"$"}who) }")
            )
            method = HttpMethod.Post

        }) {
            assertEquals(
                    expected = HttpStatusCode.BadRequest,
                    actual = response.status()
            )

            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "errors": [
                            {
                                "message": "Variables are invalid JSON."
                            }
                         ]
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `allows for custom error formatting of poorly formed requests`() = withTestApplication {
        application.routing {
                graphQL(urlString(), schema) {
                    config {
                        formatError = {
                            mapOf(Pair("message", "Custom error format: ${this.message}"))
                        }
                    }
                }

        }
        with(handleRequest {
            uri = urlString(
                    Pair("variables", "who:you"),
                    Pair("query", "query helloWho(${"$"}who: String){ test(who: ${"$"}who) }")
            )
            method = HttpMethod.Post

        }) {
            assertEquals(
                    expected = HttpStatusCode.BadRequest,
                    actual = response.status()
            )

            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "errors": [
                            {
                                "message": "Custom error format: Variables are invalid JSON."
                            }
                         ]
                    }
                    """),
                    actual = response.content
            )
        }
    }


    @Test
    fun `handles invalid variables`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString()
            method = HttpMethod.Post
            setBody("""
                {
                    "query": "query helloWho(${"$"}value: Boolean){ testBoolean(value: ${"$"}value) }",
                    "variables": {
                        "value": ["Dolly", "Jonty"]
                    }
                }
            """)
            addHeader(HttpHeaders.ContentType, "application/json")

        }) {

            assertEquals(expected = HttpStatusCode.InternalServerError, actual = response.status())
            assertEquals(
                    expected = removeWhitespace("""
                {
                    "data": null,
                    "errors": [{
                        "message": "Variable 'value' has an invalid value. Expected type 'Boolean' but was 'ArrayList'.",
                        "locations": [{
                            "line": 1,
                            "column": 16
                        }]
                    }]
                }
                """),
                    actual = response.content
            )
        }
    }

}