package io.ktor.client.request

import io.ktor.http.*

/**
 * Gets the associated URL's host.
 */
var HttpRequestBuilder.host: String
    get() = url.host
    set(value) {
        url.host = value
    }

/**
 * Gets the associated URL's port.
 */
var HttpRequestBuilder.port: Int
    get() = url.port
    set(value) {
        url.port = value
    }

/**
 * Sets a single header of [key] with a specific [value].
 */
fun HttpRequestBuilder.header(key: String, value: String): Unit = headers.append(key, value)

/**
 * Sets a single parameter of [key] with a specific [value].
 */
fun HttpRequestBuilder.parameter(key: String, value: String): Unit = url.parameters.append(key, value)

/**
 * Sets the `Accept` header with a specific [contentType].
 */
fun HttpRequestBuilder.accept(contentType: ContentType): Unit =
    headers.append(HttpHeaders.Accept, contentType.toString())
