package io.ktor.http

/**
 * Set `Content-Type` header
 */
@Suppress("unused", "UNUSED_PARAMETER", "DeprecatedCallableAddReplaceWith")
@Deprecated("Content-Type need to be passed in OutgoingContent.contentType", level = DeprecationLevel.ERROR)
fun HeadersBuilder.contentType(contentType: ContentType): Unit = TODO("Not supported anymore")

/**
 * Set `Content-Length` header
 */
@Deprecated(
    "Content-Length need to be passed in OutgoingContent.contentLength",
    level = DeprecationLevel.ERROR
)
@Suppress("unused", "UNUSED_PARAMETER", "DeprecatedCallableAddReplaceWith")
fun HeadersBuilder.contentLength(length: Long): Unit = TODO("Not supported anymore")

/**
 * Set `E-Tag` header
 */
fun HeadersBuilder.etag(entityTag: String): Unit = set(HttpHeaders.ETag, entityTag)
