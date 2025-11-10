/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel

/**
 * Configuration for the [HttpRequestLifecycle] plugin.
 */
public class HttpRequestLifecycleConfig internal constructor() {
    /**
     * When `true`, cancels the call coroutine context if the other peer resets the client connection.
     * When `false` (default), request processing continues even if the connection is closed.
     *
     * Example:
     * ```kotlin
     * install(HttpRequestLifecycle) {
     *     cancelCallOnClose = true
     * }
     * ```
     */
    public var cancelCallOnClose: Boolean = false
}

/**
 * Internal attribute key for storing the connection close handler callback.
 */
@InternalAPI
public val HttpRequestCloseHandlerKey: AttributeKey<() -> Unit> = AttributeKey<() -> Unit>("HttpRequestCloseHandler")

/**
 * A plugin that manages the HTTP request lifecycle, particularly handling client disconnections.
 *
 * The [HttpRequestLifecycle] plugin allows you to detect and respond to client connection closures
 * during request processing. When configured with [HttpRequestLifecycleConfig.cancelCallOnClose] set to `true`,
 * the plugin will automatically cancel the request handling coroutine if the client disconnects,
 * preventing unnecessary processing and freeing up resources.
 *
 * Remember, coroutine cancellation doesn't stop blocking operations, so check [call.coroutineContext.isActive] if needed.
 * Plugin only works for CIO and Netty engines. Other implementations fail on closed connection only when trying to write some response.
 *
 * This is particularly useful for:
 * - Long-running requests where the client may disconnect before completion
 * - Streaming responses where detecting disconnection allows early cleanup
 * - Resource-intensive operations that should be canceled when the client is no longer waiting
 *
 * ## Example
 *
 * ```kotlin
 * install(HttpRequestLifecycle) {
 *     cancelCallOnClose = true
 * }
 *
 * routing {
 *     get("/long-process") {
 *         try {
 *             // Long-running operation
 *             repeat(100) {
 *                 delay(100)
 *                 // Process data...
 *             }
 *             call.respond("Completed")
 *         } catch (e: CancellationException) {
 *             if (e.rootCause is ConnectionClosedException) {
 *                 // Client disconnected, clean up resources
 *                 logger.info("Request cancelled due to client disconnect")
 *             } else {
 *                 throw e
 *             }
 *         }
 *     }
 * }
 * ```
 */
@OptIn(InternalAPI::class)
public val HttpRequestLifecycle: RouteScopedPlugin<HttpRequestLifecycleConfig> = createRouteScopedPlugin(
    name = "HttpRequestLifecycle",
    createConfiguration = ::HttpRequestLifecycleConfig
) {
    on(CallSetup) { call ->
        if (
            !this@createRouteScopedPlugin.pluginConfig.cancelCallOnClose ||
            call.attributes.contains(HttpRequestCloseHandlerKey)
        ) {
            return@on
        }
        call.attributes.put(HttpRequestCloseHandlerKey) {
            val cause = CancellationException(
                "Call context was cancelled by `HttpRequestLifecycle` plugin",
                ConnectionClosedException()
            )
            call.coroutineContext.cancel(cause)
        }
    }
}
