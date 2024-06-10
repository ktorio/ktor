/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.util.pipeline.*

/**
 * An [HttpClient]'s pipeline used for executing [HttpRequest].
 */
public class HttpRequestPipeline(
    override val developmentMode: Boolean = false
) : Pipeline<Any, HttpRequestBuilder>(Before, State, Transform, Render, Send) {
    /**
     * All interceptors accept payload as [subject] and try to convert it to [OutgoingContent].
     * Last phase should proceed with [HttpClientCall].
     */
    public companion object Phases {

        /**
         * Called before the request is processed, after the URL and headers are supplied.
         */
        public val Before: PipelinePhase = PipelinePhase("Before")

        /**
         * Use this phase to modify a request with a shared state.
         */
        public val State: PipelinePhase = PipelinePhase("State")

        /**
         * Transform a request body to supported render format.
         */
        public val Transform: PipelinePhase = PipelinePhase("Transform")

        /**
         * Encode a request body to [OutgoingContent].
         */
        public val Render: PipelinePhase = PipelinePhase("Render")

        /**
         * A phase for the [HttpSend] plugin.
         */
        public val Send: PipelinePhase = PipelinePhase("Send")

        private val DoNothing: HttpRequestBuilder.() -> Unit = {}
    }

    /**
     * Applied when a new HttpRequestBuilder is created from the client.
     */
    public var initRequest: HttpRequestBuilder.() -> Unit = DoNothing
        private set

    /**
     * Include a basic function that initializes the request builder.
     * This is executed before anything is supplied to the request.
     */
    public fun onCreate(onCreate: HttpRequestBuilder.() -> Unit) {
        initRequest = if (initRequest === DoNothing) onCreate else {{ initRequest(); onCreate() }}
    }
}

/**
 * An [HttpClient]'s pipeline used for sending [HttpRequest] to a remote server.
 */
public class HttpSendPipeline(
    override val developmentMode: Boolean = false
) : Pipeline<Any, HttpRequestBuilder>(Before, State, Monitoring, Engine, Receive) {

    public companion object Phases {
        /**
         * The earliest phase that happens before any other.
         */
        public val Before: PipelinePhase = PipelinePhase("Before")

        /**
         * Use this phase to modify request with a shared state.
         */
        public val State: PipelinePhase = PipelinePhase("State")

        /**
         * Use this phase for logging and other actions that don't modify a request or shared data.
         */
        public val Monitoring: PipelinePhase = PipelinePhase("Monitoring")

        /**
         * Send a request to a remote server.
         */
        public val Engine: PipelinePhase = PipelinePhase("Engine")

        /**
         * Receive a pipeline execution phase.
         */
        public val Receive: PipelinePhase = PipelinePhase("Receive")
    }
}
