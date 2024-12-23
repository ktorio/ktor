/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.content.OutgoingContent
import io.ktor.http.parseQueryString
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.JettyWebsocketConnection.Companion.upgradeAndAwait
import io.ktor.server.request.RequestCookies
import io.ktor.server.request.encodeParameters
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.ResponseHeaders
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.ByteBufferPool
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.InternalIoApi
import org.eclipse.jetty.io.EndPoint
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

internal val bufferPool = ByteBufferPool(bufferSize = 8192)
internal val emptyBuffer = ByteBuffer.allocate(0)

@InternalAPI
public class JettyApplicationCall(
    application: Application,
    jettyRequest: Request,
    jettyResponse: Response,
    private val executor: Executor,
    private val userContext: CoroutineContext,
    override val coroutineContext: CoroutineContext,
    private val idleTimeout: Duration?
) : BaseApplicationCall(application) {

    override val request: JettyApplicationRequest =
        JettyApplicationRequest(jettyRequest)
    override val response: JettyApplicationResponse =
        JettyApplicationResponse(
            jettyRequest.connectionMetaData.connection.endPoint,
            jettyResponse
        )

    init {
        putResponseAttribute()
    }

    @InternalAPI
    public inner class JettyApplicationRequest(request: Request) : BaseApplicationRequest(this) {
        private var upgraded: Boolean = false
        private val requestBodyChannel: ByteReadChannel by lazy {
            if (upgraded) return@lazy ByteReadChannel.Empty
            call.bodyReader(request, call.application.log, idleTimeout).channel
        }

        override val cookies: RequestCookies = object : RequestCookies(this@JettyApplicationRequest) {
            override fun fetchCookies(): Map<String, String> =
                Request.getCookies(request).associate { it.name to it.value }
        }

        override val engineHeaders: Headers = JettyHeaders(request)

        override val engineReceiveChannel: ByteReadChannel get() = requestBodyChannel

        override val local: RequestConnectionPoint = JettyConnectionPoint(request)

        override val queryParameters: Parameters by lazy {
            encodeParameters(rawQueryParameters)
        }

        override val rawQueryParameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
            val queryString = request.httpURI.query ?: return@lazy Parameters.Empty
            parseQueryString(queryString, decode = false)
        }

        internal fun upgraded() {
            upgraded = true
        }
    }

    @InternalAPI
    public inner class JettyApplicationResponse(
        private val endpoint: EndPoint,
        private val response: Response,
    ) : BaseApplicationResponse(this@JettyApplicationCall) {

        @Volatile
        private var completed: Boolean = false

        init {
            pipeline.intercept(ApplicationSendPipeline.Engine) {
                if (completed) return@intercept
                completed = true

                if (responseBodyJob.isInitialized()) {
                    responseBodyJob.value.apply {
                        runCatching {
                            channel.flushAndClose()
                        }
                        join()
                    }
                    return@intercept
                }

                try {
                    if (!response.isCommitted) {
                        response.write(true, emptyBuffer, Callback.NOOP)
                    }
                } catch (cause: Throwable) {
                    throw ChannelWriteException(exception = cause)
                }
            }
        }

        @OptIn(InternalCoroutinesApi::class, InternalIoApi::class)
        private val responseBodyJob: Lazy<ReaderJob> = lazy {
            call.bodyWriter(response, idleTimeout)
        }

        override val headers: ResponseHeaders = object : ResponseHeaders() {
            override fun engineAppendHeader(name: String, value: String) {
                response.headers.add(name, value)
            }

            override fun getEngineHeaderNames(): List<String> =
                response.headers.fieldNamesCollection.toList()

            override fun getEngineHeaderValues(name: String): List<String> =
                response.headers.getValuesList(name)
        }

        override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
            // 1. Mark request / response as complete
            completed = true
            request.upgraded()

            // 2. Close body writing job
            if (responseBodyJob.isInitialized()) {
                responseBodyJob.value.channel.flushAndClose()
                responseBodyJob.value.join()
            }

            // 3. Close response output
            suspendCancellableCoroutine { continuation ->
                response.write(true, emptyBuffer, continuation.asCallback())
            }

            // 4. Redirect socket I/O to jetty connection
            val websocketConnection = JettyWebsocketConnection(
                endpoint,
                bufferPool,
                call.coroutineContext,
                executor
            )

            // 5. Handle the websocket upgrade
            upgrade.upgradeAndAwait(
                websocketConnection,
                userContext
            )
        }

        override suspend fun respondFromBytes(bytes: ByteArray) {
            response.write(true, ByteBuffer.wrap(bytes), Callback.NOOP)
        }

        override suspend fun respondNoContent(content: OutgoingContent.NoContent) {
            response.write(true, emptyBuffer, Callback.NOOP)
        }

        override suspend fun responseChannel(): ByteWriteChannel =
            responseBodyJob.value.channel

        override fun setStatus(statusCode: HttpStatusCode) {
            response.status = statusCode.value
        }
    }
}
