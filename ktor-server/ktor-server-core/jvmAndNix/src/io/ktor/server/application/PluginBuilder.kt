/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.http.content.*
import io.ktor.server.application.debug.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.debug.*
import io.ktor.util.pipeline.*
import kotlin.random.*

/**
 * Utility class to build an [ApplicationPlugin] instance.
 **/
@Suppress("UNUSED_PARAMETER", "DEPRECATION")
public abstract class PluginBuilder<PluginConfig : Any> internal constructor(
    internal val key: AttributeKey<PluginInstance>
) {

    /**
     * A reference to the [Application] where the plugin is installed.
     */
    public abstract val application: Application

    /**
     * Configuration of current plugin.
     */
    public abstract val pluginConfig: PluginConfig

    /**
     * A pipeline PluginConfig for the current plugin. See [Pipelines](https://ktor.io/docs/pipelines.html)
     * for more information.
     **/
    internal abstract val pipeline: ApplicationCallPipeline

    /**
     * Allows you to access the environment of the currently running application where the plugin is installed.
     **/
    public val environment: ApplicationEnvironment? get() = pipeline.environment

    /**
     * Configuration of your current application (incl. host, port and anything else you can define in application.conf).
     **/
    public val applicationConfig: ApplicationConfig? get() = environment?.config

    internal val callInterceptions: MutableList<CallInterception> = mutableListOf()

    internal val onReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()

    internal val onResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()

    internal val afterResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()

    internal val hooks: MutableList<HookHandler<*>> = mutableListOf()

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
     *
     * @param block An action that needs to be executed when your application receives an HTTP call.
     **/
    public fun onCall(block: suspend OnCallContext<PluginConfig>.(call: ApplicationCall) -> Unit) {
        onDefaultPhase(
            callInterceptions,
            ApplicationCallPipeline.Plugins,
            PHASE_ON_CALL,
            ::OnCallContext
        ) { call, _ ->
            block(call)
        }
    }

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
     *     onCallReceive { call, body ->
     *         println("Body is $body")
     *     }
     * }
     *
     * ```
     * @param block An action that needs to be executed when your application receives data from a client.
     **/
    public fun onCallReceive(
        block: suspend OnCallReceiveContext<PluginConfig>.(call: ApplicationCall, body: Any) -> Unit
    ) {
        onDefaultPhase(
            onReceiveInterceptions,
            ApplicationReceivePipeline.Transform,
            PHASE_ON_CALL_RECEIVE,
            ::OnCallReceiveContext,
        ) { call, body: Any -> block(call, body) }
    }

    /**
     * Specifies the [block] handler for every [call.respond()] statement.
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
     *
     * @param block An action that needs to be executed when your server is sending a response to a client.
     **/
    public fun onCallRespond(
        block: suspend OnCallRespondContext<PluginConfig>.(call: ApplicationCall, body: Any) -> Unit
    ) {
        onDefaultPhase(
            onResponseInterceptions,
            ApplicationSendPipeline.Transform,
            PHASE_ON_CALL_RESPOND,
            ::OnCallRespondContext,
            block
        )
    }

    /**
     * Specifies a [handler] for a specific [hook]. A [hook] can be a specific place in time or event during the request
     * processing like application shutdown, exception during call processing, etc.
     *
     * Example:
     * ```kotlin
     * val ResourceManager = createApplicationPlugin("ResourceManager") {
     *     val resources: List<Closeable> = TODO()
     *
     *     on(Shutdown) {
     *         resources.forEach { it.close() }
     *     }
     * }
     * ```
     */
    public fun <HookHandler> on(
        hook: Hook<HookHandler>,
        handler: HookHandler
    ) {
        hooks.add(HookHandler(hook, handler))
    }

    /**
     * Specifies the [block] handler for every [call.respond()] statement.
     *
     * This [block] is be invoked for every attempt to send the response.
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
    public val onCallRespond: OnCallRespond<PluginConfig> = object : OnCallRespond<PluginConfig> {
        override fun afterTransform(
            block: suspend OnCallRespondAfterTransformContext<PluginConfig>.(ApplicationCall, OutgoingContent) -> Unit
        ) {
            val plugin = this@PluginBuilder

            plugin.onDefaultPhaseWithMessage(
                plugin.afterResponseInterceptions,
                ApplicationSendPipeline.After,
                PHASE_ON_CALL_RESPOND_AFTER,
                ::OnCallRespondAfterTransformContext
            ) { call, body -> block(call, body as OutgoingContent) }
        }
    }

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

    private fun <T : Any, ContextT : CallContext<PluginConfig>> onDefaultPhaseWithMessage(
        interceptions: MutableList<Interception<T>>,
        phase: PipelinePhase,
        handlerName: String,
        contextInit: (pluginConfig: PluginConfig, PipelineContext<T, ApplicationCall>) -> ContextT,
        block: suspend ContextT.(ApplicationCall, T) -> Unit
    ) {
        interceptions.add(
            Interception(
                phase,
                action = { pipeline ->
                    pipeline.intercept(phase) {
                        // Information about the plugin name is needed for the Intellij Idea debugger.
                        val key = this@PluginBuilder.key
                        val pluginConfig = this@PluginBuilder.pluginConfig
                        addToContextInDebugMode(PluginName(key.name)) {
                            ijDebugReportHandlerStarted(pluginName = key.name, handler = handlerName)

                            // Perform current plugin's handler
                            contextInit(pluginConfig, this@intercept).block(call, subject)

                            ijDebugReportHandlerFinished(pluginName = key.name, handler = handlerName)
                        }
                    }
                }
            )
        )
    }

    private fun <T : Any, ContextT : CallContext<PluginConfig>> onDefaultPhase(
        interceptions: MutableList<Interception<T>>,
        phase: PipelinePhase,
        handlerName: String,
        contextInit: (pluginConfig: PluginConfig, PipelineContext<T, ApplicationCall>) -> ContextT,
        block: suspend ContextT.(call: ApplicationCall, body: T) -> Unit
    ) {
        onDefaultPhaseWithMessage(interceptions, phase, handlerName, contextInit) { call, body -> block(call, body) }
    }

    internal fun newPhase(): PipelinePhase = PipelinePhase("${key.name}Phase${Random.nextInt()}")
}
