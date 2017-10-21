package io.ktor.client.request

import io.ktor.http.*


val HttpRequest.host get() = url.host
val HttpRequestBuilder.host get() = url.host

fun HttpRequestBuilder.header(key: String, value: String) = headers.append(key, value)
fun HttpRequestBuilder.accept(contentType: ContentType) = headers.append(HttpHeaders.Accept, contentType.toString())

