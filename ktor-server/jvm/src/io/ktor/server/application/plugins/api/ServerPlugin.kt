/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION")

package io.ktor.server.application.plugins.api

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlin.random.Random

/**
 * A plugin for Ktor that embeds into the HTTP pipeline and extends functionality of Ktor framework.
 **/
public abstract class ServerPlugin<Configuration : Any> private constructor(
    pluginFactory: ServerPluginFactory<Configuration>
) : PluginContext {
    /**
     * [ServerPlugin] encapsulates a bunch of actions you can perform with HTTP pipeline.
     * [pipeline] represents an [ApplicationCallPipeline] object.
     *
     * Please, see https://ktor.io/docs/pipelines.html for more information.
     **/
    protected abstract val pipeline: ApplicationCallPipeline

    /**
     * A configuration for the current plugin. It may hold required data and any functionality that can be used by your
     * plugin.
     **/
    public abstract val pluginConfig: Configuration

    /**
     * A name for your plugin. Will be used to find your plugin in the current application.
     **/
    public val name: String = pluginFactory.name

    /**
     * Allows you to access the environment of the currently running application where the plugin is installed.
     **/
    public val environment: ApplicationEnvironment? get() = pipeline.environment

    /**
     * Configuration of your current application (incl. host, port and anything else you can define in application.conf).
     **/
    public val configuration: ApplicationConfig? get() = environment?.config

    internal val key: AttributeKey<ServerPlugin<Configuration>> = pluginFactory.key

    internal val callInterceptions: MutableList<CallInterception> = mutableListOf()

    internal val onReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()

    internal val onResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()

    internal val afterResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()

    internal val pipelineHandlers: MutableList<PipelineHandler> = mutableListOf()

    internal fun newPhase(): PipelinePhase = PipelinePhase("${name}Phase${Random.nextInt()}")

    private fun <T : Any, ContextT : CallHandlingContext> onDefaultPhaseWithMessage(
        interceptions: MutableList<Interception<T>>,
        phase: PipelinePhase,
        contextInit: (PipelineContext<T, ApplicationCall>) -> ContextT,
        block: suspend ContextT.(ApplicationCall, Any) -> Unit
    ) {
        interceptions.add(
            Interception(
                phase,
                action = { pipeline ->
                    pipeline.intercept(phase) { contextInit(this).block(call, subject) }
                }
            )
        )
    }

    private fun <T : Any, ContextT : CallHandlingContext> onDefaultPhase(
        interceptions: MutableList<Interception<T>>,
        phase: PipelinePhase,
        contextInit: (PipelineContext<T, ApplicationCall>) -> ContextT,
        block: suspend ContextT.(ApplicationCall) -> Unit
    ) {
        onDefaultPhaseWithMessage(interceptions, phase, contextInit) { call, _ -> block(call) }
    }

    /**
     * Callable object that defines how HTTP call handling should be modified by the current [ServerPlugin].
     **/
    public override val onCall: OnCall = object : OnCall {
        private val plugin get() = this@ServerPlugin

        override operator fun invoke(block: suspend CallContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.callInterceptions,
                ApplicationCallPipeline.Plugins,
                ::CallContext
            ) { call ->
                block(call)
            }
        }
    }

    /**
     * Callable object that defines how receiving data from HTTP call should be modified by the current
     * [ServerPlugin].
     **/
    public override val onCallReceive: OnCallReceive = object : OnCallReceive {
        private val plugin = this@ServerPlugin

        override fun invoke(block: suspend CallReceiveContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.onReceiveInterceptions,
                ApplicationReceivePipeline.Transform,
                ::CallReceiveContext,
                block
            )
        }
    }

    /**
     * Callable object that defines how sending data to a client within HTTP call should be modified by the current
     * [ServerPlugin].
     **/
    public override val onCallRespond: OnCallRespond = object : OnCallRespond {
        private val plugin = this@ServerPlugin

        override fun invoke(block: suspend CallRespondContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.onResponseInterceptions,
                ApplicationSendPipeline.Transform,
                ::CallRespondContext,
                block
            )
        }

        override fun afterTransform(block: suspend CallRespondAfterTransformContext.(ApplicationCall, Any) -> Unit) {
            plugin.onDefaultPhaseWithMessage(
                plugin.afterResponseInterceptions,
                ApplicationSendPipeline.After,
                ::CallRespondAfterTransformContext,
                block
            )
        }
    }

    /**
     * Execute some actions right after all [targetPlugins] were already executed.
     *
     * Note: you can define multiple actions inside a [build] callback for multiple stages of handling an HTTP call
     * (such as [onCall], [onCallRespond], etc.) and each of these actions will be executed right after all actions defined
     * by the given [plugin] were already executed in the same stage.
     **/
    public fun afterPlugins(
        vararg targetPlugins: ServerPluginFactory<out Any>,
        build: AfterPluginContext.() -> Unit
    ) {
        pipelineHandlers.add { pipeline ->
            AfterPluginContext(this, targetPlugins.map { pipeline.findServerPlugin(it) }).build()
        }
    }

    /**
     * Execute some actions right before all [targetPlugins] were already executed.
     *
     * Note: you can define multiple actions inside a [build] callback for multiple stages of handling an HTTP call
     * (such as [onCall], [onCallRespond], etc.) and each of these actions will be executed right before all actions defined
     * by the given [targetPlugins] were already executed in the same stage.
     **/
    public fun beforePlugins(
        vararg targetPlugins: ServerPluginFactory<out Any>,
        build: BeforePluginsContext.() -> Unit
    ) {
        pipelineHandlers.add { pipeline ->
            BeforePluginsContext(this, targetPlugins.map { pipeline.findServerPlugin(it) }).build()
        }
    }

    public companion object {
        /**
         * A canonical way to create a [ServerPlugin].
         **/
        public fun <Configuration : Any> createPlugin(
            name: String,
            createConfiguration: (ApplicationCallPipeline) -> Configuration,
            body: ServerPlugin<Configuration>.() -> Unit
        ): ServerPluginFactory<Configuration> = object : ServerPluginFactory<Configuration>(name) {
            override val key: AttributeKey<ServerPlugin<Configuration>> = AttributeKey(name)

            override fun install(
                pipeline: ApplicationCallPipeline,
                configure: Configuration.() -> Unit
            ): ServerPlugin<Configuration> {
                val config = createConfiguration(pipeline)
                config.configure()

                val self = this
                val pluginInstance = object : ServerPlugin<Configuration>(self) {
                    override val pipeline: ApplicationCallPipeline
                        get() = pipeline
                    override val pluginConfig: Configuration
                        get() = config
                }

                pluginInstance.apply(body)

                pluginInstance.pipelineHandlers.forEach { handle ->
                    handle(pipeline)
                }

                pluginInstance.callInterceptions.forEach {
                    it.action(pipeline)
                }

                pluginInstance.onReceiveInterceptions.forEach {
                    it.action(pipeline.receivePipeline)
                }

                pluginInstance.onResponseInterceptions.forEach {
                    it.action(pipeline.sendPipeline)
                }

                pluginInstance.afterResponseInterceptions.forEach {
                    it.action(pipeline.sendPipeline)
                }

                return pluginInstance
            }
        }

        /**
         * A canonical way to create a [ServerPlugin].
         **/
        public fun createPlugin(
            name: String,
            body: ServerPlugin<Unit>.() -> Unit
        ): ServerPluginFactory<Unit> = createPlugin<Unit>(name, {}, body)
    }

    override fun applicationShutdownHook(hook: (Application) -> Unit) {
        environment?.monitor?.subscribe(ApplicationStopped) { app ->
            hook(app)
        }
    }
}
