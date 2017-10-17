package io.ktor.http.response

import io.ktor.http.*
import io.ktor.util.*
import java.time.*

fun ValuesMapBuilder.contentType(contentType: ContentType) = set(HttpHeaders.ContentType, contentType.toString())
fun ValuesMapBuilder.contentLength(length: Long) = set(HttpHeaders.ContentLength, length.toString())
fun ValuesMapBuilder.etag(entityTag: String) = set(HttpHeaders.ETag, entityTag)
fun ValuesMapBuilder.lastModified(dateTime: ZonedDateTime) = set(HttpHeaders.LastModified, dateTime.toHttpDateString())
fun ValuesMapBuilder.expires(expires: LocalDateTime) = set(HttpHeaders.Expires, expires.toHttpDateString())
