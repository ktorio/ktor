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
    private val queue: BlockingQueue<Runnable> = SynchronousQueue()
    private val rejectedExecutionHandler =
        // Always run on caller thread when pool is full or during shutdown.
        // Unlike `ThreadPoolExecutor.CallerRunsPolicy` which discards tasks during shutdown,
        // this ensures cancellation can propagate through the dispatcher.
        RejectedExecutionHandler { r, _ -> r.run() }
    private val executor = ThreadPoolExecutor(
        configuration.callGroupSize,
        configuration.callGroupSize * 8,
        THREAD_KEEP_ALIVE_TIME,
        TimeUnit.MINUTES,
        queue,
        { r -> Thread(r, "ktor-jetty-$environmentName-${JettyKtorCounter.incrementAndGet()}") },
        rejectedExecutionHandler
    )
    private val dispatcher = executor.asCoroutineDispatcher()
    private val handlerContext: CoroutineContext = dispatcher +
        DefaultUncaughtExceptionHandler(environment.log) +
        JettyCallHandlerCoroutineName

    override fun destroy() {
        try {
            super.destroy()
            executor.shutdownNow()
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

            val job = application.launch(handlerContext) {
                val call = JettyApplicationCall(
                    application,
                    request,
                    response,
                    executor = executor,
                    userContext = application.coroutineContext,
                    coroutineContext = currentCoroutineContext(),
                    idleTimeout = configuration.idleTimeout
                )

                try {
                    pipeline.execute(call)
                    callback.succeeded()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    if (response.isCommitted || runCatching {
                            Response.writeError(
                                request,
                                response,
                                callback,
                                HttpStatus.GONE_410,
                                cancelled.message,
                                cancelled
                            )
                        }.isFailure
                    ) {
                        callback.failed(cancelled)
                    }
                } catch (channelFailed: ChannelIOException) {
                    callback.failed(channelFailed)
                } catch (error: Throwable) {
                    logError(call, error)
                    tryWriteError(response, request, callback, error)
                }
            }

            request.addIdleTimeoutListener { timeoutException ->
                job.cancel(CancellationException("Jetty idle timeout expired", timeoutException))
                tryWriteError(response, request, callback, timeoutException)
                // Returning true indicates that the idle timeout should be handled by the container as a fatal failure.
                true
            }
        } catch (ex: Throwable) {
            environment.log.error("Application cannot fulfill the request", ex)
            Response.writeError(request, response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500, ex.message, ex)
        }

        return true
    }

    private fun tryWriteError(
        response: Response,
        request: Request,
        callback: Callback,
        throwable: Throwable
    ) {
        if (response.isCommitted || runCatching {
                Response.writeError(
                    request,
                    response,
                    callback,
                    HttpStatus.INTERNAL_SERVER_ERROR_500,
                    throwable.message,
                    throwable
                )
            }.isFailure
        ) {
            callback.failed(throwable)
        }
    }
}
