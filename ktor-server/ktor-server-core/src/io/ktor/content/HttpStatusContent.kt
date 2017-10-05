package io.ktor.content

import io.ktor.http.*

data class HttpStatusContent(val code: HttpStatusCode, val message: String = code.description)