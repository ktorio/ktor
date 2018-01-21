package io.ktor.http

import java.time.*

fun HeadersBuilder.contentType(contentType: ContentType) = set(HttpHeaders.ContentType, contentType.toString())
fun HeadersBuilder.contentLength(length: Long) = set(HttpHeaders.ContentLength, length.toString())
fun HeadersBuilder.etag(entityTag: String) = set(HttpHeaders.ETag, entityTag)
fun HeadersBuilder.lastModified(dateTime: ZonedDateTime) = set(HttpHeaders.LastModified, dateTime.toHttpDateString())
fun HeadersBuilder.expires(expires: LocalDateTime) = set(HttpHeaders.Expires, expires.toHttpDateString())
