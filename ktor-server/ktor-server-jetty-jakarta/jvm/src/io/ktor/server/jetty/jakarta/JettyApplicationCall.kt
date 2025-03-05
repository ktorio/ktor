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
import io.ktor.http.toQueryParameters
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.jakarta.JettyWebsocketConnection.Companion.upgradeAndAwait
import io.ktor.server.request.RequestCookies
import io.ktor.server.request.encodeParameters
import io.ktor.server.response.ApplicationSendPipeline
import io.ktor.server.response.ResponseHeaders
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.ByteBufferPool
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.InternalIoApi
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.io.EndPoint
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
    private val engineExecutor: Executor,
    private val appDispatcher: CoroutineContext,
    private val idleTimeout: Duration?,
    override val coroutineContext: CoroutineContext
) : BaseApplicationCall(application) {

    override val request: JettyApplicationRequest =
        JettyApplicationRequest(jettyRequest)
    override val response: JettyApplicationResponse =
        JettyApplicationResponse(jettyRequest.connectionMetaData.connection.endPoint, jettyResponse)

    init {
        putResponseAttribute()
    }

    @InternalAPI
    public inner class JettyApplicationRequest(request: Request) : BaseApplicationRequest(this) {

        // See https://jetty.org/docs/jetty/12/programming-guide/arch/io.html#content-source
        private val requestBodyJob: WriterJob =
            call.bodyReader(request, call.application.log, idleTimeout)

        override val cookies: RequestCookies = object : RequestCookies(this@JettyApplicationRequest) {
            override fun fetchCookies(): Map<String, String> =
                Request.getCookies(request).associate { it.name to it.value }
        }

        override val engineHeaders: Headers = JettyHeaders(request)

        override val engineReceiveChannel: ByteReadChannel by lazy { requestBodyJob.channel }

        override val local: RequestConnectionPoint = JettyConnectionPoint(request)

        override val queryParameters: Parameters by lazy {
            encodeParameters(rawQueryParameters).toQueryParameters()
        }

        override val rawQueryParameters: Parameters by lazy(LazyThreadSafetyMode.NONE) {
            val queryString = request.httpURI.query ?: return@lazy Parameters.Empty
            parseQueryString(queryString, decode = false)
        }
    }

    @InternalAPI
    public inner class JettyApplicationResponse(
        private val endpoint: EndPoint,
        private val response: Response,
    ) : BaseApplicationResponse(this@JettyApplicationCall) {
        init {
            pipeline.intercept(ApplicationSendPipeline.Engine) {
                if (responseJob.isInitialized()) {
                    responseJob.value.apply {
                        runCatching {
                            flushAndClose()
                        }
                    }
                }
            }
        }

        @OptIn(InternalCoroutinesApi::class, InternalIoApi::class)
        private val responseJob: Lazy<ReaderJob> = lazy {
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

        // TODO set idle timeout from websocket config on endpoint
        override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
            if (responseJob.isInitialized()) {
                responseJob.value.cancel()
            }

            // An [AbstractConnection] implementation for translating I/O
            val websocketConnection = JettyWebsocketConnection(
                endpoint,
                engineExecutor,
                bufferPool,
                call.coroutineContext
            )

            // Finish the current response channel by writing an empty message with last=true
            suspendCancellableCoroutine { continuation ->
                response.write(true, emptyBuffer, continuation.asCallback())
            }

            // Start a job for handling the websocket connection and wait for it to finish
            upgrade.upgradeAndAwait(
                websocketConnection,
                appDispatcher
            )
        }

        override suspend fun respondFromBytes(bytes: ByteArray) {
            response.write(true, ByteBuffer.wrap(bytes), Callback.NOOP)
        }

        override suspend fun respondNoContent(content: OutgoingContent.NoContent) {
            response.write(true, emptyBuffer, Callback.NOOP)
        }

        override suspend fun responseChannel(): ByteWriteChannel =
            responseJob.value.channel

        override fun setStatus(statusCode: HttpStatusCode) {
            response.status = statusCode.value
        }
    }

}
