package io.ktor.tests.graphql

import io.ktor.http.formUrlEncode

/**
 * Removes all whitespace from a string. Useful when writing assertions for json strings.
 */
fun removeWhitespace(text: String): String {
    return text.replace("\\s+(?=([^\"]*\"[^\"]*\")*[^\"]*\$)".toRegex(), "")
}

fun urlString(vararg queryParams: Pair<String, String>): String {
    var route = "/graphql"
    if (queryParams.isNotEmpty()) {
        route +="?${queryParams.toList().formUrlEncode()}"
    }
    return route
}

fun stringify(vararg queryParams: Pair<String, String>): String = queryParams.toList().formUrlEncode()