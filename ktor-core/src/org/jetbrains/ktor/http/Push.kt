package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

fun ApplicationCall.push(pathAndQuery: String) {
    val (path, query) = pathAndQuery.chomp("?") { pathAndQuery to "" }
    push(path, parseQueryString(query))
}

fun ApplicationCall.push(encodedPath: String, parameters: ValuesMap) {
    push {
        url.encodedPath = encodedPath
        url.parameters.clear()
        url.parameters.appendAll(parameters)
    }
}

fun ApplicationCall.push(block: ResponsePushBuilder.() -> Unit) {
    response.push(DefaultResponsePushBuilder(this).apply(block))
}

@Deprecated("Use call.push() instead")
fun ApplicationResponse.push(encodedPath: String, parameters: ValuesMap) {
    call.push(encodedPath, parameters)
}

@Deprecated("Use call.push() instead")
fun ApplicationResponse.push(pathAndQuery: String) {
    call.push(pathAndQuery)
}
