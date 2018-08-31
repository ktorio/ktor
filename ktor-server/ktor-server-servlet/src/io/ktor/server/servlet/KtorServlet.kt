package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.util.pipeline.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.lang.IllegalStateException
import java.util.concurrent.*
import javax.servlet.http.*

abstract class KtorServlet : HttpServlet() {
    private val asyncDispatchers = lazy { AsyncDispatchers() }

    abstract val application: Application
    abstract val enginePipeline: EnginePipeline

    abstract val upgrade: ServletUpgrade

    override fun destroy() {
        // Note: container will not call service again, so asyncDispatcher cannot get initialized if it was not yet
        if (asyncDispatchers.isInitialized()) asyncDispatchers.value.destroy()
    }

    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        if (response.isCommitted) return

        try {
            if (request.isAsyncSupported) {
                asyncService(request, response)
            } else {
                blockingService(request, response)
            }
        } catch (ex: Throwable) {
            application.log.error("ServletApplicationEngine cannot service the request", ex)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.message)
        }
    }

    private fun asyncService(request: HttpServletRequest, response: HttpServletResponse) {
        val asyncContext = request.startAsync()!!.apply {
            timeout = 0L
        }
        val ad = asyncDispatchers.value
        val call = AsyncServletApplicationCall(application, request, response,
            engineContext = ad.engineDispatcher, userContext = ad.dispatcher, upgrade = upgrade)
        launch(ad.dispatcher) {
            try {
                enginePipeline.execute(call)
            } finally {
                try {
                    asyncContext.complete()
                } catch (alreadyCompleted: IllegalStateException) {
                    application.log.debug("AsyncContext is already completed due to previous I/O error",
                            alreadyCompleted)
                }
            }
        }
    }

    private fun blockingService(request: HttpServletRequest, response: HttpServletResponse) {
        runBlocking {
            val call = BlockingServletApplicationCall(application, request, response)
            enginePipeline.execute(call)
        }
    }
}

private class AsyncDispatchers {
    val engineExecutor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors())
    val engineDispatcher = DispatcherWithShutdown(engineExecutor.asCoroutineDispatcher())

    val executor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 8)
    val dispatcher = DispatcherWithShutdown(executor.asCoroutineDispatcher())

    fun destroy() {
        engineDispatcher.prepareShutdown()
        dispatcher.prepareShutdown()
        try {
            executor.shutdownNow()
            engineExecutor.shutdown()
            executor.awaitTermination(1L, TimeUnit.SECONDS)
            engineExecutor.awaitTermination(1L, TimeUnit.SECONDS)
        } finally {
            engineDispatcher.completeShutdown()
            dispatcher.completeShutdown()
        }
    }
}
