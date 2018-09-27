package io.ktor.graphql

import graphql.GraphQLError

internal fun formatResult(
        resultData: ExecutionResultData,
        formatError: (GraphQLError.() -> Map<String, Any>)?
): Map<String, Any?>? {

    val executionResult = resultData.result

    val data = executionResult.getData<Any>()

    val errors = executionResult.errors

    val responseMap = mutableMapOf<String, Any?>()

    if (resultData.isDataPresent) {
        responseMap["data"] = data
    }

    if (errors.isNotEmpty()) {

        val outputError = errors.map {
            formatError?.invoke(it) ?: it.toSpecification()
        }
        responseMap["errors"] = outputError
    }

    if (executionResult.extensions != null) {
        responseMap["extensions"] = executionResult.extensions
    }

    return responseMap
}