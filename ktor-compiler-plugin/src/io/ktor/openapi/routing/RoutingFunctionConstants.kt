package io.ktor.openapi.routing

object RoutingFunctionConstants {
    const val ROUTING_PACKAGE = "io.ktor.server.routing"
    const val ROUTE_INTERFACE = "io.ktor.server.routing.Route"
    const val ROUTING_CONTEXT = "io.ktor.server.routing.RoutingContext"


    const val ROUTE = "route"
    const val GET = "get"
    const val POST = "post"
    const val PUT = "put"
    const val DELETE = "delete"
    const val HEAD = "head"
    const val OPTIONS = "options"
    const val PATCH = "patch"
    const val QUERY = "query"

    val HTTP_METHODS = setOf(GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH, QUERY)
}