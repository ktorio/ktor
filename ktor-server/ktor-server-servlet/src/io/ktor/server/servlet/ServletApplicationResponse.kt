package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.util.cio.*
import kotlinx.coroutines.io.*
import javax.servlet.http.*

@Suppress("KDocMissingDocumentation")
@EngineAPI
abstract class ServletApplicationResponse(
    call: ApplicationCall,
    protected val servletResponse: HttpServletResponse
) : BaseApplicationResponse(call) {
    override fun setStatus(statusCode: HttpStatusCode) {
        servletResponse.status = statusCode.value
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
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

    final override suspend fun responseChannel(): ByteWriteChannel = responseChannel.value

    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            if (!completed) {
                completed = true
                if (responseJob.isInitialized()) {
                    responseJob.value.apply {
                        channel.close()
                        join()
                    }
                } else {
                    try {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        servletResponse.flushBuffer()
                    } catch (cause: Throwable) {
                        throw ChannelWriteException(exception = cause)
                    }
                }
            }
        }
    }
}
