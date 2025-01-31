/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.util.pipeline.*

/**
 * An [HttpClient]'s pipeline used for executing [HttpRequest].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpRequestPipeline)
 */
public class HttpRequestPipeline(
    override val developmentMode: Boolean = true
) : Pipeline<Any, HttpRequestBuilder>(Before, State, Transform, Render, Send) {
    /**
     * All interceptors accept payload as [subject] and try to convert it to [OutgoingContent].
     * Last phase should proceed with [HttpClientCall].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpRequestPipeline.Phases)
     */
    public companion object Phases {
        /**
         * The earliest phase that happens before any other.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpRequestPipeline.Phases.Before)
         */
        public val Before: PipelinePhase = PipelinePhase("Before")

        /**
         * Use this phase to modify a request with a shared state.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpRequestPipeline.Phases.State)
         */
        public val State: PipelinePhase = PipelinePhase("State")

        /**
         * Transform a request body to supported render format.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpRequestPipeline.Phases.Transform)
         */
        public val Transform: PipelinePhase = PipelinePhase("Transform")

        /**
         * Encode a request body to [OutgoingContent].
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpRequestPipeline.Phases.Render)
         */
        public val Render: PipelinePhase = PipelinePhase("Render")

        /**
         * A phase for the [HttpSend] plugin.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpRequestPipeline.Phases.Send)
         */
        public val Send: PipelinePhase = PipelinePhase("Send")
    }
}

/**
 * An [HttpClient]'s pipeline used for sending [HttpRequest] to a remote server.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpSendPipeline)
 */
public class HttpSendPipeline(
    override val developmentMode: Boolean = true
) : Pipeline<Any, HttpRequestBuilder>(Before, State, Monitoring, Engine, Receive) {

    public companion object Phases {
        /**
         * The earliest phase that happens before any other.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpSendPipeline.Phases.Before)
         */
        public val Before: PipelinePhase = PipelinePhase("Before")

        /**
         * Use this phase to modify request with a shared state.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpSendPipeline.Phases.State)
         */
        public val State: PipelinePhase = PipelinePhase("State")

        /**
         * Use this phase for logging and other actions that don't modify a request or shared data.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpSendPipeline.Phases.Monitoring)
         */
        public val Monitoring: PipelinePhase = PipelinePhase("Monitoring")

        /**
         * Send a request to a remote server.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpSendPipeline.Phases.Engine)
         */
        public val Engine: PipelinePhase = PipelinePhase("Engine")

        /**
         * Receive a pipeline execution phase.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.HttpSendPipeline.Phases.Receive)
         */
        public val Receive: PipelinePhase = PipelinePhase("Receive")
    }
}
