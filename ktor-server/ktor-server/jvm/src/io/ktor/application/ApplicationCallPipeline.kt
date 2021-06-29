/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.application

import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.pipeline.*

/**
 * Pipeline configuration for executing [ApplicationCall] instances
 */
@Suppress("PublicApiImplicitType")
public open class ApplicationCallPipeline(
    final override val developmentMode: Boolean = false
) : Pipeline<Unit, ApplicationCall>(
    Setup,
    Monitoring,
    Features,
    Call,
    Fallback
) {
    /**
     * Pipeline for receiving content
     */
    public val receivePipeline: ApplicationReceivePipeline = ApplicationReceivePipeline(developmentMode)

    /**
     * Pipeline for sending content
     */
    public val sendPipeline: ApplicationSendPipeline = ApplicationSendPipeline(developmentMode)

    /**
     * Standard phases for application call pipelines
     */
    public companion object ApplicationPhase {
        /**
         * Phase for preparing call and it's attributes for processing
         */
        public val Setup: PipelinePhase = PipelinePhase("Setup")

        /**
         * Phase for tracing calls, useful for logging, metrics, error handling and so on
         */
        public val Monitoring: PipelinePhase = PipelinePhase("Monitoring")

        /**
         * Phase for features. Most features should intercept this phase.
         */
        public val Features: PipelinePhase = PipelinePhase("Features")

        /**
         * Phase for processing a call and sending a response
         */
        public val Call: PipelinePhase = PipelinePhase("Call")

        /**
         * Phase for handling unprocessed calls
         */
        public val Fallback: PipelinePhase = PipelinePhase("Fallback")
    }
}

/**
 * Current call for the context
 */
public inline val PipelineContext<*, ApplicationCall>.call: ApplicationCall get() = context

/**
 * Current application for the context
 */
public val PipelineContext<*, ApplicationCall>.application: Application get() = call.application
