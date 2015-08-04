package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*

public data class HttpVerb(val method: String,
                           val uri: String,
                           val version: String
                          ) {
    override fun toString(): String {
        return "$version - $method $uri"
    }
}

fun HttpVerb.path(): String = uri.substringBefore("?")
fun HttpVerb.queryString(): String = uri.substringAfter('?', "")
fun HttpVerb.document(): String = uri.substringAfterLast('/', "").substringBefore('?')

