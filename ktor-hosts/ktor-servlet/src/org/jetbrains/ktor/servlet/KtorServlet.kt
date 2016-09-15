package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import java.lang.reflect.*
import javax.servlet.http.*

abstract class KtorServlet : HttpServlet() {

    abstract val application: Application
    protected val hostPipeline = defaultHostPipeline()

    override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        if (response.isCommitted) {
            return
        }

        response.characterEncoding = "UTF-8"
        request.characterEncoding = "UTF-8"

        try {
            request.startAsync()
            val call = ServletApplicationCall(application, request, response, NoPool, { call, block, next ->
                tryPush(request, call, block, next)
            })

            call.executeOn(application.executor, hostPipeline).whenComplete { state, throwable ->
                when (state) {
                    PipelineState.Finished, PipelineState.FinishedAll -> {
                        request.asyncContext.complete()
                    }
                    PipelineState.Failed -> {
                        application.environment.log.error("Application ${application.javaClass} cannot fulfill the request", throwable)
                        call.execution.runBlockWithResult {
                            call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                    null, PipelineState.Executing -> {}
                }
            }
        } catch (ex: Throwable) {
            application.environment.log.error("ServletApplicationHost cannot service the request", ex)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.message)
        }
    }

    override fun init() {
        application.environment.log.trace("Application initialized") // access application to ensure initialized
    }

    override fun destroy() {
        super.destroy()
        application.dispose()
    }

    private fun tryPush(request: HttpServletRequest, call: ApplicationCall, block: ResponsePushBuilder.() -> Unit, next: () -> Unit) {
        listOf(
                "org.jetbrains.ktor.servlet.v4.PushKt.doPush",
                "org.jetbrains.ktor.servlet.v4.TomcatInternalPushKt.doPushInternal"
        ).mapNotNull { tryFind(it) }
        .firstOrNull { function ->
            tryInvoke(function, request, call, block, next)
        } ?: next()
    }

    private fun tryInvoke(function: Method, request: HttpServletRequest, call: ApplicationCall, block: ResponsePushBuilder.() -> Unit, next: () -> Unit) = try {
        function.invoke(null, request, call, block, next)
        true
    } catch (ignore: ReflectiveOperationException) {
        false
    } catch (ignore: LinkageError) {
        false
    }

    private fun tryFind(spec: String): Method? = try {
        require("." in spec)
        val methodName = spec.substringAfterLast(".")

        Class.forName(spec.substringBeforeLast(".")).methods.singleOrNull { it.name == methodName }
    } catch (ignore: ReflectiveOperationException) {
        null
    } catch (ignore: LinkageError) {
        null
    }
}