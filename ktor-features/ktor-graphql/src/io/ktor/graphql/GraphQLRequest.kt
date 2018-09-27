package io.ktor.graphql

data class GraphQLRequest(
        val query: String? = null,
        val operationName: String? = null,
        val variables: Map<String, Any>? = null
)