/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.servlet

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import javax.servlet.http.*

@Deprecated(message = "Renamed to ServletServerResponse", replaceWith = ReplaceWith("ServletServerResponse"))
public typealias ServletApplicationResponse = ServletServerResponse

public abstract class ServletServerResponse(
    call: PipelineCall,
    protected val servletResponse: HttpServletResponse,
    private val managedByEngineHeaders: Set<String>
) : BaseServerResponse(call) {
    override fun setStatus(statusCode: HttpStatusCode) {
        servletResponse.status = statusCode.value
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override val managedByEngineHeaders = this@ServletServerResponse.managedByEngineHeaders

        override fun engineAppendHeader(name: String, value: String) {
            servletResponse.addHeader(name, value)
        }

        override fun getEngineHeaderNames(): List<String> = servletResponse.headerNames.toList()
        override fun getEngineHeaderValues(name: String): List<String> = servletResponse.getHeaders(name).toList()
    }

    protected abstract fun createResponseJob(): ReaderJob

    @Volatile
    protected var completed: Boolean = false

    private val responseJob = lazy {
        createResponseJob()
    }

    private val responseChannel = lazy {
        responseJob.value.channel
    }

    public final override suspend fun responseChannel(): ByteWriteChannel = responseChannel.value

    init {
        pipeline.intercept(ServerSendPipeline.Engine) {
            if (completed) return@intercept
            completed = true

            if (responseJob.isInitialized()) {
                responseJob.value.apply {

                    runCatching {
                        channel.flushAndClose()
                    }
                    join()
                }
                return@intercept
            }

            try {
                @Suppress("BlockingMethodInNonBlockingContext")
                servletResponse.flushBuffer()
            } catch (cause: Throwable) {
                throw ChannelWriteException(exception = cause)
            }
        }
    }
}
