package io.ktor.client.utils

interface HttpMessage {
    val headers: Headers
}

interface HttpMessageBuilder {
    val headers: HeadersBuilder
}

