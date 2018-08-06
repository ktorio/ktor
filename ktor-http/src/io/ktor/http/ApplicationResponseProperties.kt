package io.ktor.http

fun HeadersBuilder.contentType(contentType: ContentType) = set(HttpHeaders.ContentType, contentType.toString())
fun HeadersBuilder.contentLength(length: Long) = set(HttpHeaders.ContentLength, length.toString())
fun HeadersBuilder.etag(entityTag: String) = set(HttpHeaders.ETag, entityTag)
