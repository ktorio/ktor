package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.net.*

/**
 * Executes a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
suspend inline fun <reified T> HttpClient.get(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
suspend inline fun <reified T> HttpClient.post(
    url: URL,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post {
    this.url.takeFrom(url)
    block()
}

/**
 * Executes a [HttpClient] GET request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
suspend inline fun <reified T> HttpClient.get(
    url: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = get(URL(url), block = block)

/**
 * Executes a [HttpClient] POST request, with the specified [url] as URL and
 * an optional [block] receiving an [HttpRequestBuilder] for further configuring the request.
 *
 * Tries to receive a specific type [T], if fails, an exception is thrown.
 */
suspend inline fun <reified T> HttpClient.post(
    url: String,
    block: HttpRequestBuilder.() -> Unit = {}
): T = post(URL(url), block = block)
