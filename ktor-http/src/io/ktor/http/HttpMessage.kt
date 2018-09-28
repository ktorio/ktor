package io.ktor.http

/**
 * A message either from the client or the server,
 * that has [headers] associated.
 */
interface HttpMessage {
    /**
     * Message [Headers]
     */
    val headers: Headers
}

/**
 * A builder message either for the client or the server,
 * that has a [headers] builder associated.
 */
interface HttpMessageBuilder {
    /**
     * MessageBuilder [HeadersBuilder]
     */
    val headers: HeadersBuilder
}
