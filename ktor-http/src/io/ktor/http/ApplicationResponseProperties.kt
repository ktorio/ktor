package io.ktor.http

fun HeadersBuilder.contentType(contentType: ContentType): Unit = set(HttpHeaders.ContentType, contentType.toString())
fun HeadersBuilder.contentLength(length: Long): Unit = set(HttpHeaders.ContentLength, length.toString())
fun HeadersBuilder.etag(entityTag: String): Unit = set(HttpHeaders.ETag, entityTag)
