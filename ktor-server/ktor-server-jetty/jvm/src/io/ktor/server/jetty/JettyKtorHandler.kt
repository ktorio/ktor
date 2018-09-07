package io.ktor.server.jetty

import io.ktor.http.*
import io.ktor.util.pipeline.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import java.lang.IllegalStateException
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import javax.servlet.*
import javax.servlet.http.*
import kotlin.coroutines.*

private val JettyCallHandlerCoroutineName = CoroutineName("jetty-call-handler")

internal class JettyKtorHandler(
    val environment: ApplicationEngineEnvironment,
    private val pipeline: () -> EnginePipeline,
    private val engineDispatcher: CoroutineDispatcher
) : AbstractHandler(), CoroutineScope {
    private val executor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 8)
    private val dispatcher = DispatcherWithShutdown(executor.asCoroutineDispatcher())
    private val multipartConfig = MultipartConfigElement(System.getProperty("java.io.tmpdir"))

    private val handlerJob = Job(environment.parentCoroutineContext[Job])

    override val coroutineContext: CoroutineContext = environment.parentCoroutineContext + handlerJob

    override fun destroy() {
        dispatcher.prepareShutdown()
        try {
            super.destroy()
            executor.shutdownNow()
        } finally {
            handlerJob.cancel()
            dispatcher.completeShutdown()
        }
    }

    override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        try {
            val contentType = request.contentType
            if (contentType != null && contentType.startsWith("multipart/")) {
                baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfig)
                // TODO someone reported auto-cleanup issues so we have to check it
            }

            request.startAsync()?.apply {
                timeout = 0 // Overwrite any default non-null timeout to prevent multiple dispatches
            }
            baseRequest.isHandled = true

            launch(dispatcher + JettyCallHandlerCoroutineName) {
                val call = JettyApplicationCall(
                    environment.application,
                    baseRequest,
                    request,
                    response,
                    engineContext = engineDispatcher,
                    userContext = dispatcher,
                    coroutineContext = coroutineContext
                )

                try {
                    pipeline().execute(call)
                } catch (cancelled: CancellationException) {
                    response.sendError(HttpServletResponse.SC_GONE)
                } catch (channelFailed: ChannelIOException) {
                } catch (t: Throwable) {
                    call.respond(HttpStatusCode.InternalServerError)
                } finally {
                    try {
                        request.asyncContext?.complete()
                    } catch (expected: IllegalStateException) {
                    }
                }
            }
        } catch(ex: Throwable) {
            environment.log.error("Application ${environment.application::class.java} cannot fulfill the request", ex)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
        }
    }
}
