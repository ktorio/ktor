/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.plugins.api

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*

/**
 * The context associated with the call that is currently being processed by server.
 * Every call handler ([ServerPlugin.onCall], [ServerPlugin.onCallReceive], [ServerPlugin.onCallRespond], and so on)
 * of your plugin has a derivative of [CallHandlingContext] as a receiver.
 **/
public open class CallHandlingContext(private val context: PipelineContext<*, ApplicationCall>) {
    // Internal usage for tests only
    internal fun finish() = context.finish()
}

/**
 * A context associated with the call handlng by your application. [CallContext] is a receiver for [ServerPlugin.onCall] handler
 * of your [ServerPlugin].
 *
 * @see CallHandlingContext
 **/
public class CallContext(internal val context: PipelineContext<Unit, ApplicationCall>) : CallHandlingContext(context)

/**
 * A context associated with the call.receive() action. Allows you to transform the received body.
 * [CallReceiveContext] is a receiverfor [ServerPlugin.onCallReceive] handler of your [ServerPlugin].
 *
 * @see CallHandlingContext
 **/
public class CallReceiveContext(
    private val context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>
) : CallHandlingContext(context) {
    /**
     * Specifies how to transform a request body that is being received from a client.
     **/
    public suspend fun transformRequestBody(transform: suspend (ByteReadChannel) -> Any) {
        val receiveBody = context.subject.value as? ByteReadChannel
            ?: throw noBinaryDataException("ByteReadChannel", context.subject.value)
        context.subject = ApplicationReceiveRequest(
            context.subject.typeInfo,
            transform(receiveBody),
            context.subject.reusableValue
        )
    }
}

/**
 *  A context associated with the call.respond() action. Allows you to transform the response body.
 *  [CallRespondContext] is a receiver for [ServerPlugin.onCallRespond] handler of your [ServerPlugin].
 *
 * @see CallHandlingContext
 **/
public class CallRespondContext(
    private val context: PipelineContext<Any, ApplicationCall>
) : CallHandlingContext(context) {
    /**
     * Specifies how to transform a response body that is being sent to a client.
     **/
    public suspend fun transformResponseBody(transform: suspend (Any) -> Any) {
        context.subject = transform(context.subject)
    }
}

/**
 * A context associated with the onCallRespond.afterTransform {...} handler. Allows you to transform the response binary data.
 * [CallRespondAfterTransformContext] is a receiver for [OnCallRespond.afterTransform] handler of your [ServerPlugin].
 *
 * @see CallHandlingContext
 **/
public class CallRespondAfterTransformContext(
    private val context: PipelineContext<Any, ApplicationCall>
) : CallHandlingContext(context) {
    /**
     * Specifies how to transform the response body already transformed into [OutgoingContent] before sending it to the
     * client.
     *
     * @param transform An action that modifies [OutgoingContent] that needs to be sent to a client.
     **/
    public suspend fun transformResponseBody(transform: suspend (OutgoingContent) -> OutgoingContent) {
        val newContent =
            context.subject as? OutgoingContent ?: throw noBinaryDataException("OutgoingContent", context.subject)
        context.subject = transform(newContent)
    }
}
