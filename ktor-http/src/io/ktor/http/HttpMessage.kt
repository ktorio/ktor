package io.ktor.http

/**
 * A message either from the client or the server,
 * that has [headers] associated.
 */
interface HttpMessage {
    val headers: Headers
}

/**
 * A builder message either for the client or the server,
 * that has a [headers] builder associated.
 */
interface HttpMessageBuilder {
    val headers: HeadersBuilder
}
