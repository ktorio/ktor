package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

fun ApplicationResponse.push(pathAndQuery: String) {
    push {
        val (path, query) = pathAndQuery.chomp("?") { pathAndQuery to "" }
        push(path, parseQueryString(query))
    }
}

fun ApplicationResponse.push(encodedPath: String, parameters: ValuesMap) {
    push {
        url.encodedPath = encodedPath
        url.parameters.clear()
        url.parameters.appendAll(parameters)
    }
}



