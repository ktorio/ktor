/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application.plugins.api

import io.ktor.server.application.*

public interface OnCall {
    /**
     * Define how processing an HTTP call should be modified by the current [ApplicationPlugin].
     * */
    public operator fun invoke(block: suspend CallContext.(ApplicationCall) -> Unit): Unit
}

public interface OnCallReceive {
    /**
     * Define how current [ApplicationPlugin] should transform data received from a client.
     * */
    public operator fun invoke(block: suspend CallReceiveContext.(ApplicationCall) -> Unit): Unit
}

public interface OnCallRespond {
    /**
     * Do transformations of the data. Example: you can write a custom serializer using this method.
     * */
    public operator fun invoke(block: suspend CallRespondContext.(ApplicationCall) -> Unit): Unit

    /**
     * Allows to calculate some statistics on the data that was already sent to a client, or to handle errors.
     * (See [Metrics], [CachingHeaders], [StatusPages] features as examples).
     * */
    public fun afterTransform(block: suspend CallRespondAfterTransformContext.(ApplicationCall, Any) -> Unit): Unit
}

public interface PluginContext {
    public val onCall: OnCall
    public val onCallReceive: OnCallReceive
    public val onCallRespond: OnCallRespond

    /**
     * Sets a shutdown hook. This method is useful for closing resources allocated by the feature.
     * */
    public fun applicationShutdownHook(hook: (Application) -> Unit)
}
