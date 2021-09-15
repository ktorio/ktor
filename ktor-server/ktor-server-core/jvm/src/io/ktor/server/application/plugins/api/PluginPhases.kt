/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.plugins.api

import io.ktor.server.application.*

/**
 * Descendents of [OnCall] allow you to extend handling of an application call.
 *
 * Example:
 * ```
 * onCall { call ->
 *      println(call.request.uri)
 * }
 * ```
 *
 * This will print you a URL for each call to your application.
 **/
public interface OnCall {
    /**
     * Defines how processing an HTTP call needs to be modified by the current [ServerPlugin].
     *
     * @param block An action that needs to be executed when your application receives an HTTP call.
     **/
    public operator fun invoke(block: suspend CallContext.(ApplicationCall) -> Unit): Unit
}

/**
 * Descendents of [OnCallReceive] allow you to extend the process of receiving data from a client.
 *
 * Example:
 * ```
 * onCallReceive { call ->
 *      println(call.request.uri)
 * }
 * ```
 *
 * This will print you a URL once you execute call.receive() in your server.
 **/
public interface OnCallReceive {
    /**
     * Defines how current [ServerPlugin] needs to transform data received from a client.
     *
     * @param block An action that needs to be executed when your server receives data from a client.
     **/
    public operator fun invoke(block: suspend CallReceiveContext.(ApplicationCall) -> Unit): Unit
}

/**
 * Descendents of [OnCallReceive] allow you to extend the process of sending a response to the client.
 *
 * Example:
 *
 * ```
 * onCallRespond { call ->
 *      println(call.request.uri)
 * }
 *
 * onCallRespond.afterTransform { call, content ->
 *      println("sending $content to a client")
 * }
 * ```
 *
 * This will print you a URL once you execute call.respond() in your server and also print you a raw content that is going
 * to be sent to the client..
 **/
public interface OnCallRespond {
    /**
     * Specifies how to transform the data. For example, you can write a custom serializer using this method.
     *
     * @param block An action that needs to be executed when your server is sending a response to a client.
     **/
    public operator fun invoke(block: suspend CallRespondContext.(ApplicationCall) -> Unit): Unit

    /**
     * Allows you to execute your code after response transformation has been made.
     * @see StatusPages
     *
     * @param block An action that needs to be executed after transformation of the response body.
     **/
    public fun afterTransform(block: suspend CallRespondAfterTransformContext.(ApplicationCall, Any) -> Unit): Unit
}

/**
 * A context that is available inside a plugin creation block. It allows you to define handlers for different stages
 * (a.k.a. phases) of the HTTP pipeline.
 **/
public interface PluginContext {
    public val onCall: OnCall
    public val onCallReceive: OnCallReceive
    public val onCallRespond: OnCallRespond

    /**
     * Specifies a shutdown hook. This method is useful for closing resources allocated by the plugin.
     *
     * @param hook An action that needs to be executed when the application shuts down.
     **/
    public fun applicationShutdownHook(hook: (Application) -> Unit)
}
