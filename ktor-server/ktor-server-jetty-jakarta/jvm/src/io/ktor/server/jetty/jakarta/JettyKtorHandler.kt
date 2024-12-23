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
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong

private val JettyCallHandlerCoroutineName = CoroutineName("jetty-call-handler")
private val JettyKtorCounter = AtomicLong()
private const val THREAD_KEEP_ALIVE_TIME = 1L

@OptIn(InternalAPI::class)
internal class JettyKtorHandler(
    private val environment: ApplicationEnvironment,
    private val configuration: JettyApplicationEngineBase.Configuration,
    private val pipeline: EnginePipeline,
    private val applicationProvider: () -> Application
) : Handler.Abstract() {
    private val environmentName = configuration.connectors.joinToString("-") { it.port.toString() }
    private val queue: BlockingQueue<Runnable> = LinkedBlockingQueue()
    private val executor = ThreadPoolExecutor(
        configuration.callGroupSize,
        configuration.callGroupSize * 8,
        THREAD_KEEP_ALIVE_TIME,
        TimeUnit.MINUTES,
        queue
    ) { r ->
        Thread(r, "ktor-jetty-$environmentName-${JettyKtorCounter.incrementAndGet()}")
    }
    private val dispatcher = executor.asCoroutineDispatcher()

    private val handlerJob = SupervisorJob(applicationProvider().parentCoroutineContext[Job])
    private val coroutineScope: CoroutineScope get() =
        applicationProvider() + handlerJob + DefaultUncaughtExceptionHandler(environment.log)

    override fun destroy() {
        try {
            super.destroy()
            executor.shutdownNow()
        } finally {
            handlerJob.cancel()
        }
    }

    override fun handle(
        request: Request,
        response: Response,
        callback: Callback,
    ): Boolean {
        try {
            coroutineScope.launch(dispatcher + JettyCallHandlerCoroutineName) {
                val call = JettyApplicationCall(
                    applicationProvider(),
                    request,
                    response,
                    executor = executor,
                    userContext = dispatcher,
                    coroutineContext = this@launch.coroutineContext,
                    idleTimeout = configuration.idleTimeout
                )

                try {
                    pipeline.execute(call)
                    callback.succeeded()
                } catch (cancelled: CancellationException) {
                    Response.writeError(request, response, callback, HttpStatus.GONE_410, cancelled.message, cancelled)
                    callback.failed(cancelled)
                } catch (channelFailed: ChannelIOException) {
                    callback.failed(channelFailed)
                } catch (error: Throwable) {
                    try {
                        logError(call, error)
                        if (!response.isCommitted) {
                            Response.writeError(
                                request,
                                response,
                                callback,
                                HttpStatus.INTERNAL_SERVER_ERROR_500,
                                error.message,
                                error
                            )
                        }
                    } finally {
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
