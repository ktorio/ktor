package org.jetbrains.ktor.samples.httpbin

import org.jetbrains.ktor.http.*

data class HttpBinError(
        val request: String,
        val message: String,
        val code: HttpStatusCode,
        val cause: Throwable? = null
)