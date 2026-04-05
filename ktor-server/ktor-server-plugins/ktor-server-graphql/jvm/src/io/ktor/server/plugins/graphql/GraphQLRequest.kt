/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.graphql

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Represents a GraphQL request received from a client.
 *
 * @property query The GraphQL query string.
 * @property operationName An optional name of the operation to execute when the query contains multiple operations.
 * @property variables An optional map of variable values to substitute into the query.
 * @property extensions An optional map of extension values for protocol extensions.
 */
@Serializable
public data class GraphQLRequest(
    val query: String,
    val operationName: String? = null,
    val variables: Map<String, JsonElement>? = null,
    val extensions: Map<String, JsonElement>? = null,
)

/**
 * Represents a GraphQL response returned to the client.
 *
 * Follows the [GraphQL over HTTP specification](https://graphql.github.io/graphql-over-http/).
 *
 * @property data The result of the executed operation. May be `null` if errors occurred before execution.
 * @property errors A list of errors that occurred during request processing, if any.
 * @property extensions An optional map of extension values for protocol extensions.
 */
@Serializable
public data class GraphQLResponse(
    val data: JsonElement? = null,
    val errors: List<GraphQLError>? = null,
    val extensions: Map<String, JsonElement>? = null,
)

/**
 * Represents a single error in a GraphQL response.
 *
 * @property message A description of the error.
 * @property locations The locations in the GraphQL document where the error occurred, if applicable.
 * @property path The path of the response field that experienced the error, if applicable.
 * @property extensions Additional error information for protocol extensions.
 */
@Serializable
public data class GraphQLError(
    val message: String,
    val locations: List<GraphQLSourceLocation>? = null,
    val path: List<JsonElement>? = null,
    val extensions: Map<String, JsonElement>? = null,
)

/**
 * Represents a location in a GraphQL document.
 *
 * @property line The line number (1-indexed) where the error occurred.
 * @property column The column number (1-indexed) where the error occurred.
 */
@Serializable
public data class GraphQLSourceLocation(
    val line: Int,
    val column: Int,
)
