/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.response

import io.ktor.application.*
import io.ktor.util.pipeline.*

/**
 * Server response send pipeline
 */
public open class ApplicationSendPipeline(
    override val developmentMode: Boolean = false
) : Pipeline<Any, ApplicationCall>(Before, Transform, Render, ContentEncoding, TransferEncoding, After, Engine) {
    /**
     * Send pipeline phases
     */
    @Suppress("PublicApiImplicitType")
    public companion object Phases {
        /**
         * The earliest phase that happens before any other
         */
        public val Before: PipelinePhase = PipelinePhase("Before")

        /**
         * Transformation phase that can proceed with any supported data like String
         */
        public val Transform: PipelinePhase = PipelinePhase("Transform")

        /**
         * Phase to render any current pipeline subject into [io.ktor.http.content.OutgoingContent]
         *
         * Beyond this phase only [io.ktor.http.content.OutgoingContent] should be produced by any interceptor
         */
        public val Render: PipelinePhase = PipelinePhase("Render")

        /**
         * Phase for processing Content-Encoding, like compression and partial content
         */
        public val ContentEncoding: PipelinePhase = PipelinePhase("ContentEncoding")

        /**
         * Phase for handling Transfer-Encoding, like if chunked encoding is being done manually and not by engine
         */
        public val TransferEncoding: PipelinePhase = PipelinePhase("TransferEncoding")

        /**
         * The latest application phase that happens right before engine will send the response
         */
        public val After: PipelinePhase = PipelinePhase("After")

        /**
         * Phase for Engine to send the response out to client.
         */
        public val Engine: PipelinePhase = PipelinePhase("Engine")
    }
}
