package io.ktor.tests.graphql

import io.ktor.application.Application
import io.ktor.graphql.GraphQLRequest
import io.ktor.graphql.config
import io.ktor.graphql.graphQL
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals


class GraphQLRouteGetTest {

    @Test
    fun `allows GET with query param`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(Pair("query", "{ test }"))
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "{\"data\":{\"test\":\"Hello World\"}}", actual = response.content)
        }
    }

    @Test
    fun `allows GET with variable values`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(
                    Pair("query", "query helloWho(\$who: String){ test(who: \$who) }"),
                    Pair("variables", "{ \"who\": \"Dolly\" }")
            )
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "{\"data\":{\"test\":\"Hello Dolly\"}}", actual = response.content)
        }
    }

    @Test
    fun `allows GET with operation name`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(
                    Pair("query", """
                              query helloYou { test(who: "You"), ...shared }
                              query helloWorld { test(who: "World"), ...shared }
                              query helloDolly { test(who: "Dolly"), ...shared }
                              fragment shared on Query {
                                shared: test(who: "Everyone")
                              }
                            """
                    ),
                    Pair("operationName", "helloWorld")
            )
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "data": {
                            "test": "Hello World",
                            "shared": "Hello Everyone"
                        }
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `allows GET if the content-type is application json`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(Pair("query", "{ test }"))
            method = HttpMethod.Get
            addHeader(HttpHeaders.ContentType, "application/json")
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "{\"data\":{\"test\":\"Hello World\"}}", actual = response.content)
        }
    }

    @Test
    fun `reports validation errors`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(Pair("query", "{ test, unknownOne, unknownTwo }"))
            method = HttpMethod.Get
        }) {

            assertEquals(expected = HttpStatusCode.BadRequest, actual = response.status())
            assertEquals(
                    expected = removeWhitespace("""
                    {
                      "errors": [
                        {
                          "message": "Validation error of type FieldUndefined: Field 'unknownOne' in type 'Query' is undefined @ 'unknownOne'",
                          "locations": [
                            {
                              "line": 1,
                              "column": 9
                            }
                          ]
                        },
                        {
                          "message": "Validation error of type FieldUndefined: Field 'unknownTwo' in type 'Query' is undefined @ 'unknownTwo'",
                          "locations": [
                            {
                              "line": 1,
                              "column": 21
                            }
                          ]
                        }
                      ]
                    }
                    """),
                    actual = response.content
            )

        }
    }

    @Test
    fun `errors when missing the operation name`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(Pair("query", """
                query TestQuery { test }
                mutation TestMutation { writeTest { test } }
              """))
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.InternalServerError, actual = response.status())
            assertEquals(
                    expected = removeWhitespace("""
                        {
                          "errors": [
                            {
                              "message": "Must provide operation name if query contains multiple operations."
                            }
                          ]
                        }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `errors when sending a mutation via a GET`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(Pair("query", """
                mutation TestMutation { writeTest { test } }
            """))
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.MethodNotAllowed, actual = response.status())
            assertEquals(
                    expected = removeWhitespace("""
                        {
                            "errors": [
                                {
                                    "message": "Can only perform a mutation operation from a POST request."
                                }
                            ]
                        }
                    """),
                    actual = response.content
            )

        }
    }

    @Test
    fun `errors when selecting a mutation via a GET`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(
                    Pair("operationName", "TestMutation"),
                    Pair("query", """
                        query TestQuery { test }
                        mutation TestMutation { writeTest { test } }
                    """)
            )
            method = HttpMethod.Get
        }) {
            assertEquals(
                    expected = HttpStatusCode.MethodNotAllowed,
                    actual = response.status()
            )

            assertEquals(
                    expected = removeWhitespace("""
                        {
                            "errors": [{
                                "message": "Can only perform a mutation operation from a POST request."
                            }]
                        }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `allows a mutation to exist within a GET`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(
                    Pair("operationName", "TestQuery"),
                    Pair("query", """
                        mutation TestMutation { writeTest { test } }
                        query TestQuery { test }
                    """)
            )
            method = HttpMethod.Get
        }) {
            assertEquals(
                    expected = HttpStatusCode.OK,
                    actual = response.status()
            )

            assertEquals(
                    expected = removeWhitespace("""
                        {
                          "data": {
                            "test": "Hello World"
                          }
                        }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `allows passing in a context`() = withTestApplication {
        application.routing {
                graphQL(urlString(), schema) {
                    config {
                        context = "testValue"
                    }
                }
        }
        with(handleRequest {
            uri = urlString(
                    Pair("operationName", "TestQuery"),
                    Pair("query", "query TestQuery { context }")
            )
            method = HttpMethod.Get
        }) {
            assertEquals(
                    expected = HttpStatusCode.OK,
                    actual = response.status()
            )
            assertEquals(
                    expected = removeWhitespace("""
                        {
                          "data": {
                            "context": "testValue"
                          }
                        }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `allows passing in a root value`() = withTestApplication {
        application.routing {
                graphQL(urlString(), schema) {
                    config {
                        rootValue = "testValue"
                    }
                }
        }
        with(handleRequest {
            uri = urlString(
                    Pair("operationName", "TestQuery"),
                    Pair("query", "query TestQuery { rootValue }")
            )
            method = HttpMethod.Get
        }) {
            assertEquals(
                    expected = HttpStatusCode.OK,
                    actual = response.status()
            )
            assertEquals(
                    expected = removeWhitespace("""
                        {
                          "data": {
                            "rootValue": "testValue"
                          }
                        }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `it provides a setup function with arguments`() = withTestApplication {
        var requestInSetupFn: GraphQLRequest? = null

        application.routing {
                graphQL(urlString(), schema) { request ->
                    requestInSetupFn = request
                    config {
                        context = "testValue"
                    }
                }

        }

        with(handleRequest {
            uri = urlString(Pair("query", "{ test }"))
            method = HttpMethod.Get
        }) {
            assertEquals(
                    expected = GraphQLRequest(query = "{ test }"),
                    actual = requestInSetupFn
            )
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(expected = "{\"data\":{\"test\":\"Hello World\"}}", actual = response.content)
        }
    }

    @Test
    fun `it catches errors thrown from the setup function`() = withTestApplication {
        application.routing {
                graphQL(urlString(), schema) {
                    throw Exception("Something went wrong")
                }

        }

        with(handleRequest {
            uri = urlString(Pair("query", "{ test }"))
            method = HttpMethod.Get
        }) {
            assertEquals(expected = HttpStatusCode.InternalServerError, actual = response.status())
            assertEquals(expected = removeWhitespace("""
                {
                    "errors": [{ "message": "Something went wrong" }]
                }
                """), actual = response.content)
        }
    }
}