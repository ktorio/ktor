package io.ktor.samples.httpbin

import io.ktor.http.*

data class HttpBinError(
        val request: String,
        val message: String,
        val code: HttpStatusCode,
        val cause: Throwable? = null
)