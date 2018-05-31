package io.ktor.client.utils

import io.ktor.http.*

fun buildHeaders(block: HeadersBuilder.() -> Unit = {}): Headers =
    HeadersBuilder().apply(block).build()
