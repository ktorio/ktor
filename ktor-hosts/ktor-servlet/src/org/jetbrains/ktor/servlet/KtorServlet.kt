package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.servlet.http.*

abstract class KtorServlet : HttpServlet() {

    abstract val application: Application

    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        if (response.isCommitted) {
            return
        }

        response.characterEncoding = "UTF-8"
        request.characterEncoding = "UTF-8"

        try {
            val latch = CountDownLatch(1)
            val upgraded = AtomicBoolean(false)
            val call = ServletApplicationCall(application, request, response, { latch.countDown() }, { call, block, next -> next() })
            var throwable: Throwable? = null
            var pipelineState: PipelineState? = null

            setupUpgradeHelper(request, response, latch, call, upgraded)

            call.executeOn(application.executor, application).whenComplete { state, t ->
                pipelineState = state
                throwable = t
                latch.countDown()
            }

            latch.await()
            when {
                throwable != null -> throw throwable!!
                pipelineState == null -> {}
                pipelineState == PipelineState.Executing -> {
                    if (!upgraded.get()) {
                        call.ensureAsync()
                    }
                }
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
        application.environment.log.info("Application initialized") // access application to ensure initialized
    }

    override fun destroy() {
        super.destroy()
        application.dispose()
    }
}