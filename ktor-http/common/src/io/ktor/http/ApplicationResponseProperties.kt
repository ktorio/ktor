package io.ktor.http

/**
 * Set `E-Tag` header
 */
fun HeadersBuilder.etag(entityTag: String): Unit = set(HttpHeaders.ETag, entityTag)
