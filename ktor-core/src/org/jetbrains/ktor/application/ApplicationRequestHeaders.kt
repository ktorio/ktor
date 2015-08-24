package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*

fun ApplicationRequest.queryString(): String = requestLine.queryString()

fun ApplicationRequest.queryParameters(): ValuesMap {
    val query = queryString()
    if (query.isEmpty())
        return ValuesMap.Empty
    return ValuesMap.build {
        for (item in query.split("&")) {
            val pair = item.split("=")
            val name = pair[0]
            when (pair.size()) {
                1 -> append(name, "")
                2 -> append(name, pair[1])
            }
        }
    }
}

fun ApplicationRequest.contentType(): ContentType = header("Content-Type")?.let { ContentType.parse(it) } ?: ContentType.Any
fun ApplicationRequest.document(): String = requestLine.document()
fun ApplicationRequest.path(): String = requestLine.path()
fun ApplicationRequest.authorization(): String? = header("Authorization")
fun ApplicationRequest.location(): String? = header("Location")
fun ApplicationRequest.accept(): String? = header("Accept")
fun ApplicationRequest.acceptEncoding(): String? = header("Accept-Encoding")
fun ApplicationRequest.acceptLanguage(): String? = header("Accept-Language")
fun ApplicationRequest.acceptCharset(): String? = header("Accept-Charset")
fun ApplicationRequest.isChunked(): Boolean = header("Transfer-Encoding")?.compareTo("chunked", ignoreCase = true) == 0
fun ApplicationRequest.userAgent(): String? = header("User-Agent")
fun ApplicationRequest.cacheControl(): String? = header("Cache-Control")
fun ApplicationRequest.host(): String? = header("Host")?.substringBefore(':')
fun ApplicationRequest.port(): Int = header("Host")?.substringAfter(':', "80")?.toInt() ?: 80
