package io.ktor.client.request

import io.ktor.http.*

val HttpRequestBuilder.host get() = url.host
val HttpRequestBuilder.port get() = url.port

fun HttpRequestBuilder.header(key: String, value: String) = headers.append(key, value)
fun HttpRequestBuilder.accept(contentType: ContentType) = headers.append(HttpHeaders.Accept, contentType.toString())
