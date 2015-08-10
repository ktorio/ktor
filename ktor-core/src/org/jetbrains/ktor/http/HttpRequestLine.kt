package org.jetbrains.ktor.http

public data class HttpRequestLine(val method: String,
                                  val uri: String,
                                  val version: String
                                 ) {
    override fun toString(): String {
        return "$version - $method $uri"
    }
}

fun HttpRequestLine.path(): String = uri.substringBefore("?")
fun HttpRequestLine.queryString(): String = uri.substringAfter('?', "")
fun HttpRequestLine.document(): String = uri.substringAfterLast('/', "").substringBefore('?')

