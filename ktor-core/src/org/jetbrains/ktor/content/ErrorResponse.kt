package org.jetbrains.ktor.content

import org.jetbrains.ktor.http.*

data class ErrorResponse(val code: HttpStatusCode, val message: String = code.description)