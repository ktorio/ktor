/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application.plugins.api

import io.ktor.application.*
import io.ktor.config.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import java.lang.module.*

/**
 * Gets plugin instance for this pipeline, or fails with [MissingApplicationFeatureException] if the feature is not installed
 * @throws MissingApplicationFeatureException
 * @param plugin plugin to lookup
 * @return an instance of plugin
 */
public fun <A : Pipeline<*, ApplicationCall>> A.plugin(
    plugin: ApplicationInstallablePlugin<Configuration>
): ApplicationPlugin<Configuration> {
    return attributes[featureRegistryKey].getOrNull(plugin.key)
        ?: throw MissingApplicationFeatureException(plugin.key)
}

internal fun <A : Pipeline<*, ApplicationCall>> A.findInterceptionsHolder(
    plugin: ApplicationInstallablePlugin<*>
): InterceptionsHolder {
    return attributes[featureRegistryKey].getOrNull(plugin.key)
        ?: throw MissingApplicationFeatureException(plugin.key)
}

public abstract class ApplicationInstallablePlugin<Configuration : Any>(public val name: String) :
    ApplicationFeature<ApplicationCallPipeline, Configuration, ApplicationPlugin<Configuration>>

internal typealias PipelineHandler = (Pipeline<*, ApplicationCall>) -> Unit
internal typealias ExceptionHandler = suspend RequestContext.(Throwable) -> Unit

/**
 * A plugin for Ktor that embeds into the HTTP pipeline and extends functionality of Ktor framework.
 * */
public abstract class ApplicationPlugin<Configuration : Any> private constructor(
    installablePlugin: ApplicationInstallablePlugin<Configuration>
) : PluginContext,
    InterceptionsHolder {
    protected abstract val pipeline: ApplicationCallPipeline

    public abstract val pluginConfig: Configuration

    public override val name: String = installablePlugin.name

    public val environment: ApplicationEnvironment? get() = pipeline.environment
    public val configuration: ApplicationConfig? get() = environment?.config

    override val key: AttributeKey<ApplicationPlugin<Configuration>> = installablePlugin.key

    override val fallbackInterceptions: MutableList<CallInterception> = mutableListOf()
    override val callInterceptions: MutableList<CallInterception> = mutableListOf()
    override val monitoringInterceptions: MutableList<CallInterception> = mutableListOf()

    override val beforeReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()
    override val onReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()

    override val beforeResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val onResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val afterResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()

    internal val pipelineHandlers: MutableList<PipelineHandler> = mutableListOf()
    private val exceptionHandlers: MutableList<ExceptionHandler> = mutableListOf()

    private fun <T : Any, ContextT : CallHandlingContext> onDefaultPhase(
        interceptions: MutableList<Interception<T>>,
        phase: PipelinePhase,
        contextInit: (PipelineContext<T, ApplicationCall>) -> ContextT,
        block: suspend ContextT.(ApplicationCall) -> Unit
    ) {
        interceptions.add(
            Interception(
                phase,
                action = { pipeline ->
                    pipeline.intercept(phase) { contextInit(this).block(call) }
                }
            )
        )
    }

    /**
     * Callable object that defines how HTTP call handling should be modified by the current [ApplicationPlugin].
     * */
    public override val onRequest: OnRequest = object : OnRequest {
        private val plugin = this@ApplicationPlugin

        override operator fun invoke(block: suspend RequestContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.callInterceptions,
                ApplicationCallPipeline.Features,
                ::RequestContext
            ) { call ->
                try {
                    block(call)
                    context.proceed()
                } catch (e: Throwable) {
                    exceptionHandlers.forEach { it(e) }
                }
            }
        }

        override fun beforeHandle(block: suspend RequestContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.monitoringInterceptions,
                ApplicationCallPipeline.Monitoring,
                ::RequestContext,
                block
            )
        }

        override fun fallback(block: suspend RequestContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.fallbackInterceptions,
                ApplicationCallPipeline.Fallback,
                ::RequestContext,
                block
            )
        }

        override fun handleException(block: ExceptionHandler) {
            exceptionHandlers.add(block)
        }
    }

    /**
     * Callable object that defines how receiving data from HTTP call should be modified by the current [ApplicationPlugin].
     * */
    public override val onCallReceive: OnReceive = object : OnReceive {
        private val plugin = this@ApplicationPlugin

        override fun invoke(block: suspend CallReceiveContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.onReceiveInterceptions,
                ApplicationReceivePipeline.Transform,
                ::CallReceiveContext,
                block
            )
        }

        override fun beforeTransform(block: suspend CallReceiveContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.beforeReceiveInterceptions,
                ApplicationReceivePipeline.Before,
                ::CallReceiveContext,
                block
            )
        }
    }

    /**
     * Callable object that defines how sending data to a client within HTTP call should be modified by the current [ApplicationPlugin].
     * */
    public override val onCallRespond: OnRespond = object : OnRespond {
        private val plugin = this@ApplicationPlugin

        override fun invoke(block: suspend CallRespondContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.onResponseInterceptions,
                ApplicationSendPipeline.Transform,
                ::CallRespondContext,
                block
            )
        }

        override fun beforeTransform(block: suspend CallRespondContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.beforeResponseInterceptions,
                ApplicationSendPipeline.Before,
                ::CallRespondContext,
                block
            )
        }

        override fun afterTransform(block: suspend CallRespondContext.(ApplicationCall) -> Unit) {
            plugin.onDefaultPhase(
                plugin.afterResponseInterceptions,
                ApplicationSendPipeline.After,
                ::CallRespondContext,
                block
            )
        }
    }

    public abstract class RelativePluginContext(
        private val currentPlugin: ApplicationPlugin<*>,
        private val otherPlugin: InterceptionsHolder
    ) : PluginContext {
        protected fun <T : Any> sortedPhases(
            interceptions: List<Interception<T>>,
            pipeline: Pipeline<*, ApplicationCall>
        ): List<PipelinePhase> =
            interceptions
                .map { it.phase }
                .sortedBy {
                    if (!pipeline.items.contains(it)) {
                        throw PluginNotInstalledException(otherPlugin.name)
                    }

                    pipeline.items.indexOf(it)
                }

        public abstract fun selectPhase(phases: List<PipelinePhase>): PipelinePhase?

        public abstract fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        )

        private fun <T : Any, ContextT : CallHandlingContext> insertToPhaseRelatively(
            currentInterceptions: MutableList<Interception<T>>,
            otherInterceptions: MutableList<Interception<T>>,
            contextInit: (PipelineContext<T, ApplicationCall>) -> ContextT,
            block: suspend ContextT.(ApplicationCall) -> Unit
        ) {
            val currentPhase = currentPlugin.newPhase()

            currentInterceptions.add(
                Interception(
                    currentPhase,
                    action = { pipeline ->
                        val otherPhases = sortedPhases(otherInterceptions, pipeline)
                        selectPhase(otherPhases)?.let { lastDependentPhase ->
                            insertPhase(pipeline, lastDependentPhase, currentPhase)
                        }
                        pipeline.intercept(currentPhase) {
                            contextInit(this).block(call)
                        }
                    }
                )
            )
        }

        override val onRequest: OnRequest = object : OnRequest {
            override operator fun invoke(block: suspend RequestContext.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(
                    currentPlugin.callInterceptions,
                    otherPlugin.callInterceptions,
                    ::RequestContext,
                    block
                )
            }

            override fun beforeHandle(block: suspend RequestContext.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(
                    currentPlugin.monitoringInterceptions,
                    otherPlugin.monitoringInterceptions,
                    ::RequestContext,
                    block
                )
            }

            override fun fallback(block: suspend RequestContext.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(
                    currentPlugin.fallbackInterceptions,
                    otherPlugin.fallbackInterceptions,
                    ::RequestContext,
                    block
                )
            }

            override fun handleException(block: ExceptionHandler) {
                currentPlugin.exceptionHandlers.add(block)
            }
        }

        override val onCallReceive: OnReceive = object : OnReceive {
            override operator fun invoke(block: suspend CallReceiveContext.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(
                    currentPlugin.onReceiveInterceptions,
                    otherPlugin.onReceiveInterceptions,
                    ::CallReceiveContext,
                    block
                )
            }

            override fun beforeTransform(block: suspend CallReceiveContext.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(
                    currentPlugin.beforeReceiveInterceptions,
                    otherPlugin.beforeReceiveInterceptions,
                    ::CallReceiveContext,
                    block
                )
            }
        }

        override val onCallRespond: OnRespond = object : OnRespond {
            override operator fun invoke(block: suspend CallRespondContext.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(
                    currentPlugin.onResponseInterceptions,
                    otherPlugin.onResponseInterceptions,
                    ::CallRespondContext,
                    block
                )
            }

            override fun beforeTransform(block: suspend CallRespondContext.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(
                    currentPlugin.beforeResponseInterceptions,
                    otherPlugin.beforeResponseInterceptions,
                    ::CallRespondContext,
                    block
                )
            }

            override fun afterTransform(block: suspend CallRespondContext.(ApplicationCall) -> Unit) {
                insertToPhaseRelatively(
                    currentPlugin.afterResponseInterceptions,
                    otherPlugin.afterResponseInterceptions,
                    ::CallRespondContext,
                    block
                )
            }
        }
    }

    public class AfterPluginContext(currentPlugin: ApplicationPlugin<*>, otherPlugin: InterceptionsHolder) :
        RelativePluginContext(currentPlugin, otherPlugin) {
        override fun selectPhase(phases: List<PipelinePhase>): PipelinePhase? = phases.lastOrNull()

        override fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        ) {
            pipeline.insertPhaseAfter(relativePhase, newPhase)
        }
    }

    public class BeforePluginContext(currentPlugin: ApplicationPlugin<*>, otherPlugin: InterceptionsHolder) :
        RelativePluginContext(currentPlugin, otherPlugin) {
        override fun selectPhase(phases: List<PipelinePhase>): PipelinePhase? = phases.firstOrNull()

        override fun insertPhase(
            pipeline: Pipeline<*, ApplicationCall>,
            relativePhase: PipelinePhase,
            newPhase: PipelinePhase
        ) {
            pipeline.insertPhaseBefore(relativePhase, newPhase)
        }
    }

    /**
     * Execute some actions right after some other [plugin] was already executed.
     *
     * Note: you can define multiple actions inside a [build] callback for multiple stages of handling an HTTP call
     * (such as [onRequest], [onCallRespond], etc.) and each of these actions will be executed right after all actions defined
     * by the given [plugin] were already executed in the same stage.
     * */
    public fun afterPlugin(
        installablePlugin: ApplicationInstallablePlugin<out Any>,
        build: AfterPluginContext.() -> Unit
    ) {
        pipelineHandlers.add { pipeline ->
            AfterPluginContext(this, pipeline.findInterceptionsHolder(installablePlugin)).build()
        }
    }

    /**
     * Execute some actions right before some other [installablePlugin] was already executed.
     *
     * Note: you can define multiple actions inside a [build] callback for multiple stages of handling an HTTP call
     * (such as [onRequest], [onCallRespond], etc.) and each of these actions will be executed right before all actions defined
     * by the given [installablePlugin] were already executed in the same stage.
     * */
    public fun beforePlugin(
        installablePlugin: ApplicationInstallablePlugin<out Any>,
        build: BeforePluginContext.() -> Unit
    ) {
        pipelineHandlers.add { pipeline ->
            BeforePluginContext(this, pipeline.findInterceptionsHolder(installablePlugin)).build()
        }
    }

    public companion object {
        /**
         * A canonical way to create a [ApplicationPlugin].
         * */
        public fun <Configuration : Any> createPlugin(
            name: String,
            createConfiguration: (ApplicationCallPipeline) -> Configuration,
            body: ApplicationPlugin<Configuration>.() -> Unit
        ): ApplicationInstallablePlugin<Configuration> = object : ApplicationInstallablePlugin<Configuration>(name) {
            override val key: AttributeKey<ApplicationPlugin<Configuration>> = AttributeKey(name)

            override fun install(
                pipeline: ApplicationCallPipeline,
                configure: Configuration.() -> Unit
            ): ApplicationPlugin<Configuration> {
                val config = createConfiguration(pipeline)
                config.configure()

                val self = this
                val pluginInstance = object : ApplicationPlugin<Configuration>(self) {
                    override val pipeline: ApplicationCallPipeline
                        get() = pipeline
                    override val pluginConfig: Configuration
                        get() = config
                }

                pluginInstance.apply(body)

                pluginInstance.pipelineHandlers.forEach { handle ->
                    handle(pipeline)
                }

                pluginInstance.fallbackInterceptions.forEach {
                    it.action(pipeline)
                }

                pluginInstance.callInterceptions.forEach {
                    it.action(pipeline)
                }

                pluginInstance.monitoringInterceptions.forEach {
                    it.action(pipeline)
                }

                pluginInstance.beforeReceiveInterceptions.forEach {
                    it.action(pipeline.receivePipeline)
                }

                pluginInstance.onReceiveInterceptions.forEach {
                    it.action(pipeline.receivePipeline)
                }

                pluginInstance.beforeResponseInterceptions.forEach {
                    it.action(pipeline.sendPipeline)
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
         * A canonical way to create a [ApplicationPlugin].
         * */
        public fun createPlugin(
            name: String,
            body: ApplicationPlugin<Unit>.() -> Unit
        ): ApplicationInstallablePlugin<Unit> = createPlugin<Unit>(name, {}, body)
    }

    /**
     * Sets a shutdown hook. This method is useful for closing resources allocated by the feature.
     * */
    public fun applicationShutdownHook(hook: (Application) -> Unit) {
        environment?.monitor?.subscribe(ApplicationStopped) { app ->
            hook(app)
        }
    }
}

/**
 * Port of the current application. Same as in config.
 * */
public val ApplicationConfig.port: Int get() = propertyOrNull("ktor.deployment.port")?.getString()?.toInt() ?: 8080

/**
 * Host of the current application. Same as in config.
 * */
public val ApplicationConfig.host: String get() = propertyOrNull("ktor.deployment.host")?.getString() ?: "0.0.0.0"
