package org.jetbrains.ktor.jetty

import kotlinx.coroutines.experimental.*
import org.eclipse.jetty.io.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.cio.ByteBufferPool
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.nio.*
import java.util.concurrent.*
import javax.servlet.*
import javax.servlet.http.*

internal class JettyKtorHandler(val environment: ApplicationHostEnvironment, private val pipeline: () -> HostPipeline, private val hostDispatcher: CoroutineDispatcher) : AbstractHandler() {
    private val executor = ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 8)
    private val dispatcher = DispatcherWithShutdown(executor.asCoroutineDispatcher())
    private val MULTI_PART_CONFIG = MultipartConfigElement(System.getProperty("java.io.tmpdir"))

    override fun destroy() {
        dispatcher.prepareShutdown()
        try {
            super.destroy()
            executor.shutdownNow()
        } finally {
            dispatcher.completeShutdown()
        }
    }

    override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        val call = JettyApplicationCall(environment.application, server, request, response, byteBufferPool, { builder ->
            if (baseRequest.isPushSupported) {
                baseRequest.pushBuilder.apply {
                    this.method(builder.method.value)
                    this.path(builder.url.encodedPath)
                    val query = builder.url.build().substringAfter('?', "").takeIf { it.isNotEmpty() }
                    if (query != null) {
                        queryString(query)
                    }

                    push()
                }

                true
            } else {
                false
            }
        }, hostContext = hostDispatcher, userAppContext = dispatcher)

        try {
            val contentType = request.contentType
            if (contentType != null && contentType.startsWith("multipart/")) {
                baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG)
                // TODO someone reported auto-cleanup issues so we have to check it
            }

            request.startAsync()?.apply {
                timeout = 0 // Overwrite any default non-null timeout to prevent multiple dispatches
            }
            baseRequest.isHandled = true

            launch(dispatcher) {
                try {
                    pipeline().execute(call)
                } finally {
                    request.asyncContext?.complete()
                }
            }
        } catch(ex: Throwable) {
            environment.log.error("Application ${environment.application::class.java} cannot fulfill the request", ex)

            launch(dispatcher) {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }

    private class Ticket(bb: ByteBuffer) : ReleasablePoolTicket(bb)

    private val byteBufferPool = object : ByteBufferPool {
        val jbp = MappedByteBufferPool(16)

        override fun allocate(size: Int) = Ticket(jbp.acquire(size, false).apply { clear() })
        override fun release(buffer: PoolTicket) {
            jbp.release(buffer.buffer)
            (buffer as Ticket).release()
        }
    }
}