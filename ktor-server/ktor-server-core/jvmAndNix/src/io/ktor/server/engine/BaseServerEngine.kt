/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

@Deprecated(message = "Renamed to BaseServerEngine", replaceWith = ReplaceWith("BaseServerEngine"))
public typealias BaseApplicationEngine = BaseServerEngine

/**
 * Base class for implementing [ServerEngine]
 *
 * It creates default engine pipeline, provides [server] property and installs default transformations
 * on respond and receive
 *
 * @param environment instance of [ServerEnvironment] for this engine
 * @param pipeline pipeline to use with this engine
 */
public abstract class BaseServerEngine(
    public final override val environment: ServerEnvironment,
    protected val monitor: Events,
    developmentMode: Boolean,
    public val pipeline: EnginePipeline = defaultEnginePipeline(environment.config, developmentMode)
) : ServerEngine {

    /**
     * Configuration for the [BaseServerEngine].
     */
    public open class Configuration : ServerEngine.Configuration()

    protected val resolvedConnectors: CompletableDeferred<List<EngineConnectorConfig>> = CompletableDeferred()

    init {
        val environment = environment
        val info = StartupInfo()
        val pipeline = pipeline

        BaseServerResponse.setupSendPipeline(pipeline.sendPipeline)

        monitor.subscribe(ServerStarting) {
            if (!info.isFirstLoading) {
                info.initializedStartAt = getTimeMillis()
            }
            it.receivePipeline.merge(pipeline.receivePipeline)
            it.sendPipeline.merge(pipeline.sendPipeline)
            it.receivePipeline.installDefaultTransformations()
            it.sendPipeline.installDefaultTransformations()
            it.installDefaultInterceptors()
            it.installDefaultTransformationChecker()
        }
        monitor.subscribe(ServerStarted) {
            val finishedAt = getTimeMillis()
            val elapsedTimeInSeconds = (finishedAt - info.initializedStartAt) / 1_000.0
            if (info.isFirstLoading) {
                environment.log.info("Application started in $elapsedTimeInSeconds seconds.")
                info.isFirstLoading = false
            } else {
                environment.log.info("Application auto-reloaded in $elapsedTimeInSeconds seconds.")
            }
        }
    }

    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> {
        return resolvedConnectors.await()
    }
}

private suspend fun PipelineContext<Unit, PipelineCall>.verifyHostHeader() {
    val hostHeaders = call.request.headers.getAll(HttpHeaders.Host) ?: return
    if (hostHeaders.size > 1) {
        call.respond(HttpStatusCode.BadRequest)
        finish()
    }
}

private class StartupInfo {
    var isFirstLoading = true
    var initializedStartAt = getTimeMillis()
}

@OptIn(InternalAPI::class)
private fun Server.installDefaultInterceptors() {
    intercept(ServerCallPipeline.Fallback) {
        if (call.isHandled) return@intercept

        val status = call.response.status()
            ?: call.attributes.getOrNull(RoutingFailureStatusCode)
            ?: HttpStatusCode.NotFound

        call.respond(status)
    }

    intercept(ServerCallPipeline.Call) {
        verifyHostHeader()
    }
}

private fun Server.installDefaultTransformationChecker() {
    // Respond with "415 Unsupported Media Type" if content cannot be transformed on receive
    intercept(ServerCallPipeline.Plugins) {
        try {
            proceed()
        } catch (e: CannotTransformContentToTypeException) {
            call.respond(HttpStatusCode.UnsupportedMediaType)
        }
    }

    val checkBodyPhase = PipelinePhase("BodyTransformationCheckPostRender")
    sendPipeline.insertPhaseAfter(ServerSendPipeline.Render, checkBodyPhase)
    sendPipeline.intercept(checkBodyPhase) { subject ->
        if (subject !is OutgoingContent) {
            proceedWith(HttpStatusCodeContent(HttpStatusCode.NotAcceptable))
        }
    }
}
