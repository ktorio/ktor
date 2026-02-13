/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.math.absoluteValue

private val JettyCallHandlerCoroutineName = CoroutineName("jetty-call-handler")
private val JettyKtorCounter = AtomicLong()

@OptIn(InternalAPI::class)
internal class JettyKtorHandler(
    private val environment: ApplicationEnvironment,
    private val configuration: JettyApplicationEngineBase.Configuration,
    private val pipeline: EnginePipeline,
    private val applicationProvider: () -> Application
) : Handler.Abstract() {
    private val environmentName = configuration.connectors.joinToString("-") { it.port.toString() }
    private val handlerContext: CoroutineContext =
        DefaultUncaughtExceptionHandler(environment.log) +
            JettyCallHandlerCoroutineName

    // Thread-affinity pool: N single-thread dispatchers, shared across calls.
    private val executors: Array<ExecutorService> =
        Array(configuration.callGroupSize) { i ->
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "ktor-jetty-$environmentName-$i").apply { isDaemon = true }
            }
        }
    private val dispatchers: Array<CoroutineDispatcher> =
        Array(configuration.callGroupSize) { i ->
            executors[i].asCoroutineDispatcher()
        }

    override fun destroy() {
        try {
            super.destroy()
            executors.forEach { it.shutdownNow() }
        } finally {
            handlerContext.cancel()
        }
    }

    override fun handle(
        request: Request,
        response: Response,
        callback: Callback,
    ): Boolean {
        try {
            val application = applicationProvider()

            val threadIndex = JettyKtorCounter.incrementAndGet().hashCode().absoluteValue % dispatchers.size
            val callDispatcher = dispatchers[threadIndex]

            application.launch(handlerContext + callDispatcher) {
                val call = JettyApplicationCall(
                    application,
                    request,
                    response,
                    executor = executors[threadIndex],
                    userContext = currentCoroutineContext(),
                    coroutineContext = currentCoroutineContext(),
                    idleTimeout = configuration.idleTimeout
                )

                try {
                    pipeline.execute(call)
                    callback.succeeded()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    Response.writeError(request, response, callback, HttpStatus.GONE_410, cancelled.message, cancelled)
                } catch (channelFailed: ChannelIOException) {
                    callback.failed(channelFailed)
                } catch (error: Throwable) {
                    logError(call, error)
                    if (!response.isCommitted) {
                        try {
                            Response.writeError(
                                request,
                                response,
                                callback,
                                HttpStatus.INTERNAL_SERVER_ERROR_500,
                                error.message,
                                error
                            )
                        } catch (_: Throwable) {
                            callback.failed(error)
                        }
                    } else {
                        callback.failed(error)
                    }
                }
            }
        } catch (ex: Throwable) {
            environment.log.error("Application cannot fulfill the request", ex)
            Response.writeError(request, response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500, ex.message, ex)
        }

        return true
    }
}
