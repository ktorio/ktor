/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.server.application.plugins.api

import io.ktor.server.application.*
import io.ktor.server.application.plugins.api.debug.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.debug.*
import io.ktor.util.pipeline.*
import kotlin.random.*

/**
 * A builder that is available inside a plugin creation block. It allows you to define handlers for different stages
 * (a.k.a. phases) of the HTTP pipeline.
 **/
public interface PluginBuilderBase {
    /**
     * Specifies how to modify HTTP call handling for the current [PluginBuilder].
     * @see OnCall
     **/
    public val onCall: OnCall

    /**
     * Specifies how to modify receiving data from an HTTP call for the current [PluginBuilder].
     * @see OnCallReceive
     **/
    public val onCallReceive: OnCallReceive

    /**
     * Specifies how to modify sending data within an HTTP call for the current [PluginBuilder].
     * @see OnCallRespond
     **/
    public val onCallRespond: OnCallRespond

    /**
     * Specifies a shutdown hook. This method is useful for closing resources allocated by the plugin.
     *
     * @param hook An action that needs to be executed when the application shuts down.
     **/
    public fun applicationShutdownHook(hook: (Application) -> Unit)
}

/**
 * A plugin that embeds into the HTTP pipeline and extends Ktor functionality.
 **/
public sealed class PluginBuilder<PluginConfigT : Any> private constructor(
    internal val key: AttributeKey<PluginInstance>
) : PluginBuilderBase {

    /**
     * An implementation of [PluginBuilder] that can be installed into [Application]
     **/
    public abstract class ApplicationPluginBuilder<PluginConfigT : Any> internal constructor(
        pluginFactory: ApplicationPlugin<Application, PluginConfigT, PluginInstance>
    ) : PluginBuilder<PluginConfigT>(pluginFactory.key) {
        public abstract val pluginConfig: PluginConfigT
    }

    /**
     * An implementation of [PluginBuilder] that can be installed into [io.ktor.server.routing.Routing]
     **/
    public abstract class SubroutePluginBuilder<PluginConfigT : Any> internal constructor(
        pluginFactory: SubroutePlugin<PluginConfigT, PluginInstance>
    ) : PluginBuilder<PluginConfigT>(pluginFactory.key) {
        /**
         * A PluginConfigT for the current plugin for this pipeline. It may hold required data and any
         * functionality that can be used by your plugin.
         **/
        public val PipelineContext<*, ApplicationCall>.pluginConfig: PluginConfigT
            get() = call.attributes[configKey]

        /**
         * A PluginConfigT for the current plugin for this pipeline. It may hold required data and any
         * functionality that can be used by your plugin.
         **/
        public val CallHandlingContext.pluginConfig: PluginConfigT
            get() = context.pluginConfig

        /**
         * A unique key that identifies a plugin PluginConfigT
         */
        public val configKey: AttributeKey<PluginConfigT> = pluginFactory.configKey
    }

    /**
     * A pipeline PluginConfigT for the current plugin. See [Pipelines](https://ktor.io/docs/pipelines.html)
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

    internal fun newPhase(): PipelinePhase = PipelinePhase("${key.name}Phase${Random.nextInt()}")

    private fun <T : Any, ContextT : CallHandlingContext> onDefaultPhaseWithMessage(
        interceptions: MutableList<Interception<T>>,
        phase: PipelinePhase,
        handlerName: String,
        contextInit: (PipelineContext<T, ApplicationCall>) -> ContextT,
        block: suspend ContextT.(ApplicationCall, Any) -> Unit
    ) {
        interceptions.add(
            Interception(
                phase,
                action = { pipeline ->
                    pipeline.intercept(phase) {
                        // Information about the plugin name is needed for Intellij Idea debugger.
                        addToContextInDebugMode(PluginName(key.name)) {
                            ijDebugReportHandlerStarted(pluginName = key.name, handler = handlerName)

                            // Perform current plugin's handler
                            contextInit(this@intercept).block(call, subject)

                            ijDebugReportHandlerFinished(pluginName = key.name, handler = handlerName)
                        }
                    }
                }
            )
        )
    }

    private fun <T : Any, ContextT : CallHandlingContext> onDefaultPhase(
        interceptions: MutableList<Interception<T>>,
        phase: PipelinePhase,
        handlerName: String,
        contextInit: (PipelineContext<T, ApplicationCall>) -> ContextT,
        block: suspend ContextT.(ApplicationCall) -> Unit
    ) {
        onDefaultPhaseWithMessage(interceptions, phase, handlerName, contextInit) { call, _ -> block(call) }
    }

    /**
     * Specifies how to modify HTTP call handling for the current [PluginBuilder].
     * @see OnCall
     **/
    public override val onCall: OnCall = object : OnCall {
        private val plugin get() = this@PluginBuilder

        override operator fun invoke(block: suspend CallContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.callInterceptions,
                ApplicationCallPipeline.Plugins,
                PHASE_ON_CALL,
                ::CallContext
            ) { call ->
                block(call)
            }
        }
    }

    /**
     * Specifies how to modify receiving data from an HTTP call for the current [PluginBuilder].
     * @see OnCallReceive
     **/
    public override val onCallReceive: OnCallReceive = object : OnCallReceive {
        private val plugin = this@PluginBuilder

        override fun invoke(block: suspend CallReceiveContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.onReceiveInterceptions,
                ApplicationReceivePipeline.Transform,
                PHASE_ON_CALL_RECEIVE,
                ::CallReceiveContext,
                block
            )
        }
    }

    /**
     * Specifies how to modify sending data within an HTTP call for the current [PluginBuilder].
     * @see OnCallRespond
     **/
    public override val onCallRespond: OnCallRespond = object : OnCallRespond {
        private val plugin = this@PluginBuilder

        override fun invoke(block: suspend CallRespondContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.onResponseInterceptions,
                ApplicationSendPipeline.Transform,
                PHASE_ON_CALL_RESPOND,
                ::CallRespondContext,
                block
            )
        }

        override fun afterTransform(block: suspend CallRespondAfterTransformContext.(ApplicationCall, Any) -> Unit) {
            plugin.onDefaultPhaseWithMessage(
                plugin.afterResponseInterceptions,
                ApplicationSendPipeline.After,
                PHASE_ON_CALL_RESPOND_AFTER,
                ::CallRespondAfterTransformContext,
                block
            )
        }
    }

    /**
     * Executes specific actions after all [targetPlugins] are executed.
     *
     * @param targetPlugins Plugins that need to be executed before your current [PluginBuilder].
     * @param build Defines the code of your plugin that needs to be executed after [targetPlugins].
     *
     * Note: you can define multiple actions inside a [build] callback for multiple stages of handling an HTTP call
     * (such as [onCall], [onCallRespond], and so on). These actions are executed right after all actions defined
     * by the given [plugin] are already executed in the same stage.
     **/
    public fun afterPlugins(
        vararg targetPlugins: Plugin<*, *, PluginInstance>,
        build: AfterPluginBuilder.() -> Unit
    ) {
        pipelineHandlers.add { pipeline ->
            AfterPluginBuilder(this, targetPlugins.map { pipeline.plugin(it).builder }).build()
        }
    }

    /**
     * Executes specific actions before all [targetPlugins] are executed.
     *
     * @param targetPlugins Plugins that need to be executed after your current [PluginBuilder].
     * @param build Defines the code of your plugin that needs to be executed before [targetPlugins].
     *
     * Note: you can define multiple actions inside a [build] callback for multiple stages of handling an HTTP call
     * (such as [onCall], [onCallRespond], and so on) and each of these actions will be executed right before all actions defined
     * by the given [targetPlugins] were already executed in the same stage.
     **/
    public fun beforePlugins(
        vararg targetPlugins: Plugin<*, *, PluginInstance>,
        build: BeforePluginsBuilder.() -> Unit
    ) {
        pipelineHandlers.add { pipeline ->
            BeforePluginsBuilder(this, targetPlugins.map { pipeline.plugin(it).builder }).build()
        }
    }

    override fun applicationShutdownHook(hook: (Application) -> Unit) {
        environment?.monitor?.subscribe(ApplicationStopped) { app ->
            hook(app)
        }
    }
}
