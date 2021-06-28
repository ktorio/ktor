/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application.plugins.api

import io.ktor.application.*

public interface OnRequest {
    /**
     * Define how processing an HTTP call should be modified by the current [ApplicationPlugin].
     * */
    public operator fun invoke(block: suspend RequestContext.(ApplicationCall) -> Unit): Unit

    /**
     * Defines actions to perform before the call was processed by any feature (including [Routing]).
     * It is useful for monitoring and logging (see [CallLogging] feature) to be executed before any "real stuff"
     * was performed with the call because other features can change it's content as well as add more resource usage etc.
     * while for logging and monitoring it is important to observe the pure (untouched) data.
     * */
    public fun beforeHandle(block: suspend RequestContext.(ApplicationCall) -> Unit): Unit

    /**
     * Defines what to do with the call in case request was not handled for some reason.
     * Usually, it is handy to use fallback { call -> ...} to set the response with some message or status code,
     * or to throw an exception.
     * */
    public fun fallback(block: suspend RequestContext.(ApplicationCall) -> Unit): Unit

    /**
     * Special handler that is executed only in case of exceptions in HTTP pipeline while handling a call.
     * */
    public fun handleException(block: suspend RequestContext.(Throwable) -> Unit): Unit
}

public interface OnReceive {
    /**
     * Define how current [ApplicationPlugin] should transform data received from a client.
     * */
    public operator fun invoke(block: suspend CallReceiveContext.(ApplicationCall) -> Unit): Unit

    /**
     * Defines actions to perform before any transformations were made to the received content. It is useful for caching.
     * */
    public fun beforeTransform(block: suspend CallReceiveContext.(ApplicationCall) -> Unit): Unit
}

public interface OnRespond {
    /**
     * Do transformations of the data. Example: you can write a custom serializer using this method.
     * (Note: it is handy to also use [Execution.proceedWith] for this scenario)
     * */
    public operator fun invoke(block: suspend CallRespondContext.(ApplicationCall) -> Unit): Unit

    /**
     * Allows to use the direct result of call processing (see [OnRequest.invoke]) and prepare data before sending a response will be executed.
     * */
    public fun beforeTransform(block: suspend CallRespondContext.(ApplicationCall) -> Unit): Unit

    /**
     * Allows to calculate some statistics on the data that was already sent to a client, or to handle errors.
     * (See [Metrics], [CachingHeaders], [StatusPages] features as examples).
     * */
    public fun afterTransform(block: suspend CallRespondContext.(ApplicationCall) -> Unit): Unit
}

public interface PluginContext {
    public val onRequest: OnRequest
    public val onCallReceive: OnReceive
    public val onCallRespond: OnRespond
}
