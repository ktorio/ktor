package io.ktor.server.servlet

import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.server.host.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.io.*
import java.lang.reflect.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

open class ServletApplicationResponse(call: ServletApplicationCall,
                                      protected val servletRequest: HttpServletRequest,
                                      protected val servletResponse: HttpServletResponse,
                                      protected val hostCoroutineContext: CoroutineContext,
                                      protected val userCoroutineContext: CoroutineContext
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

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        servletResponse.status = upgrade.status?.value ?: HttpStatusCode.SwitchingProtocols.value
        upgrade.headers.flattenEntries().forEach { e ->
            servletResponse.addHeader(e.first, e.second)
        }

        servletResponse.flushBuffer()
        val handler = servletRequest.upgrade(ServletUpgradeHandler::class.java)
        handler.up = UpgradeRequest(servletResponse, upgrade, hostCoroutineContext, userCoroutineContext)

//        servletResponse.flushBuffer()

        completed = true
//        servletRequest.asyncContext?.complete() // causes pipeline execution break however it is required for websocket
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

    // the following types need to be public as they are accessed through reflection

    class UpgradeRequest(val response: HttpServletResponse,
                         val upgradeMessage: FinalContent.ProtocolUpgrade,
                         val hostContext: CoroutineContext,
                         val userAppContext: CoroutineContext)

    class ServletUpgradeHandler : HttpUpgradeHandler {
        @Volatile
        lateinit var up: UpgradeRequest

        override fun init(webConnection: WebConnection?) {
            if (webConnection == null) {
                throw IllegalArgumentException("Upgrade processing requires WebConnection instance")
            }

            val servletReader = servletReader(webConnection.inputStream)
            val servletWriter = servletWriter(webConnection.outputStream)

            val inputChannel = CIOReadChannelAdapter(servletReader.channel)
            val outputChannel = CIOWriteChannelAdapter(servletWriter.channel)

            val closeable = Closeable {
                servletWriter.channel.close()

                runBlocking {
                    servletReader.cancel()
                    servletWriter.join()
                    servletReader.join()
                }

                webConnection.close()
            }

            runBlocking {
                up.upgradeMessage.upgrade(inputChannel, outputChannel, closeable, up.hostContext, up.userAppContext)
            }
        }

        override fun destroy() {
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
