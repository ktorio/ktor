/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

/**
 * Pipeline configuration for executing [PipelineCall] instances.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCallPipeline)
 */
@Suppress("PublicApiImplicitType")
public open class ApplicationCallPipeline public constructor(
    final override val developmentMode: Boolean = false,
    public val environment: ApplicationEnvironment
) : Pipeline<Unit, PipelineCall>(
    Setup,
    Monitoring,
    Plugins,
    Call,
    Fallback
) {
    /**
     * Pipeline for receiving content
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCallPipeline.receivePipeline)
     */
    public val receivePipeline: ApplicationReceivePipeline = ApplicationReceivePipeline(developmentMode)

    /**
     * Pipeline for sending content
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCallPipeline.sendPipeline)
     */
    public val sendPipeline: ApplicationSendPipeline = ApplicationSendPipeline(developmentMode)

    /**
     * Standard phases for application call pipelines
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase)
     */
    public companion object ApplicationPhase {
        /**
         * Phase for preparing call and it's attributes for processing
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Setup)
         */
        public val Setup: PipelinePhase = PipelinePhase("Setup")

        /**
         * Phase for tracing calls, useful for logging, metrics, error handling and so on
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Monitoring)
         */
        public val Monitoring: PipelinePhase = PipelinePhase("Monitoring")

        /**
         * Phase for plugins. Most plugins should intercept this phase.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins)
         */
        public val Plugins: PipelinePhase = PipelinePhase("Plugins")

        /**
         * Phase for processing a call and sending a response
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Call)
         */
        public val Call: PipelinePhase = PipelinePhase("Call")

        /**
         * Phase for handling unprocessed calls
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Fallback)
         */
        public val Fallback: PipelinePhase = PipelinePhase("Fallback")

        /**
         * Phase for plugins. Most plugins should intercept this phase.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Features)
         */
        @Deprecated(
            "Renamed to Plugins",
            replaceWith = ReplaceWith("Plugins"),
            level = DeprecationLevel.ERROR
        )
        public val Features: PipelinePhase = Plugins
    }
}

/**
 * Current call for the context
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.call)
 */
public inline val PipelineContext<*, PipelineCall>.call: PipelineCall get() = context

/**
 * Current application for the context
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.application)
 */
public val PipelineContext<*, PipelineCall>.application: Application get() = call.application
