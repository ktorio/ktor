/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application.plugins.api

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*

/**
 * A context associated with a call that is currently being processed by server.
 * */
public open class CallHandlingContext(private val context: PipelineContext<*, ApplicationCall>) {
    /**
     * Useful for closing and deallocating resources associated with a call
     * */
    public fun onCallFinished(block: suspend () -> Unit) {
        GlobalScope.launch(context.coroutineContext) {
            block()
        }
    }

    // Internal usage for tests only
    internal fun finish() = context.finish()
}

/**
 * [CallHandlingContext] for the action of processing a HTTP request.
 * */
public class RequestContext(internal val context: PipelineContext<Unit, ApplicationCall>) : CallHandlingContext(context)

/**
 * [CallHandlingContext] for the call.receive() action. Allows transformations of the received body
 * */
public class CallReceiveContext(private val context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>) :
    CallHandlingContext(context) {
    public fun transformReceiveBody(transform: (Any) -> Any) {
        context.subject = ApplicationReceiveRequest(
            context.subject.typeInfo,
            transform(context.subject.value),
            context.subject.reusableValue
        )
    }
}

/**
 * [CallHandlingContext] for the call.respond() action. Allows transformations of the response body
 * */
public class CallRespondContext(private val context: PipelineContext<Any, ApplicationCall>) :
    CallHandlingContext(context) {
    public fun transformReceiveBody(transform: (Any) -> Any) {
        context.subject = transform(context.subject)
    }
}
