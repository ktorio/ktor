package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.transform.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.servlet.http.*

abstract class KtorServlet : HttpServlet() {

    abstract val application: Application

    private val threadCounter = AtomicInteger()
    val executorService = ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), 100, 30L, TimeUnit.SECONDS, LinkedBlockingQueue(), { r ->
        Thread(r, "apphost-pool-thread-${threadCounter.incrementAndGet()}")
    })

    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        response.characterEncoding = "UTF-8"
        request.characterEncoding = "UTF-8"

        try {
            val latch = CountDownLatch(1)
            val call = ServletApplicationCall(application, request, response, executorService) { latch.countDown() }
            var throwable: Throwable? = null
            var pipelineState: PipelineState? = null

            call.executeOn(executorService, application).whenComplete { state, t ->
                pipelineState = state
                throwable = t
                latch.countDown()
            }

            latch.await()
            when {
                throwable != null -> throw throwable!!
                pipelineState == null -> {}
                pipelineState == PipelineState.Executing -> call.ensureAsync()
                !call.completed -> {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND)
                    call.close()
                }
                else -> {}
            }
        } catch (ex: Throwable) {
            application.environment.log.error("ServletApplicationHost cannot service the request", ex)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.message)
        }
    }

    override fun init() {
        application.setupDefaultHostPages()
        application.install(TransformationSupport).registerDefaultHandlers()
    }
}