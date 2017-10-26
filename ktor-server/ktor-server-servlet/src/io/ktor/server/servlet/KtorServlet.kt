package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.pipeline.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*
import javax.servlet.http.*

abstract class KtorServlet : HttpServlet() {
    private val engineExecutor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors())
    private val engineDispatcher = DispatcherWithShutdown(engineExecutor.asCoroutineDispatcher())

    private val executor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 8)
    private val dispatcher = DispatcherWithShutdown(executor.asCoroutineDispatcher())

    abstract val application: Application
    abstract val enginePipeline: EnginePipeline

    abstract val upgrade: ServletUpgrade

    override fun destroy() {
        engineDispatcher.prepareShutdown()
        dispatcher.prepareShutdown()
        try {
            super.destroy()
            executor.shutdownNow()
            engineExecutor.shutdown()

            executor.awaitTermination(1L, TimeUnit.SECONDS)
            engineExecutor.awaitTermination(1L, TimeUnit.SECONDS)
        } finally {
            engineDispatcher.completeShutdown()
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
                    engineDispatcher, userContext = dispatcher,
                    upgrade = upgrade)

            launch(dispatcher) {
                try {
                    enginePipeline.execute(call)
                } finally {
                    asyncContext?.complete()
                }
            }
        } catch (ex: Throwable) {
            application.log.error("ServletApplicationEngine cannot service the request", ex)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.message)
        }
    }
}