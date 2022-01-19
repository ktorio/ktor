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
import io.ktor.util.collections.*
import io.ktor.util.debug.*
import io.ktor.util.pipeline.*
import kotlin.random.*

/**
 * Utility class to build an [ApplicationPlugin] instance.
 **/
@Suppress("UNUSED_PARAMETER", "DEPRECATION")
public abstract class ApplicationPluginBuilder<PluginConfig : Any> internal constructor(
    internal val key: AttributeKey<PluginInstance>
) : PluginBuilder<PluginConfig> {

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

    internal val pipelineHandlers: MutableList<PipelineHandler> = mutableListOf()

    internal val hooks: MutableList<HookHandler<*>> = mutableListOf()

    /**
     * Defines how processing an HTTP call needs to be modified by the current [ApplicationPluginBuilder].
     *
     * @param block An action that needs to be executed when your application receives an HTTP call.
     **/
    public override fun onCall(block: suspend OnCallContext<PluginConfig>.(call: ApplicationCall) -> Unit) {
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
     * Defines how the current [ApplicationPluginBuilder] needs to transform data received from a client.
     *
     * @param block An action that needs to be executed when your application receives data from a client.
     **/
    public override fun onCallReceive(
        block: suspend OnCallReceiveContext<PluginConfig>.(call: ApplicationCall, body: ApplicationReceiveRequest) -> Unit // ktlint-disable max-line-length
    ) {
        onDefaultPhase(
            onReceiveInterceptions,
            ApplicationReceivePipeline.Transform,
            PHASE_ON_CALL_RECEIVE,
            ::OnCallReceiveContext,
        ) { call, body -> block(call, body) }
    }

    /**
     * Specifies how to transform the data. For example, you can write a custom serializer using this method.
     *
     * @param block An action that needs to be executed when your server is sending a response to a client.
     **/
    public override fun onCallRespond(
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
     * Executes specific actions after all [targetPlugins] are executed.
     *
     * @param targetPlugins Plugins that need to be executed before your current [ApplicationPluginBuilder].
     * @param build Defines the code of your plugin that needs to be executed after [targetPlugins].
     *
     * Note: you can define multiple actions inside a [build] callback for multiple stages of handling an HTTP call
     * (such as [onCall], [onCallRespond], and so on). These actions are executed right after all actions defined
     * by the given [plugin] are already executed in the same stage.
     **/
    public fun after(
        vararg targetPlugins: Plugin<*, *, PluginInstance>,
        build: AfterPluginsBuilder<PluginConfig>.() -> Unit
    ) {
        pipelineHandlers.add { pipeline ->
            AfterPluginsBuilder(this, targetPlugins.map { pipeline.plugin(it).builder }).build()
        }
    }

    /**
     * Executes specific actions before all [targetPlugins] are executed.
     *
     * @param targetPlugins Plugins that need to be executed after your current [ApplicationPluginBuilder].
     * @param build Defines the code of your plugin that needs to be executed before [targetPlugins].
     *
     * Note: you can define multiple actions inside a [build] callback for multiple stages of handling an HTTP call
     * (such as [onCall], [onCallRespond], and so on) and each of these actions will be executed right before all actions defined
     * by the given [targetPlugins] were already executed in the same stage.
     **/
    public fun before(
        vararg targetPlugins: Plugin<*, *, PluginInstance>,
        build: BeforePluginsBuilder<PluginConfig>.() -> Unit
    ) {
        pipelineHandlers.add { pipeline ->
            BeforePluginsBuilder(this, targetPlugins.map { pipeline.plugin(it).builder }).build()
        }
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

    public override val onCallRespond: OnCallRespond<PluginConfig> = object : OnCallRespond<PluginConfig> {
        override fun afterTransform(
            block: suspend OnCallRespondAfterTransformContext<PluginConfig>.(ApplicationCall, OutgoingContent) -> Unit
        ) {
            val plugin = this@ApplicationPluginBuilder

            plugin.onDefaultPhaseWithMessage(
                plugin.afterResponseInterceptions,
                ApplicationSendPipeline.After,
                PHASE_ON_CALL_RESPOND_AFTER,
                ::OnCallRespondAfterTransformContext
            ) { call, body -> block(call, body as OutgoingContent) }
        }
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

internal typealias PipelineHandler = (Pipeline<*, ApplicationCall>) -> Unit
