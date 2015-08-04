package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*

fun ApplicationResponse.status(code: HttpStatusCode) = status(code.value)
fun ApplicationResponse.contentType(value: ContentType) = contentType(value.toString())
fun ApplicationResponse.contentType(value: String) = header("Content-Type", value)
