package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*

fun ApplicationRequest.queryString(): String = uri.substringAfter('?', "")

fun ApplicationRequest.queryParameters(): Map<String, List<String>> {
    val query = queryString()
    if (query.isEmpty())
        return mapOf()
    val parameters = hashMapOf<String, MutableList<String>>()
    for (item in query.split("&")) {
        val pair = item.split("=")
        when (pair.size()) {
            1 -> parameters.getOrPut(pair[0], { arrayListOf() }).add("")
            2 -> parameters.getOrPut(pair[0], { arrayListOf() }).add(pair[1])
        }
    }
    return parameters
}

fun ApplicationRequest.contentType(): ContentType = header("Content-Type")?.let { ContentType.parse(it) } ?: ContentType.Any
fun ApplicationRequest.document(): String = uri.substringAfterLast('/', "").substringBefore('?')
fun ApplicationRequest.path(): String = uri.substringBefore("?")
fun ApplicationRequest.authorization(): String? = header("Authorization")
fun ApplicationRequest.accept(): String? = header("Accept")
fun ApplicationRequest.acceptEncoding(): String? = header("Accept-Encoding")
fun ApplicationRequest.acceptLanguage(): String? = header("Accept-Language")
fun ApplicationRequest.acceptCharset(): String? = header("Accept-Charset")
fun ApplicationRequest.isChunked(): Boolean = header("Transfer-Encoding")?.compareTo("chunked", ignoreCase = true) == 0
fun ApplicationRequest.userAgent(): String? = header("User-Agent")
fun ApplicationRequest.cacheControl(): String? = header("Cache-Control")
fun ApplicationRequest.host(): String? = header("Host")?.substringBefore(':')
fun ApplicationRequest.port(): Int = header("Host")?.substringAfter(':')?.toInt() ?: 80
