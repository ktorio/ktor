package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import java.lang.reflect.*
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
            val call = ServletApplicationCall(application, request, response, { latch.countDown() }, { call, block, next ->
                tryPush(request, call, block, next)
            })
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