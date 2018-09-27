package io.ktor.graphql

import graphql.GraphQLError

fun config(block: GraphQLRouteConfigBuilder.() -> Unit) = GraphQLRouteConfigBuilder().apply(block).build()

class GraphQLRouteConfigBuilder {
    var context: Any? = null
    var rootValue: Any? = null
    var formatError: (GraphQLError.() -> Map<String, Any>)? = null
    var graphiql: Boolean = false
    internal fun build(): GraphQLRouteConfig {
        return GraphQLRouteConfig(context, rootValue, formatError, graphiql)
    }
}

data class GraphQLRouteConfig(
        val context: Any? = null,
        val rootValue: Any? = null,
        val formatError: (GraphQLError.() -> Map<String, Any>)? = null,
        val graphiql: Boolean = false
)