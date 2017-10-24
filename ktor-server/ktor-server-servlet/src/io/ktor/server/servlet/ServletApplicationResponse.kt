package io.ktor.server.servlet

import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.server.host.*
import io.ktor.util.*
import java.io.*
import java.lang.reflect.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

open class ServletApplicationResponse(call: ServletApplicationCall,
                                      protected val servletRequest: HttpServletRequest,
                                      protected val servletResponse: HttpServletResponse,
                                      protected val hostCoroutineContext: CoroutineContext,
                                      protected val userCoroutineContext: CoroutineContext,
                                      private val servletUpgradeImpl: ServletUpgrade
) : BaseApplicationResponse(call) {
    override fun setStatus(statusCode: HttpStatusCode) {
        servletResponse.status = statusCode.value
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            servletResponse.addHeader(name, value)
        }

        override fun getHostHeaderNames(): List<String> = servletResponse.headerNames.toList()
        override fun getHostHeaderValues(name: String): List<String> = servletResponse.getHeaders(name).toList()
    }

    @Volatile
    private var completed: Boolean = false

    suspend final override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        servletResponse.status = upgrade.status?.value ?: HttpStatusCode.SwitchingProtocols.value
        upgrade.headers.flattenEntries().forEach { e ->
            servletResponse.addHeader(e.first, e.second)
        }

        try {
            servletResponse.flushBuffer()
        } catch (e: IOException) {
            throw ChannelWriteException("Cannot write HTTP upgrade response", e)
        }

        completed = true

        performUpgrade(upgrade)
    }

    private suspend fun performUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        servletUpgradeImpl.performUpgrade(upgrade, servletRequest, servletResponse, hostCoroutineContext, userCoroutineContext)
    }

    private val responseByteChannel = lazy {
        servletWriter(servletResponse.outputStream)
    }

    private val responseChannel = lazy {
        CIOWriteChannelAdapter(responseByteChannel.value.channel)
    }

    override suspend fun responseChannel(): WriteChannel = responseChannel.value

    init {
        pipeline.intercept(ApplicationSendPipeline.Host) {
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
