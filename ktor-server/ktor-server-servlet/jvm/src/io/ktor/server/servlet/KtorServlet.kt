package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import javax.servlet.*
import javax.servlet.http.*
import kotlin.coroutines.*

/**
 * A base class for servlet engine implementations
 */
@EngineAPI
@UseExperimental(InternalAPI::class)
abstract class KtorServlet : HttpServlet(), CoroutineScope {
    private val asyncDispatchers = lazy { AsyncDispatchers() }

    /**
     * Current application instance. Could be lazy
     */
    protected abstract val application: Application

    /**
     * Engine pipeline
     */
    protected abstract val enginePipeline: EnginePipeline

    /**
     * Servlet upgrade implementation
     */
    protected abstract val upgrade: ServletUpgrade

    override val coroutineContext: CoroutineContext  = Dispatchers.Unconfined + SupervisorJob() + CoroutineName("servlet")

    /**
     * Called by the servlet container when loading the servlet (on load)
     */
    override fun init() {
        super.init()
        application.attributes.put(ServletContextAttribute, servletContext!!)
    }

    /**
     * Called by servlet container when the application is going to be undeployed or stopped.
     */
    override fun destroy() {
        coroutineContext.cancel()
        // Note: container will not call service again, so asyncDispatcher cannot get initialized if it was not yet
        if (asyncDispatchers.isInitialized()) asyncDispatchers.value.destroy()
    }

    /**
     * Called by the servlet container when an HTTP request received.
     */
    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        if (response.isCommitted) return

        try {
            if (request.isAsyncSupported) {
                asyncService(request, response)
            } else {
                blockingService(request, response)
            }
        } catch (ioError: ChannelIOException) {
            application.log.debug("I/O error", ioError)
        } catch (ex: Throwable) {
            application.log.error("ServletApplicationEngine cannot service the request", ex)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.message)
        }
    }

    private fun asyncService(request: HttpServletRequest, response: HttpServletResponse) {
        val asyncContext = request.startAsync()!!.apply {
            timeout = 0L
        }

        val asyncDispatchers = asyncDispatchers.value

        launch(asyncDispatchers.dispatcher) {
            val call = AsyncServletApplicationCall(application, request, response,
                engineContext = asyncDispatchers.engineDispatcher,
                userContext = asyncDispatchers.dispatcher,
                upgrade = upgrade,
                parentCoroutineContext = coroutineContext
            )

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
        runBlocking(coroutineContext) {
            val call = BlockingServletApplicationCall(application, request, response, coroutineContext)
            enginePipeline.execute(call)
        }
    }
}

/**
 * Attribute that is added by ktor servlet to application attributes to hold [ServletContext] instance.
 */
@InternalAPI
val ServletContextAttribute: AttributeKey<ServletContext> = AttributeKey("servlet-context")

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
