package io.ktor.http

import io.ktor.util.*
import java.time.*

fun StringValuesBuilder.contentType(contentType: ContentType) = set(HttpHeaders.ContentType, contentType.toString())
fun StringValuesBuilder.contentLength(length: Long) = set(HttpHeaders.ContentLength, length.toString())
fun StringValuesBuilder.etag(entityTag: String) = set(HttpHeaders.ETag, entityTag)
fun StringValuesBuilder.lastModified(dateTime: ZonedDateTime) = set(HttpHeaders.LastModified, dateTime.toHttpDateString())
fun StringValuesBuilder.expires(expires: LocalDateTime) = set(HttpHeaders.Expires, expires.toHttpDateString())
