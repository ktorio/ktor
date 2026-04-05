/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.plugins.graphql

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.graphql.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import kotlin.test.*

class GraphQLTest {

    private val testSchema = """
        type Query {
            hello: String
            greet(name: String!): String
            sum(a: Int!, b: Int!): Int
        }

        type Mutation {
            setMessage(text: String!): String
        }
    """.trimIndent()

    @Test
    fun `simple query returns expected data`() = testApplication {
        configureServer()

        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "{ hello }"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<GraphQLResponse>(response.bodyAsText())
        assertEquals(JsonPrimitive("Hello, World!"), body.data?.jsonObject?.get("hello"))
        assertNull(body.errors)
    }

    @Test
    fun `query with variables resolves correctly`() = testApplication {
        configureServer()

        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "query": "query Greet(${'$'}name: String!) { greet(name: ${'$'}name) }",
                    "variables": { "name": "Ktor" }
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<GraphQLResponse>(response.bodyAsText())
        assertEquals(JsonPrimitive("Hello, Ktor!"), body.data?.jsonObject?.get("greet"))
    }

    @Test
    fun `query with operation name selects correct operation`() = testApplication {
        configureServer()

        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "query": "query Hello { hello } query Sum { sum(a: 2, b: 3) }",
                    "operationName": "Sum"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<GraphQLResponse>(response.bodyAsText())
        assertEquals(JsonPrimitive(5), body.data?.jsonObject?.get("sum"))
    }

    @Test
    fun `mutation executes successfully`() = testApplication {
        configureServer()

        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "query": "mutation { setMessage(text: \"updated\") }"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<GraphQLResponse>(response.bodyAsText())
        assertEquals(JsonPrimitive("updated"), body.data?.jsonObject?.get("setMessage"))
    }

    @Test
    fun `invalid query returns errors`() = testApplication {
        configureServer()

        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "{ nonExistentField }"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<GraphQLResponse>(response.bodyAsText())
        assertNotNull(body.errors)
        assertTrue(body.errors.isNotEmpty())
    }

    @Test
    fun `syntax error in query returns errors`() = testApplication {
        configureServer()

        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "{ hello"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<GraphQLResponse>(response.bodyAsText())
        assertNotNull(body.errors)
        assertTrue(body.errors.isNotEmpty())
    }

    @Test
    fun `custom endpoint path is respected`() = testApplication {
        routing {
            graphQL {
                endpoint = "api/graphql"
                schema(testSchema) {
                    type("Query") {
                        it.dataFetcher("hello") { "Hello!" }
                            .dataFetcher("greet") { env -> "Hello, ${env.getArgument<String>("name")}!" }
                            .dataFetcher("sum") { env ->
                                env.getArgument<Int>("a")!! + env.getArgument<Int>("b")!!
                            }
                    }
                    type("Mutation") {
                        it.dataFetcher("setMessage") { env -> env.getArgument<String>("text") }
                    }
                }
            }
        }

        val response = client.post("/api/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "{ hello }"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<GraphQLResponse>(response.bodyAsText())
        assertEquals(JsonPrimitive("Hello!"), body.data?.jsonObject?.get("hello"))
    }

    @Test
    fun `graphiql endpoint serves HTML when enabled`() = testApplication {
        routing {
            graphQL {
                graphiql = true
                schema(testSchema) {
                    type("Query") {
                        it.dataFetcher("hello") { "Hello!" }
                            .dataFetcher("greet") { env -> "Hello, ${env.getArgument<String>("name")}!" }
                            .dataFetcher("sum") { env ->
                                env.getArgument<Int>("a")!! + env.getArgument<Int>("b")!!
                            }
                    }
                    type("Mutation") {
                        it.dataFetcher("setMessage") { env -> env.getArgument<String>("text") }
                    }
                }
            }
        }

        val response = client.get("/graphiql")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("GraphiQL"))
        assertTrue(body.contains("graphiql.min.js"))
    }

    @Test
    fun `pre-built schema is accepted`() = testApplication {
        routing {
            val prebuiltSchema = graphql.schema.idl.SchemaGenerator().makeExecutableSchema(
                graphql.schema.idl.SchemaParser().parse("type Query { ping: String }"),
                graphql.schema.idl.RuntimeWiring.newRuntimeWiring()
                    .type("Query") { it.dataFetcher("ping") { "pong" } }
                    .build()
            )

            graphQL {
                schema(prebuiltSchema)
            }
        }

        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "{ ping }"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<GraphQLResponse>(response.bodyAsText())
        assertEquals(JsonPrimitive("pong"), body.data?.jsonObject?.get("ping"))
    }

    @Test
    fun `integer arguments are correctly coerced`() = testApplication {
        configureServer()

        val response = client.post("/graphql") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                    "query": "query Add(${'$'}a: Int!, ${'$'}b: Int!) { sum(a: ${'$'}a, b: ${'$'}b) }",
                    "variables": { "a": 10, "b": 20 }
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<GraphQLResponse>(response.bodyAsText())
        assertEquals(JsonPrimitive(30), body.data?.jsonObject?.get("sum"))
    }

    private fun ApplicationTestBuilder.configureServer() {
        routing {
            graphQL {
                schema(testSchema) {
                    type("Query") {
                        it.dataFetcher("hello") { "Hello, World!" }
                            .dataFetcher("greet") { env -> "Hello, ${env.getArgument<String>("name")}!" }
                            .dataFetcher("sum") { env ->
                                env.getArgument<Int>("a")!! + env.getArgument<Int>("b")!!
                            }
                    }
                    type("Mutation") {
                        it.dataFetcher("setMessage") { env -> env.getArgument<String>("text") }
                    }
                }
            }
        }
    }
}
