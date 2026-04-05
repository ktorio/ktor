/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.graphql

import graphql.*
import graphql.schema.*
import graphql.schema.idl.*
import io.ktor.server.application.*
import io.ktor.utils.io.*

/**
 * Configuration for the [GraphQL] plugin.
 *
 * Provides a DSL to configure the GraphQL schema, execution strategy,
 * instrumentation, and optional GraphiQL IDE endpoint.
 */
@KtorDsl
public class GraphQLConfig {

    /**
     * The path at which the GraphQL endpoint will be mounted.
     * Defaults to `"graphql"`.
     */
    public var endpoint: String = "graphql"

    /**
     * Whether to enable the GraphiQL interactive IDE.
     * When enabled, a GET request to [graphiqlEndpoint] will serve the GraphiQL HTML page.
     * Defaults to `false`.
     */
    public var graphiql: Boolean = false

    /**
     * The path at which the GraphiQL IDE will be served.
     * Only used when [graphiql] is `true`.
     * Defaults to `"graphiql"`.
     */
    public var graphiqlEndpoint: String = "graphiql"

    internal var schemaFactory: (() -> GraphQLSchema)? = null
    internal var graphQLFactory: ((GraphQLSchema) -> GraphQL)? = null
    internal var contextFactory: (suspend (ApplicationCall) -> Map<Any, Any>)? = null

    /**
     * Configures the GraphQL schema using the SDL (Schema Definition Language) and runtime wiring.
     *
     * @param sdl The GraphQL schema definition in SDL format.
     * @param block A configuration block for the [RuntimeWiring.Builder] to bind data fetchers and type resolvers.
     */
    public fun schema(sdl: String, block: RuntimeWiring.Builder.() -> Unit = {}) {
        schemaFactory = {
            val typeRegistry = SchemaParser().parse(sdl)
            val runtimeWiring = RuntimeWiring.newRuntimeWiring().apply(block).build()
            SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring)
        }
    }

    /**
     * Configures the GraphQL schema using a pre-built [GraphQLSchema] instance.
     *
     * @param schema The pre-built schema.
     */
    public fun schema(schema: GraphQLSchema) {
        schemaFactory = { schema }
    }

    /**
     * Configures the GraphQL schema using a factory function.
     *
     * @param factory A function that produces a [GraphQLSchema].
     */
    public fun schema(factory: () -> GraphQLSchema) {
        schemaFactory = factory
    }

    /**
     * Customizes the [GraphQL] execution engine built from the configured schema.
     *
     * Use this to add instrumentation, custom execution strategies, or other
     * advanced [GraphQL.Builder] settings.
     *
     * @param block A configuration block that receives the schema and returns a configured [GraphQL] instance.
     */
    public fun engine(block: (GraphQLSchema) -> GraphQL) {
        graphQLFactory = block
    }

    /**
     * Provides a custom context factory that produces per-request context values.
     *
     * The returned map is made available to data fetchers via
     * `DataFetchingEnvironment.graphQlContext`.
     *
     * @param factory A suspending function that creates a context map from the [ApplicationCall].
     */
    public fun context(factory: suspend (ApplicationCall) -> Map<Any, Any>) {
        contextFactory = factory
    }
}
