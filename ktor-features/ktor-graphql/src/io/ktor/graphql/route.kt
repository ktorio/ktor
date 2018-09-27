package io.ktor.graphql

import graphql.schema.GraphQLSchema
import io.ktor.application.ApplicationCall
import io.ktor.pipeline.PipelineContext
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route

fun Route.graphQL(
    path: String,
    schema: GraphQLSchema,
    setup: (PipelineContext<Unit, ApplicationCall>.(GraphQLRequest) -> GraphQLRouteConfig)? = null
): Route {

    val requestHandler = RequestHandler(schema, setup)

    val graphQLRoute: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit = {
        requestHandler.doRequest(this)
    }

    return route(path) {
        get(graphQLRoute)
        post(graphQLRoute)
    }
}