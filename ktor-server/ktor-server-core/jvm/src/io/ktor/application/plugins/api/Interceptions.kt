/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application.plugins.api

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlin.random.*

/**
 * Compatibility class. It describes how (with what new functionality) some particular phase should be intercepted.
 * It is a wrapper over pipeline.intercept(phase) { ... } and is needed to hide old Plugins API functionality
 * */
public class Interception<T : Any>(
    public val phase: PipelinePhase,
    public val action: (Pipeline<T, ApplicationCall>) -> Unit
)

/**
 * Compatibility class. Interception class for Call phase
 * */
public typealias CallInterception = Interception<Unit>

/**
 * Compatibility class. Interception class for Receive phase
 * */
public typealias ReceiveInterception = Interception<ApplicationReceiveRequest>

/**
 * Compatibility class. Interception class for Send phase
 * */
public typealias ResponseInterception = Interception<Any>

/**
 * Compatibility class. Every plugin that needs to be compatible with `beforePlugin(...)` and `afterPlugin(...)`
 * needs to implement this class. It defines a list of interceptions (see [Interception]) for phases in different pipelines
 * that are being intercepted by the current plugin.
 *
 * It is needed in order to get first/last phase in each pipeline for the current plugin and create a new phase
 * that is strictly earlier/later in this pipeline than any interception of the current plugin.
 *
 * Note: any [ApplicationPlugin] instance automatically fills these fields and there is no need to implement them by hand.
 * But if you want to use old plugin API based on pipelines and phases and to make it available for `beforePlugin`/
 * `afterPlugin` methods of the new plugins API, please consider filling the phases your plugin defines by implementing
 * this interface [InterceptionsHolder].
 *
 * Please, use [defineInterceptions] method inside the first init block to simply define all the interceptions for your
 * feature. Also it is useful to always delegate to [DefaultInterceptionsHolder] for simplicity.
 * */
public interface InterceptionsHolder {
    /**
     * A name for the current plugin.
     * */
    public val name: String get() = javaClass.simpleName

    public val key: AttributeKey<out InterceptionsHolder>

    @InternalAPI
    public val fallbackInterceptions: MutableList<CallInterception>

    @InternalAPI
    public val callInterceptions: MutableList<CallInterception>

    @InternalAPI
    public val monitoringInterceptions: MutableList<CallInterception>

    @InternalAPI
    public val beforeReceiveInterceptions: MutableList<ReceiveInterception>

    @InternalAPI
    public val onReceiveInterceptions: MutableList<ReceiveInterception>

    @InternalAPI
    public val beforeResponseInterceptions: MutableList<ResponseInterception>

    @InternalAPI
    public val onResponseInterceptions: MutableList<ResponseInterception>

    @InternalAPI
    public val afterResponseInterceptions: MutableList<ResponseInterception>

    public fun newPhase(): PipelinePhase = PipelinePhase("${name}Phase${Random.nextInt()}")

    public fun defineInterceptions(build: InterceptionsBuilder.() -> Unit) {
        InterceptionsBuilder(this).build()
    }

    /**
     * Builder class that helps to define interceptions for a feature written in old API.
     * */
    public class InterceptionsBuilder(private val holder: InterceptionsHolder) {
        /**
         * Define all phases in [ApplicationCallPipeline] that happen before, after and on [ApplicationCallPipeline.Fallback]
         * */
        public fun fallback(vararg phases: PipelinePhase = arrayOf(ApplicationCallPipeline.Fallback)): Unit =
            addPhases(holder.fallbackInterceptions, *phases)

        /**
         * Define all phases in [ApplicationCallPipeline] that happen before, after and on [ApplicationCallPipeline.Features]
         * */
        public fun call(vararg phases: PipelinePhase = arrayOf(ApplicationCallPipeline.Features)): Unit =
            addPhases(holder.callInterceptions, *phases)

        /**
         * Define all phases in [ApplicationCallPipeline] that happen before, after and on [ApplicationCallPipeline.Monitoring]
         * */
        public fun beforeHandle(vararg phases: PipelinePhase = arrayOf(ApplicationCallPipeline.Monitoring)): Unit =
            addPhases(holder.monitoringInterceptions, *phases)

        /**
         * Define all phases in [ApplicationReceivePipeline] that happen before, after and on [ApplicationReceivePipeline.Before]
         * */
        public fun beforeReceive(vararg phases: PipelinePhase = arrayOf(ApplicationReceivePipeline.Before)): Unit =
            addPhases(holder.beforeReceiveInterceptions, *phases)

        /**
         * Define all phases in [ApplicationReceivePipeline] that happen before, after and on [ApplicationReceivePipeline.Transform]
         * */
        public fun onReceive(vararg phases: PipelinePhase = arrayOf(ApplicationReceivePipeline.Transform)): Unit =
            addPhases(holder.onReceiveInterceptions, *phases)

        /**
         * Define all phases in [ApplicationSendPipeline] that happen before, after and on [ApplicationSendPipeline.Before]
         * */
        public fun beforeResponse(vararg phases: PipelinePhase = arrayOf(ApplicationSendPipeline.Before)): Unit =
            addPhases(holder.beforeResponseInterceptions, *phases)

        /**
         * Define all phases in [ApplicationSendPipeline] that happen before, after and on [ApplicationSendPipeline.Transform]
         * */
        public fun onResponse(vararg phases: PipelinePhase = arrayOf(ApplicationSendPipeline.Transform)): Unit =
            addPhases(holder.onResponseInterceptions, *phases)

        /**
         * Define all phases in [ApplicationSendPipeline] that happen before, after and on [ApplicationSendPipeline.After]
         * */
        public fun afterResponse(vararg phases: PipelinePhase = arrayOf(ApplicationSendPipeline.After)): Unit =
            addPhases(holder.afterResponseInterceptions, *phases)

        private fun <T : Any> addPhases(target: MutableList<Interception<T>>, vararg phases: PipelinePhase) {
            phases.forEach { phase ->
                target.add(Interception(phase) {})
            }
        }
    }
}

/**
 * Empty implementation of [InterceptionsHolder] interface that can be used for simplicity.
 * */
public abstract class DefaultInterceptionsHolder(override val name: String) : InterceptionsHolder {
    override val fallbackInterceptions: MutableList<CallInterception> = mutableListOf()
    override val callInterceptions: MutableList<CallInterception> = mutableListOf()
    override val monitoringInterceptions: MutableList<CallInterception> = mutableListOf()
    override val beforeReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()
    override val onReceiveInterceptions: MutableList<ReceiveInterception> = mutableListOf()
    override val beforeResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val onResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
    override val afterResponseInterceptions: MutableList<ResponseInterception> = mutableListOf()
}
