/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*

/**
 * A [PluginBuilder] that allows you to insert the [currentPlugin] actions before/after [otherPlugins].
 **/
public sealed class RelativePluginBuilder<PluginConfig : Any>(
    private val currentPlugin: ApplicationPluginBuilder<PluginConfig>,
    private val otherPlugins: List<ApplicationPluginBuilder<*>>
) : PluginBuilder<PluginConfig> {
    private fun <T : Any> sortedPhases(
        interceptions: List<Interception<T>>,
        pipeline: Pipeline<*, ApplicationCall>,
        otherPlugin: ApplicationPluginBuilder<*>
    ): List<PipelinePhase> = interceptions
        .map { it.phase }
        .sortedBy {
            if (!pipeline.items.contains(it)) {
                throw MissingApplicationPluginException(otherPlugin.key)
            }

            pipeline.items.indexOf(it)
        }

    /**
     * Specifies how to select a phase from a sorted list of pipeline phases of another plugin.
     * After a phase is selected, it is passed to the [insertPhase] method as [relativePhase].
     **/
    protected abstract fun selectPhase(phases: List<PipelinePhase>): PipelinePhase?

    /**
     * Specifies how to insert a [newPhase] relatively to a [relativePhase] of another plugin.
     **/
    protected abstract fun insertPhase(
        pipeline: Pipeline<*, ApplicationCall>,
        relativePhase: PipelinePhase,
        newPhase: PipelinePhase
    )

    private fun <T : Any, ContextT : CallContext<PluginConfig>> insertToPhaseRelativelyWithMessage(
        currentInterceptions: MutableList<Interception<T>>,
        otherInterceptionsList: List<MutableList<Interception<T>>>,
        contextInit: (pluginConfig: PluginConfig, PipelineContext<T, ApplicationCall>) -> ContextT,
        block: suspend ContextT.(ApplicationCall, T) -> Unit
    ) {
        val currentPhase = currentPlugin.newPhase()

        currentInterceptions.add(
            Interception(
                currentPhase,
                action = { pipeline ->
                    for (i in otherPlugins.indices) {
                        val otherPlugin = otherPlugins[i]
                        val otherInterceptions = otherInterceptionsList[i]

                        val otherPhases = sortedPhases(otherInterceptions, pipeline, otherPlugin)
                        selectPhase(otherPhases)?.let { lastDependentPhase ->
                            insertPhase(pipeline, lastDependentPhase, currentPhase)
                        }
                    }

                    pipeline.intercept(currentPhase) {
                        contextInit(this@RelativePluginBuilder.currentPlugin.pluginConfig, this).block(call, subject)
                    }
                }
            )
        )
    }

    private fun <T : Any, ContextT : CallContext<PluginConfig>> insertToPhaseRelatively(
        currentInterceptions: MutableList<Interception<T>>,
        otherInterceptions: List<MutableList<Interception<T>>>,
        contextInit: (pluginConfig: PluginConfig, PipelineContext<T, ApplicationCall>) -> ContextT,
        block: suspend ContextT.(ApplicationCall, body: T) -> Unit
    ): Unit = insertToPhaseRelativelyWithMessage(currentInterceptions, otherInterceptions, contextInit) { call, body ->
        block(call, body)
    }

    override fun onCall(block: suspend OnCallContext<PluginConfig>.(call: ApplicationCall) -> Unit) {
        val otherInterceptions = otherPlugins.map { it.callInterceptions }
        insertToPhaseRelatively(currentPlugin.callInterceptions, otherInterceptions, ::OnCallContext) { call, _ ->
            block(call)
        }
    }

    override fun onCallReceive(
        block: suspend OnCallReceiveContext<PluginConfig>.(ApplicationCall, body: ApplicationReceiveRequest) -> Unit
    ) {
        val otherInterceptions = otherPlugins.map { it.onReceiveInterceptions }

        insertToPhaseRelatively(
            currentPlugin.onReceiveInterceptions,
            otherInterceptions,
            ::OnCallReceiveContext
        ) { call, body ->
            block(call, body)
        }
    }

    override fun onCallRespond(block: suspend OnCallRespondContext<PluginConfig>.(ApplicationCall, body: Any) -> Unit) {
        val otherInterceptions = otherPlugins.map { it.onResponseInterceptions }
        insertToPhaseRelatively(
            currentPlugin.onResponseInterceptions,
            otherInterceptions,
            ::OnCallRespondContext,
            block
        )
    }

    override val onCallRespond: OnCallRespond<PluginConfig> = object : OnCallRespond<PluginConfig> {
        override fun afterTransform(
            block: suspend OnCallRespondAfterTransformContext<PluginConfig>.(ApplicationCall, OutgoingContent) -> Unit
        ) {
            this@RelativePluginBuilder.insertToPhaseRelativelyWithMessage(
                this@RelativePluginBuilder.currentPlugin.afterResponseInterceptions,
                this@RelativePluginBuilder.otherPlugins.map { it.afterResponseInterceptions },
                ::OnCallRespondAfterTransformContext,
            ) { call, body -> block(call, body as OutgoingContent) }
        }
    }
}
