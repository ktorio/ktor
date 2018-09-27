package io.ktor.tests.graphql

import io.ktor.application.Application
import io.ktor.graphql.config
import io.ktor.graphql.graphQL
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.contentType
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun Application.GraphiQL() {
    routing {
            graphQL(urlString(), schema) {
               config {
                   graphiql = true
               }
            }

    }
}



class TestGraphiQL {

    @Test
    fun `does not renders GraphiQL if no opt-in`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            method = HttpMethod.Get
            uri = urlString(
                    Pair("query", " { test } ")
            )
            addHeader(HttpHeaders.Accept, "text/html")
        }) {
            assertEquals(expected = "application/json; charset=UTF-8", actual = response.contentType().toString())
            assertEquals(
                    expected = HttpStatusCode.OK,
                    actual = response.status()
            )
            assertEquals(expected = "{\"data\":{\"test\":\"Hello World\"}}", actual = response.content)

        }
    }

    @Test
    fun `presents GraphiQL when accepting HTML`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            method = HttpMethod.Get
            uri = urlString(
                    Pair("query", "{test}")
            )
            addHeader(HttpHeaders.Accept, "text/html")
        }) {
            assertEquals(
                    expected = HttpStatusCode.OK,
                    actual = response.status()
            )
            assertEquals(expected = "text/html; charset=UTF-8", actual = response.contentType().toString())

            val content = response.content!!
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertContains(content, "{test}")
            assertContains(content, "graphiql.min.js")
        }
    }

    @Test
    fun `contains a pre-run response within GraphiQL`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            method = HttpMethod.Get
            uri = urlString(
                    Pair("query", "{test}")
            )
            addHeader(HttpHeaders.Accept, "text/html")
        }) {
            assertEquals(
                    expected = HttpStatusCode.OK,
                    actual = response.status()
            )
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "text/html; charset=UTF-8", actual = response.contentType().toString())

            val content = response.content!!
            assertContains(content, "response: JSON.stringify({\"data\":{\"test\":\"Hello World\"}}, null, 2)")

        }
    }

    @Test
    fun `contains a pre-run operation name within GraphiQL`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            method = HttpMethod.Get
            uri = urlString(
                    Pair("query", "query A{a:test} query B{b:test}"),
                    Pair("operationName", "B")
            )
            addHeader(HttpHeaders.Accept, "text/html")
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "text/html; charset=UTF-8", actual = response.contentType().toString())

            val content = response.content!!

            assertContains(content, "response: JSON.stringify({\"data\":{\"b\":\"Hello World\"}}, null, 2)")
            assertContains(content, "operationName: \"B\"")

        }
    }

    @Test
    fun `escapes HTML in queries within GraphiQL`() = withTestApplication(Application::GraphiQL) {

        with(handleRequest {
            method = HttpMethod.Get
            uri = urlString(
                    Pair("query", "</script><script>alert(1)</script>"),
                    Pair("operationName", "B")
            )
            addHeader(HttpHeaders.Accept, "text/html")
        }) {
            assertEquals(expected = HttpStatusCode.BadRequest, actual = response.status())
            assertEquals(expected = "text/html; charset=UTF-8", actual = response.contentType().toString())
            val content = response.content!!

            assertDoesntContains(content, "</script><script>alert(1)</script>")
        }
    }

    @Test
    fun `escapes HTML in variables within GraphiQL`() = withTestApplication(Application::GraphiQL) {

        with(handleRequest {
            method = HttpMethod.Get
            uri = urlString(
                    Pair("query", "query helloWho(${"$"}who: String){ test(who: ${"$"}who) }"),
                    Pair("variables", """
                    {
                        "who": "</script><script>alert(1)</script>"
                    }
                    """)
            )
            addHeader(HttpHeaders.Accept, "text/html")
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "text/html; charset=UTF-8", actual = response.contentType().toString())
            val content = response.content!!

            assertDoesntContains(content, "</script><script>alert(1)</script>")
        }
    }

    @Test
    fun `GraphiQL renders provided variables`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            uri = urlString(
                    Pair("query", "query helloWho(${"$"}who: String){ test(who: ${"$"}who) }"),
                    Pair("variables", """
                    {
                        "who": "Dolly"
                    }
                    """)
            )
            method = HttpMethod.Get
            addHeader(HttpHeaders.Accept, "text/html")

        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "text/html; charset=UTF-8", actual = response.contentType().toString())
            assertContains(response.content!!, "variables: JSON.stringify({\"who\":\"Dolly\"}, null, 2)")
        }
    }

    @Test
    fun `GraphiQL accepts an empty query`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            uri = urlString()
            method = HttpMethod.Get
            addHeader(HttpHeaders.Accept, "text/html")
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "text/html; charset=UTF-8", actual = response.contentType().toString())
            assertContains(response.content!!, "response: undefined")
        }
    }

    @Test
    fun `GraphiQL accepts a mutation query - does not execute it`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            uri = urlString(
                    Pair("query", "mutation TestMutation { writeTest { test } }")
            )
            method = HttpMethod.Get
            addHeader(HttpHeaders.Accept, "text/html")
        }) {


            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "text/html; charset=UTF-8", actual = response.contentType().toString())

            val response = response.content!!

            assertContains(response, "query: \"mutation TestMutation { writeTest { test } }\"")
            assertContains(response, "response: undefined")
        }
    }

    @Test
    fun `returns HTML if preferred`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            uri = urlString(Pair("query", "{test}"))
            addHeader(HttpHeaders.Accept, "text/html,application/json")
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "text/html; charset=UTF-8", actual = response.contentType().toString())
            assertContains(response.content!!, "graphiql.min.js")
        }
    }

    @Test
    fun `returns JSON if preferred`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            uri = urlString(Pair("query", "{test}"))
            addHeader(HttpHeaders.Accept, "application/json,text/html")
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "application/json; charset=UTF-8", actual = response.contentType().toString())
            assertEquals(expected = "{\"data\":{\"test\":\"Hello World\"}}", actual = response.content)
        }
    }

    @Test
    fun `prefers JSON if unknown accept`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            uri = urlString(Pair("query", "{test}"))
            addHeader(HttpHeaders.Accept, "unknown")
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "application/json; charset=UTF-8", actual = response.contentType().toString())
            assertEquals(expected = "{\"data\":{\"test\":\"Hello World\"}}", actual = response.content)
        }
    }

    @Test
    fun `prefers JSON if no header is specified`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            uri = urlString(Pair("query", "{test}"))
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "application/json; charset=UTF-8", actual = response.contentType().toString())
            assertEquals(expected = "{\"data\":{\"test\":\"Hello World\"}}", actual = response.content)
        }
    }

    @Test
    fun `prefers JSON if explicitly requested raw response`() = withTestApplication(Application::GraphiQL) {
        with(handleRequest {
            uri = urlString(Pair("query", "{test}"), Pair("raw", ""))
            addHeader(HttpHeaders.Accept, "text/html")
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "application/json; charset=UTF-8", actual = response.contentType().toString())
            assertEquals(expected = "{\"data\":{\"test\":\"Hello World\"}}", actual = response.content)
        }
    }
}


fun assertContains(actual: String, containing: String) {
    val containsMessage =
         """
        Expected:
        $actual

        To contain:
        $containing
        """



    assertTrue(actual.contains(containing), containsMessage)
}

fun assertDoesntContains(actual: String, containing: String) {
    val containsMessage =
            """
        Expected:
        $actual

        Not to contain:
        $containing
        """



    assertFalse(actual.contains(containing), containsMessage)
}