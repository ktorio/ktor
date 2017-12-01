package io.ktor.server.servlet

import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.server.engine.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.lang.reflect.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

open class ServletApplicationResponse(call: ServletApplicationCall,
                                      protected val servletRequest: HttpServletRequest,
                                      protected val servletResponse: HttpServletResponse,
                                      protected val engineContext: CoroutineContext,
                                      protected val userContext: CoroutineContext,
                                      private val servletUpgradeImpl: ServletUpgrade
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

    @Volatile
    private var completed: Boolean = false

    suspend final override fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        try {
            servletResponse.flushBuffer()
        } catch (e: IOException) {
            throw ChannelWriteException("Cannot write HTTP upgrade response", e)
        }

        completed = true

        servletUpgradeImpl.performUpgrade(upgrade, servletRequest, servletResponse, engineContext, userContext)
    }

    private val responseByteChannel = lazy {
        servletWriter(servletResponse.outputStream)
    }

    private val responseChannel = lazy {
        responseByteChannel.value.channel
    }

    override suspend fun responseChannel(): ByteWriteChannel = responseChannel.value

    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            if (!completed) {
                completed = true
                if (responseByteChannel.isInitialized()) {
                    responseByteChannel.value.apply {
                        channel.close()
                        join()
                    }
                } else {
                    servletResponse.flushBuffer()
                }
            }
        }
    }

    override fun push(builder: ResponsePushBuilder) {
        if (!tryPush(servletRequest, builder)) {
            super.push(builder)
        }
    }

    private fun tryPush(request: HttpServletRequest, builder: ResponsePushBuilder): Boolean {
        return foundPushImpls.any { function ->
            tryInvoke(function, request, builder)
        }
    }

    companion object {
        private val foundPushImpls by lazy {
            listOf("io.ktor.servlet.v4.PushKt.doPush").mapNotNull { tryFind(it) }
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

        private fun tryInvoke(function: Method, request: HttpServletRequest, builder: ResponsePushBuilder) = try {
            function.invoke(null, request, builder) as Boolean
        } catch (ignore: ReflectiveOperationException) {
            false
        } catch (ignore: LinkageError) {
            false
        }
    }
}
