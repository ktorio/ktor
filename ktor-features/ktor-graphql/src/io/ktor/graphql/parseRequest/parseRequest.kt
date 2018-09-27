package io.ktor.graphql.parseRequest

import io.ktor.graphql.GraphQLRequest
import io.ktor.application.ApplicationCall
import io.ktor.http.HttpStatusCode
import io.ktor.request.contentType
import io.ktor.request.receiveText
import io.ktor.graphql.HttpException
import io.ktor.graphql.HttpGraphQLError

/**
 * See https://graphql.org/learn/serving-over-http/
 */
internal suspend fun parseGraphQLRequest(call: ApplicationCall): GraphQLRequest {
    return try {
        parseRequest(call)
    } catch (exception: Exception) {
        if (exception !is HttpException) {
            throw HttpException(HttpStatusCode.BadRequest, HttpGraphQLError("The GraphQL query could not be parsed"))
        } else {
            throw exception
        }
    }
}

private suspend fun parseRequest(call: ApplicationCall): GraphQLRequest {

    val body = call.receiveText()
    val contentType = call.request.contentType()
    val bodyRequest = parseBody(body, contentType)


    val parameters = call.parameters
    val urlRequest = parseQueststring(parameters)

    val query = urlRequest.query ?: bodyRequest.query
    val variables = urlRequest.variables ?: bodyRequest.variables
    val operationName = urlRequest.operationName ?: bodyRequest.operationName

    return GraphQLRequest(query = query, variables = variables, operationName = operationName)
}


