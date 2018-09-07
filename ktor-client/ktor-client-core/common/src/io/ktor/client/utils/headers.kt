package io.ktor.client.utils

import io.ktor.http.*
import io.ktor.util.*

@KtorExperimentalAPI
fun buildHeaders(block: HeadersBuilder.() -> Unit = {}): Headers =
    HeadersBuilder().apply(block).build()
