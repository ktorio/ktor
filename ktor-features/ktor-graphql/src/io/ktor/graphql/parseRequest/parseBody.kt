package io.ktor.graphql.parseRequest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.graphql.GraphQLRequest
import io.ktor.graphql.HttpException
import io.ktor.graphql.HttpGraphQLError
import io.ktor.graphql.mapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode

private val graphQLContentType = ContentType.parse("application/graphql")

internal fun parseBody(body: String, contentType: ContentType): GraphQLRequest {

    if (body.isBlank()) {
        return GraphQLRequest()
    }

    return when(contentType) {
        graphQLContentType -> requestGraphQL(body)
        ContentType.Application.Json -> requestJson(body)
        else -> GraphQLRequest()
    }
}

private fun requestGraphQL(body: String) = GraphQLRequest(body)

private fun requestJson(body: String): GraphQLRequest {

    val bodyNode = jsonEncodedParser(body)

    if (bodyNode.isNull) {
        return GraphQLRequest()
    }

    val query = getQuery(bodyNode)
    val variables = getVariables(bodyNode)
    val operationName = getOperationName(bodyNode)

    return GraphQLRequest(
        query = query,
        operationName = operationName,
        variables = variables
    )
}

private fun getQuery(bodyNode: JsonNode): String? {
    return if (bodyNode.has("query")) {
        bodyNode.get("query").asText()
    } else {
        null
    }
}

private fun getVariables(bodyNode: JsonNode): Map<String, Any>? {
    return if(bodyNode.has("variables")) {
        val variablesNode = bodyNode.get("variables")

        if (!variablesNode.isNull) {
            mapper.readValue<Map<String, Any>>(variablesNode.toString())
        } else {
            null
        }
    } else {
        null
    }
}

private fun getOperationName(bodyNode: JsonNode): String? {
    return if (bodyNode.has("operationName")) {

        val operationNameNode= bodyNode.get("operationName")

        if (operationNameNode.isNull) {
            null
        } else {
            bodyNode.get("operationName").asText()
        }
    } else {
        null
    }
}

fun jsonEncodedParser(body: String): JsonNode {

    if (jsonObjRegex.containsMatchIn(body)) {

        try {
            return mapper.readTree(body)
        } catch (exception: Exception) {
            // DO NOTHING
        }
    }
    throw HttpException(HttpStatusCode.BadRequest, HttpGraphQLError("POST body sent invalid JSON."))
}

/**
 * RegExp to match an Object-opening brace "{" as the first non-space
 * in a string. Allowed whitespace is defined in RFC 7159:
 *
 *     x20  Space
 *     x09  Horizontal tab
 *     x0A  Line feed or New line
 *     x0D  Carriage return
 */
val jsonObjRegex = "^[\\x20\\x09\\x0a\\x0d]*\\{".toRegex()