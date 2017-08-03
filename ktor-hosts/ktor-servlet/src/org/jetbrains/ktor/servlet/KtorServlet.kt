package org.jetbrains.ktor.servlet

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.lang.reflect.*
import java.util.concurrent.*
import javax.servlet.http.*

abstract class KtorServlet : HttpServlet() {
    private val hostExecutor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors())
    private val hostDispatcher = DispatcherWithShutdown(hostExecutor.asCoroutineDispatcher())

    private val executor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 8)
    private val dispatcher = DispatcherWithShutdown(executor.asCoroutineDispatcher())

    abstract val application: Application
    abstract val hostPipeline: HostPipeline

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
            val call = ServletApplicationCall(application, request, response, NoPool, { builder ->
                tryPush(request, builder)
            }, hostDispatcher, userAppContext = dispatcher)

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

    private fun tryPush(request: HttpServletRequest, builder: ResponsePushBuilder): Boolean {
        return listOf("org.jetbrains.ktor.servlet.v4.PushKt.doPush")
                .mapNotNull { tryFind(it) }
                .any { function ->
                    tryInvoke(function, request, builder)
                }
    }

    private fun tryInvoke(function: Method, request: HttpServletRequest, builder: ResponsePushBuilder) = try {
        function.invoke(null, request, builder) as Boolean
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