/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.server.application

import io.ktor.server.application.debug.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.debug.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import kotlin.random.*

/**
 * A builder that is available inside a plugin creation block. You can use this builder to hook in various steps of request processing.
 **/
@KtorDsl
public sealed interface PluginBuilder<PluginConfig : Any> {

    /**
     * A reference to the [Application] where the plugin is installed.
     */
    public val application: Application

    /**
     * Specifies the [block] handler for every incoming [ApplicationCall].
     *
     * This block is invoked for every incoming call even if the call is already handled by some different handler.
     * There you can handle the call in a way you want: add headers, change the response status, etc. You can also
     * access the external state to calculate stats.
     *
     * Example:
     * ```kotlin
     *
     * val plugin = createApplicationPlugin("CallCounterHeader") {
     *     var counter = 0
     *
     *     onCall { call ->
     *         counter++
     *         call.response.header("X-Call-Count", "$counter")
     *     }
     * }
     *
     * ```
     **/
    public fun onCall(block: suspend OnCallContext<PluginConfig>.(call: ApplicationCall) -> Unit)

    /**
     * Specifies the [block] handler for every [call.receive()] statement.
     *
     * This [block] is invoked for every attempt to receive the request body.
     * You can observe the [receiveRequest] of the body. You can also modify the body using the [transformBody] block.
     *
     * Example:
     * ```kotlin
     *
     * val ReceiveTypeLogger = createApplicationPlugin("ReceiveTypeLogger") {
     *     onCallReceive { call, receiveRequest ->
     *         println("Requested ${receiveRequest.typeInfo} type")
     *     }
     * }
     *
     * ```
     *
     * @param body lets you monitor the body content.
     **/
    public fun onCallReceive(
        block: suspend OnCallReceiveContext<PluginConfig>.(
            call: ApplicationCall,
            receiveRequest: ApplicationReceiveRequest
        ) -> Unit
    )

    /**
     * Specifies the [block] handler for every [call.respond()] statement.
     *
     * This [block] is invoked for every attempt to send the response.
     *
     * @param body lets you monitor the body content.
     *
     * Example:
     *
     * ```kotlin
     *
     * val BodyLimiter = createApplicationPlugin("BodyLimiter") {
     *     onCallRespond { _: ApplicationCall, body: Any ->
     *         if (body is ByteArray) {
     *             check(body.size < 4 * 1024 * 1024) { "Body size is too big: ${body.size} bytes" }
     *         }
     *     }
     *  }
     *
     * ```
     **/
    public fun onCallRespond(
        block: suspend OnCallRespondContext<PluginConfig>.(call: ApplicationCall, body: Any) -> Unit
    )

    /**
     * Specifies the [block] handler for every [call.respond()] statement.
     *
     * This [block] is be invoked for every attempt to send the response.
     *
     * @param body lets you monitor the body content.
     *
     * Example:
     *
     * ```kotlin
     *
     * val BodyLimiter = createApplicationPlugin("BodyLimiter") {
     *     onCallRespond { _: ApplicationCall, body: Any ->
     *         if (body is ByteArray) {
     *             check(body.size < 4 * 1024 * 1024) { "Body size is too big: ${body.size} bytes" }
     *         }
     *     }
     *  }
     *
     * ```
     **/
    public val onCallRespond: OnCallRespond<PluginConfig>

    /**
     * Specifies the [block] handler for every [call.receive()] statement.
     *
     * This [block] will be invoked for every attempt to receive the request body.
     * You can observe the [receiveRequest] of the body. You can also modify the body using the [transformBody] block.
     *
     * Example:
     * ```kotlin
     *
     * val ContentType = createApplicationPlugin("ReceiveTypeLogger") {
     *     onCallReceive { call ->
     *         println("Received ${call.request.headers(HttpHeaders.ContentType)} type")
     *     }
     * }
     *
     * ```
     *
     * @param body lets you monitor the body content.
     **/
    public fun onCallReceive(
        block: suspend OnCallReceiveContext<PluginConfig>.(call: ApplicationCall) -> Unit
    ) {
        onCallReceive { call, _ -> block(call) }
    }

    /**
     * Specifies the [block] handler for every [call.respond()] statement.
     *
     * This [block] is invoked for every attempt to send the response.
     *
     * @param body lets you monitor the body content.
     *
     * Example:
     *
     * ```kotlin
     *
     * val NoKeepAlive = createApplicationPlugin("NoKeepAlive") {
     *     onCallRespond { call: ApplicationCall ->
     *         call.respond.header("Connection", "close")
     *     }
     *  }
     *
     * ```
     **/
    public fun onCallRespond(
        block: suspend OnCallRespondContext<PluginConfig>.(call: ApplicationCall) -> Unit
    ) {
        onCallRespond { call, _ -> block(call) }
    }
}
