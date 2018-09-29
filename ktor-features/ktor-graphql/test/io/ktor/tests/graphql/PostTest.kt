package io.ktor.tests.graphql

import io.ktor.application.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

class GraphQLRoutePostTest {

    @Test
    fun `allows POST with JSON encoding`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString()
            setBody("""
                {
                	"query": "\n  query IntrospectionQuery {\n    __schema {\n      queryType { name }\n      mutationType { name }\n      types {\n        ...FullType\n      }\n      directives {\n        name\n        description\n        locations\n        args {\n          ...InputValue\n        }\n      }\n    }\n  }\n\n  fragment FullType on __Type {\n    kind\n    name\n    description\n    fields(includeDeprecated: true) {\n      name\n      description\n      args {\n        ...InputValue\n      }\n      type {\n        ...TypeRef\n      }\n      isDeprecated\n      deprecationReason\n    }\n    inputFields {\n      ...InputValue\n    }\n    interfaces {\n      ...TypeRef\n    }\n    enumValues(includeDeprecated: true) {\n      name\n      description\n      isDeprecated\n      deprecationReason\n    }\n    possibleTypes {\n      ...TypeRef\n    }\n  }\n\n  fragment InputValue on __InputValue {\n    name\n    description\n    type { ...TypeRef }\n    defaultValue\n  }\n\n  fragment TypeRef on __Type {\n    kind\n    name\n    ofType {\n      kind\n      name\n      ofType {\n        kind\n        name\n        ofType {\n          kind\n          name\n          ofType {\n            kind\n            name\n            ofType {\n              kind\n              name\n              ofType {\n                kind\n                name\n                ofType {\n                  kind\n                  name\n                }\n              }\n            }\n          }\n        }\n      }\n    }\n  }\n"
                }
            """)
            addHeader(HttpHeaders.ContentType, "application/json")
            method = HttpMethod.Post
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())

        }
    }

    @Test
    fun `allows sending a mutation via POST`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString()
            setBody("""
            {
                "query": "mutation TestMutation { writeTest { test } }"
            }
            """)
            method = HttpMethod.Post
            addHeader(HttpHeaders.ContentType, "application/json")
        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "data": {
                            "writeTest":{
                                "test":"Hello World"
                            }
                        }
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `supports POST JSON query with JSON variables`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString()
            setBody("""
            {
                "query": "query helloWho(${"$"}who: String){ test(who: ${"$"}who) }",
                "variables": {"who": "Dolly"}
            }
            """)
            method = HttpMethod.Post
            addHeader(HttpHeaders.ContentType, "application/json")

        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "data": {
                            "test":"Hello Dolly"
                        }
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `supports POST JSON with GET variable values`() = withTestApplication(Application::testGraphQLRoute){
        with(handleRequest {
            uri= urlString(Pair("variables", """
                    {
                        "who": "Dolly"
                    }
                    """
            ))
            setBody("""
            {
                "query": "query helloWho(${"$"}who: String){ test(who: ${"$"}who) }"
            }
            """)
            addHeader(HttpHeaders.ContentType, "application/json")
            method = HttpMethod.Post

        }) {
            assertEquals(expected = HttpStatusCode.OK, actual = response.status())
            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "data": {
                            "test":"Hello Dolly"
                        }
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `allows POST with operation name`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString()
            setBody("""
            {
                "query": "\n query helloYou { test(who: \"You\") }\n query helloWorld { test(who: \"World\") }\n",
                "operationName": "helloWorld"
}
            """)
            addHeader(HttpHeaders.ContentType, "application/json")
            method = HttpMethod.Post
        }) {
            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "data": {
                            "test":"Hello World"
                        }
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `allows POST with GET operation name`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(Pair("operationName", "helloWorld"))
            setBody("""
            {
                "query": "\n query helloYou { test(who: \"You\") }\n query helloWorld { test(who: \"World\") }\n"
            }
            """)
            addHeader(HttpHeaders.ContentType, "application/json")
            method = HttpMethod.Post
        }) {
            assertEquals(
                    expected = removeWhitespace("""
                    {
                        "data": {
                            "test":"Hello World"
                        }
                    }
                    """),
                    actual = response.content
            )
        }
    }

    @Test
    fun `supports POST raw text query with GET variable values`() = withTestApplication(Application::testGraphQLRoute) {
        with(handleRequest {
            uri = urlString(
            Pair("variables", """
                    {
                        "who": "Dolly"
                    }
                    """
            ),
            Pair("operationName", "helloWho"))
            setBody("query helloWho(${"$"}who: String){ test(who: ${"$"}who) }")
            method = HttpMethod.Post
            addHeader(HttpHeaders.ContentType, "application/graphql")
        }) {
            assertEquals(
                expected = removeWhitespace("""
                {
                    "data": {
                        "test":"Hello Dolly"
                    }
                }
                """),
                actual = response.content
            )
        }
    }
}