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
 * A context associated with a call that is currently being processed by server.
 * */
public open class CallHandlingContext(private val context: PipelineContext<*, ApplicationCall>) {
    // Internal usage for tests only
    internal fun finish() = context.finish()
}

/**
 * [CallHandlingContext] for the action of processing a HTTP request.
 * */
public class CallContext(internal val context: PipelineContext<Unit, ApplicationCall>) : CallHandlingContext(context)

/**
 * [CallHandlingContext] for the call.receive() action. Allows transformations of the received body
 * */
public class CallReceiveContext(private val context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>) :
    CallHandlingContext(context) {
    public fun transformReceiveBody(transform: (ByteReadChannel) -> Any) {
        val receiveBody = context.subject.value as? ByteReadChannel
            ?: throw NoByteReadChannelException(context.subject.value)
        context.subject = ApplicationReceiveRequest(
            context.subject.typeInfo,
            transform(receiveBody),
            context.subject.reusableValue
        )
    }
}

/**
 * [CallHandlingContext] for the call.respond() action. Allows transformations of the response body
 * */
public class CallRespondContext(private val context: PipelineContext<Any, ApplicationCall>) :
    CallHandlingContext(context) {
    public fun transformRespondBody(transform: (Any) -> Any) {
        context.subject = transform(context.subject)
    }
}

/**
 * [CallHandlingContext] for the onCallRespond.afterTransform {...} handler. Allows transformations of the response binary data
 * */
public class CallRespondAfterTransformContext(private val context: PipelineContext<Any, ApplicationCall>) :
    CallHandlingContext(context) {
    public fun transformRespondBody(transform: (OutgoingContent) -> OutgoingContent) {
        val newContent = context.subject as? OutgoingContent ?: throw NoOutgoingContentException(context.subject)
        context.subject = transform(newContent)
    }
}
