package io.ktor.client.request

import io.ktor.http.*

/**
 * Gets the associated URL's host.
 */
val HttpRequestBuilder.host: String get() = url.host

/**
 * Gets the associated URL's port.
 */
val HttpRequestBuilder.port: Int get() = url.port

/**
 * Sets a single header of [key] with a specific [value].
 */
fun HttpRequestBuilder.header(key: String, value: String): Unit = headers.append(key, value)

/**
 * Sets the `Accept` header with a specific [contentType].
 */
fun HttpRequestBuilder.accept(contentType: ContentType): Unit = headers.append(HttpHeaders.Accept, contentType.toString())
