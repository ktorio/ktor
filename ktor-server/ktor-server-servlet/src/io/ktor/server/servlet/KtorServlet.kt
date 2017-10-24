package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.pipeline.*
import io.ktor.server.host.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*
import javax.servlet.http.*

abstract class KtorServlet : HttpServlet() {
    private val hostExecutor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors())
    private val hostDispatcher = DispatcherWithShutdown(hostExecutor.asCoroutineDispatcher())

    private val executor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 8)
    private val dispatcher = DispatcherWithShutdown(executor.asCoroutineDispatcher())

    abstract val application: Application
    abstract val hostPipeline: HostPipeline

    abstract val upgrade: ServletUpgrade

    override fun destroy() {
        hostDispatcher.prepareShutdown()
        dispatcher.prepareShutdown()
        try {
            super.destroy()
            executor.shutdownNow()
            hostExecutor.shutdown()

            executor.awaitTermination(1L, TimeUnit.SECONDS)
            hostExecutor.awaitTermination(1L, TimeUnit.SECONDS)
        } finally {
            hostDispatcher.completeShutdown()
            dispatcher.completeShutdown()
        }

    }

    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        if (response.isCommitted) {
            return
        }

        response.characterEncoding = "UTF-8"
        request.characterEncoding = "UTF-8"

        try {
            val asyncContext = request.startAsync().apply {
                timeout = 0L
            }

            val call = ServletApplicationCall(application, request, response, NoPool,
                    hostDispatcher, userAppContext = dispatcher,
                    upgrade = upgrade)

            launch(dispatcher) {
                try {
                    hostPipeline.execute(call)
                } finally {
                    asyncContext?.complete()
                }
            }
        } catch (ex: Throwable) {
            application.log.error("ServletApplicationHost cannot service the request", ex)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.message)
        }
    }
}