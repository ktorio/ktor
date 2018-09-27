package io.ktor.graphql

import graphql.ErrorType
import graphql.GraphQLError
import graphql.language.SourceLocation

data class HttpGraphQLError(private val message: String): GraphQLError {
    override fun getMessage(): String = message

    override fun getErrorType(): ErrorType? = null

    override fun getLocations(): MutableList<SourceLocation>? = null
}