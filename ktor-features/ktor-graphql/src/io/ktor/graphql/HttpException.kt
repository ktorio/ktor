package io.ktor.graphql

import graphql.GraphQLError
import io.ktor.http.HttpStatusCode

internal class HttpException(val statusCode: HttpStatusCode, val errors: List<GraphQLError>): Exception() {
    constructor(statusCode: HttpStatusCode, error: GraphQLError): this(statusCode, listOf(error))
}