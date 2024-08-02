/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.hooks

import io.ktor.events.EventDefinition
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.reflect.*

/**
 * A hook that is invoked as a first step in processing a call.
 * Useful for validating, updating a call based on proxy information, etc.
 */
public object CallSetup : Hook<suspend (ServerCall) -> Unit> {
    override fun install(pipeline: ServerCallPipeline, handler: suspend (ServerCall) -> Unit) {
        pipeline.intercept(ServerCallPipeline.Setup) {
            handler(call)
        }
    }
}

/**
 * A hook that is invoked when a call fails with an exception.
 */
public object CallFailed : Hook<suspend (call: ServerCall, cause: Throwable) -> Unit> {

    private val phase = PipelinePhase("BeforeSetup")
    override fun install(
        pipeline: ServerCallPipeline,
        handler: suspend (call: ServerCall, cause: Throwable) -> Unit
    ) {
        pipeline.insertPhaseBefore(ServerCallPipeline.Setup, phase)
        pipeline.intercept(phase) {
            try {
                coroutineScope {
                    proceed()
                }
            } catch (cause: Throwable) {
                handler(call, cause)
                if (!call.response.isSent) throw cause
            }
        }
    }
}

/**
 * A shortcut hook for [Server.monitor] subscription.
 */
public class MonitoringEvent<Param : Any, Event : EventDefinition<Param>>(
    private val event: Event
) : Hook<(Param) -> Unit> {
    override fun install(pipeline: ServerCallPipeline, handler: (Param) -> Unit) {
        val server = when (pipeline) {
            is Server -> pipeline
            is Route -> pipeline.server
            else -> error("Unsupported pipeline: $pipeline")
        }
        server.monitor.subscribe(event) {
            handler(it)
        }
    }
}

/**
 * A hook that is invoked before routing and most of the plugins.
 * Useful for metrics, logging, etc.
 *
 * Can be renamed or removed from public API in the future.
 */
@InternalAPI
public object Metrics : Hook<suspend (ServerCall) -> Unit> {
    override fun install(pipeline: ServerCallPipeline, handler: suspend (ServerCall) -> Unit) {
        pipeline.intercept(ServerCallPipeline.Monitoring) {
            handler(call)
        }
    }
}

/**
 * A hook that is invoked when a response body comes through all transformations and is ready to be sent.
 */
public object ResponseBodyReadyForSend :
    Hook<suspend ResponseBodyReadyForSend.Context.(ServerCall, OutgoingContent) -> Unit> {
    public class Context(private val context: PipelineContext<Any, PipelineCall>) {
        public fun transformBodyTo(body: OutgoingContent) {
            context.subject = body
        }
    }

    override fun install(
        pipeline: ServerCallPipeline,
        handler: suspend Context.(ServerCall, OutgoingContent) -> Unit
    ) {
        pipeline.sendPipeline.intercept(ServerSendPipeline.After) {
            handler(Context(this), call, subject as OutgoingContent)
        }
    }
}

/**
 * A hook that is invoked when response was successfully sent to a client.
 * Useful for cleaning up opened resources or finishing measurements.
 */
public object ResponseSent : Hook<(ServerCall) -> Unit> {
    override fun install(pipeline: ServerCallPipeline, handler: (ServerCall) -> Unit) {
        pipeline.sendPipeline.intercept(ServerSendPipeline.Engine) {
            proceed()
            handler(call)
        }
    }
}

/**
 * A hook that is invoked when a request is about to be received. It gives control over the raw request body.
 */
public object ReceiveRequestBytes : Hook<(call: ServerCall, body: ByteReadChannel) -> ByteReadChannel> {
    override fun install(
        pipeline: ServerCallPipeline,
        handler: (call: ServerCall, body: ByteReadChannel) -> ByteReadChannel
    ) {
        pipeline.receivePipeline.intercept(ServerReceivePipeline.Before) { body ->
            if (body !is ByteReadChannel) return@intercept
            val convertedBody = handler(call, body)
            proceedWith(convertedBody)
        }
    }
}

/**
 * A hook that is invoked before `Transform` phase.
 * Useful for some plugins which used for templates as views within application.
 */
@InternalAPI
public class BeforeResponseTransform<T : Any>(private val clazz: KClass<T>) :
    Hook<suspend (call: ServerCall, body: T) -> Any> {
    override fun install(
        pipeline: ServerCallPipeline,
        handler: suspend (call: ServerCall, body: T) -> Any
    ) {
        val beforeTransform = PipelinePhase("BeforeTransform")
        pipeline.sendPipeline.insertPhaseBefore(ServerSendPipeline.Transform, beforeTransform)
        pipeline.sendPipeline.intercept(beforeTransform) { body ->
            if (body.instanceOf(this@BeforeResponseTransform.clazz)) {
                @Suppress("UNCHECKED_CAST")
                subject = handler(call, body as T)
            }
        }
    }
}
