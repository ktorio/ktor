/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.plugins.api

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * The context associated with the call that is currently being processed by server.
 * Every call handler ([PluginBuilder.onCall], [PluginBuilder.onCallReceive], [PluginBuilder.onCallRespond], and so on)
 * of your plugin has a derivative of [CallHandlingContext] as a receiver.
 **/
public open class CallHandlingContext(internal open val context: PipelineContext<*, ApplicationCall>) {
    // Internal usage for tests only
    internal fun finish() = context.finish()
}

/**
 * A context associated with the call handlng by your application. [CallContext] is a receiver for [PluginBuilder.onCall] handler
 * of your [PluginBuilder].
 *
 * @see CallHandlingContext
 **/
public class CallContext(override val context: PipelineContext<Unit, ApplicationCall>) :
    CallHandlingContext(context)

/**
 * Contains type information about the current request or response body when performing a transformation.
 * */
public class TransformContext(public val requestedType: TypeInfo?)

/**
 * A context associated with the call.receive() action. Allows you to transform the received body.
 * [CallReceiveContext] is a receiver for [PluginBuilder.onCallReceive] handler of your [PluginBuilder].
 *
 * @see CallHandlingContext
 **/
public class CallReceiveContext(
    override val context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>
) : CallHandlingContext(context) {
    /**
     * Specifies how to transform a request body that is being received from a client.
     * If another plugin has already made the transformation, then your [transformBody] handler is not executed.
     **/
    public suspend fun transformBody(transform: suspend TransformContext.(body: ByteReadChannel) -> Any) {
        val receiveBody = context.subject.value as? ByteReadChannel ?: return
        val typeInfo = context.subject.typeInfo
        if (typeInfo.type == ByteReadChannel::class) return

        val transformContext = TransformContext(typeInfo)

        context.subject = ApplicationReceiveRequest(
            context.subject.typeInfo,
            transformContext.transform(receiveBody),
            context.subject.reusableValue
        )
    }
}

/**
 *  A context associated with the call.respond() action. Allows you to transform the response body.
 *  [CallRespondContext] is a receiver for [PluginBuilder.onCallRespond] handler of your [PluginBuilder].
 *
 * @see CallHandlingContext
 **/
public class CallRespondContext(
    override val context: PipelineContext<Any, ApplicationCall>
) : CallHandlingContext(context) {
    /**
     * Specifies how to transform a response body that is being sent to a client.
     **/
    public suspend fun transformBody(transform: suspend TransformContext.(body: Any) -> Any) {
        val transformContext = TransformContext(context.call.response.responseType)

        context.subject = transformContext.transform(context.subject)
    }
}

/**
 * A context associated with the onCallRespond.afterTransform {...} handler. Allows you to transform the response binary data.
 * [CallRespondAfterTransformContext] is a receiver for [OnCallRespond.afterTransform] handler of your [PluginBuilder].
 *
 * @see CallHandlingContext
 **/
public class CallRespondAfterTransformContext(
    override val context: PipelineContext<Any, ApplicationCall>
) : CallHandlingContext(context) {
    /**
     * Specifies how to transform the response body already transformed into [OutgoingContent] before sending it to the
     * client.
     *
     * @param transform An action that modifies [OutgoingContent] that needs to be sent to a client.
     **/
    public suspend fun transformBody(transform: suspend TransformContext.(body: OutgoingContent) -> OutgoingContent) {
        val transformContext = TransformContext(context.call.response.responseType)

        val newContent =
            context.subject as? OutgoingContent ?: throw noBinaryDataException("OutgoingContent", context.subject)
        context.subject = transformContext.transform(newContent)
    }
}
