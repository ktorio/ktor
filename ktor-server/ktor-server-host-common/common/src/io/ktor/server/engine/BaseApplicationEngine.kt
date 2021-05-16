/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*

/**
 * Base class for implementing [ApplicationEngine]
 *
 * It creates default engine pipeline, provides [application] property and installs default transformations
 * on respond and receive
 *
 * @param environment instance of [ApplicationEngineEnvironment] for this engine
 * @param pipeline pipeline to use with this engine
 * @param initEngine if false, [initEngine] function should be called inside engine implementation.
 * Required for K/N as [initEngine] will freeze class
 */
@EngineAPI
public abstract class BaseApplicationEngine(
    public final override val environment: ApplicationEngineEnvironment,
    public val pipeline: EnginePipeline = defaultEnginePipeline(environment),
    initEngine: Boolean = true
) : ApplicationEngine {

    /**
     * Configuration for the [BaseApplicationEngine]
     */
    public open class Configuration : ApplicationEngine.Configuration()

    init {
        if (initEngine) initEngine()
    }

    @EngineAPI
    protected fun initEngine() {
        BaseApplicationResponse.setupSendPipeline(pipeline.sendPipeline)
        environment.monitor.subscribe(ApplicationStarting) {
            it.receivePipeline.merge(pipeline.receivePipeline)
            it.sendPipeline.merge(pipeline.sendPipeline)
            it.receivePipeline.installDefaultTransformations()
            it.sendPipeline.installDefaultTransformations()
            it.installDefaultInterceptors()
        }
        environment.monitor.subscribe(ApplicationStarted) {
            environment.connectors.forEach {
                environment.log.info("Responding at ${it.type.name.toLowerCase()}://${it.host}:${it.port}")
            }
        }
    }

    private fun Application.installDefaultInterceptors() {
        intercept(ApplicationCallPipeline.Fallback) {
            if (call.response.status() == null) {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
