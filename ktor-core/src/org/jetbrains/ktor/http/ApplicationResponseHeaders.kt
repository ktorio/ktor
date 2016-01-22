package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.time.*
import java.time.temporal.*

fun ApplicationResponse.contentType(value: ContentType) = contentType(value.toString())
fun ApplicationResponse.contentType(value: String) = headers.append(HttpHeaders.ContentType, value)
fun ApplicationResponse.header(name: String, value: String) = headers.append(name, value)
fun ApplicationResponse.header(name: String, value: Int) = headers.append(name, value.toString())
fun ApplicationResponse.header(name: String, value: Long) = headers.append(name, value.toString())
fun ApplicationResponse.header(name: String, date: Temporal) = headers.append(name, date.toHttpDateString())

fun ApplicationResponse.etag(value: String) = header(HttpHeaders.ETag, value)
fun ApplicationResponse.lastModified(value: Long) = header(HttpHeaders.LastModified, LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault()))
fun ApplicationResponse.contentLength(value: Long) = header(HttpHeaders.ContentLength, value)


