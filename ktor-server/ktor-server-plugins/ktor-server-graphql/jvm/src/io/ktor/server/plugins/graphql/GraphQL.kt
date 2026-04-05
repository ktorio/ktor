/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.graphql

import graphql.*
import graphql.GraphQL
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.*
import kotlinx.serialization.json.*

private val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.graphql")

/**
 * Installs a GraphQL endpoint under the specified route.
 *
 * This sets up a POST endpoint for executing GraphQL queries and mutations,
 * and optionally a GET endpoint serving the GraphiQL interactive IDE.
 *
 * Example:
 * ```kotlin
 * routing {
 *     graphQL {
 *         schema("""
 *             type Query {
 *                 hello: String
 *             }
 *         """) {
 *             type("Query") {
 *                 dataFetcher("hello") { "Hello, World!" }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param block A configuration block for [GraphQLConfig].
 * @return The created [Route].
 */
public fun Route.graphQL(block: GraphQLConfig.() -> Unit): Route {
    val config = GraphQLConfig().apply(block)

    val schemaFactory = requireNotNull(config.schemaFactory) {
        "GraphQL schema must be configured. Use schema(sdl) { ... } or schema(graphQLSchema) in the graphQL block."
    }

    val schema = schemaFactory()
    val graphql = config.graphQLFactory?.invoke(schema)
        ?: GraphQL.newGraphQL(schema).build()

    val contextFactory = config.contextFactory

    val json = Json { ignoreUnknownKeys = true }

    val route = route(config.endpoint) {
        post {
            val body = call.receiveText()
            val request = json.decodeFromString<GraphQLRequest>(body)
            val response = executeGraphQL(graphql, request, call, contextFactory)
            val responseJson = json.encodeToString(response)
            call.respondText(responseJson, ContentType.Application.Json)
        }
    }

    if (config.graphiql) {
        route(config.graphiqlEndpoint) {
            get {
                val graphqlPath = call.request.path().removeSuffix(config.graphiqlEndpoint) + config.endpoint
                call.respondText(buildGraphiQLPage(graphqlPath), ContentType.Text.Html)
            }
        }
    }

    return route
}

internal suspend fun executeGraphQL(
    graphql: GraphQL,
    request: GraphQLRequest,
    call: ApplicationCall,
    contextFactory: (suspend (ApplicationCall) -> Map<Any, Any>)?,
): GraphQLResponse {
    val inputBuilder = ExecutionInput.newExecutionInput()
        .query(request.query)

    request.operationName?.let { inputBuilder.operationName(it) }
    request.variables?.let { inputBuilder.variables(jsonElementMapToJavaMap(it)) }

    if (contextFactory != null) {
        val contextValues = contextFactory(call)
        inputBuilder.graphQLContext(contextValues)
    }

    val executionResult = try {
        graphql.executeAsync(inputBuilder.build()).await()
    } catch (
        @Suppress("TooGenericExceptionCaught")
        e: Exception
    ) {
        LOGGER.error("GraphQL execution failed", e)
        return GraphQLResponse(
            errors = listOf(
                GraphQLError(message = e.message ?: "Internal server error")
            )
        )
    }

    return executionResult.toGraphQLResponse()
}

internal fun ExecutionResult.toGraphQLResponse(): GraphQLResponse {
    val jsonData = getData<Any?>()?.let { javaValueToJsonElement(it) }
    val jsonErrors = errors?.takeIf { it.isNotEmpty() }?.map { error ->
        GraphQLError(
            message = error.message,
            locations = error.locations?.map { loc ->
                GraphQLSourceLocation(line = loc.line, column = loc.column)
            },
            path = error.path?.map { segment ->
                when (segment) {
                    is Int -> JsonPrimitive(segment)
                    is Number -> JsonPrimitive(segment.toInt())
                    else -> JsonPrimitive(segment.toString())
                }
            },
            extensions = error.extensions?.let { javaMapToJsonElementMap(it) },
        )
    }
    val jsonExtensions = extensions?.takeIf { it.isNotEmpty() }?.let { javaMapToJsonElementMap(it) }

    return GraphQLResponse(
        data = jsonData,
        errors = jsonErrors,
        extensions = jsonExtensions,
    )
}

@Suppress("UNCHECKED_CAST")
internal fun javaValueToJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(
        (value as Map<String, Any?>).mapValues { (_, v) -> javaValueToJsonElement(v) }
    )
    is List<*> -> JsonArray(value.map { javaValueToJsonElement(it) })
    else -> JsonPrimitive(value.toString())
}

@Suppress("UNCHECKED_CAST")
internal fun javaMapToJsonElementMap(map: Map<*, *>): Map<String, JsonElement> =
    (map as Map<String, Any?>).mapValues { (_, v) -> javaValueToJsonElement(v) }

internal fun jsonElementToJavaValue(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.content.equals("true", ignoreCase = true) -> true
        element.content.equals("false", ignoreCase = true) -> false
        element.content.contains('.') -> element.content.toDoubleOrNull() ?: element.content
        else -> element.content.toLongOrNull() ?: element.content
    }
    is JsonObject -> element.mapValues { (_, v) -> jsonElementToJavaValue(v) }
    is JsonArray -> element.map { jsonElementToJavaValue(it) }
}

internal fun jsonElementMapToJavaMap(map: Map<String, JsonElement>): Map<String, Any?> =
    map.mapValues { (_, v) -> jsonElementToJavaValue(v) }

internal fun buildGraphiQLPage(graphqlEndpoint: String): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>GraphiQL</title>
    <link rel="stylesheet" href="https://unpkg.com/graphiql/graphiql.min.css" />
</head>
<body style="margin: 0;">
    <div id="graphiql" style="height: 100vh;"></div>
    <script crossorigin src="https://unpkg.com/react/umd/react.production.min.js"></script>
    <script crossorigin src="https://unpkg.com/react-dom/umd/react-dom.production.min.js"></script>
    <script crossorigin src="https://unpkg.com/graphiql/graphiql.min.js"></script>
    <script>
        const fetcher = GraphiQL.createFetcher({ url: '$graphqlEndpoint' });
        ReactDOM.render(
            React.createElement(GraphiQL, { fetcher: fetcher }),
            document.getElementById('graphiql'),
        );
    </script>
</body>
</html>
""".trimIndent()
