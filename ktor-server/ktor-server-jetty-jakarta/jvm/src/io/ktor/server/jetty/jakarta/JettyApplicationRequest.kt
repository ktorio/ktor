/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.EOFException
import kotlinx.io.IOException
import org.eclipse.jetty.server.Request
import kotlin.coroutines.resume

@InternalAPI
public class JettyApplicationRequest(
    call: PipelineCall,
    request: Request
) : BaseApplicationRequest(call) {

    // See https://jetty.org/docs/jetty/12/programming-guide/arch/io.html#content-source
    private val requestBodyJob: WriterJob =
        call.writer(Dispatchers.IO + CoroutineName("request-reader")) {
            val contentLength = if (request.headers.contains(HttpHeaders.ContentLength)) {
                request.headers.get(HttpHeaders.ContentLength)?.toLong()
            } else {
                null
            }

            var bytesRead = 0L
            while (true) {
                when (val chunk = request.read()) {
                    // nothing available, suspend for more content
                    null -> {
                        suspendCancellableCoroutine { continuation ->
                            request.demand { continuation.resume(Unit) }
                        }
                    }
                    // read the chunk, exit and close channel if last chunk or failure
                    else -> {
                        with(chunk) {
                            if (failure != null) {
                                if (isLast) {
                                    throw failure
                                }
                                call.application.log.warn("Recoverable error reading request body; continuing", failure)
                            } else {
                                bytesRead += byteBuffer.remaining()
                                channel.writeFully(byteBuffer)
                                release()
                                if (contentLength != null && bytesRead > contentLength) {
                                    channel.cancel(IOException("Request body exceeded content length limit"))
                                }
                                if (isLast) {
                                    if (contentLength != null && bytesRead < contentLength) {
                                        channel.cancel(
                                            EOFException("Expected $contentLength bytes, received only $bytesRead")
                                        )
                                    }
                                    return@writer
                                }
                            }
                        }
                    }
                }
            }
        }

    override val cookies: RequestCookies = JettyRequestCookies(this, request)

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
public class JettyRequestCookies(
    request: JettyApplicationRequest,
    private val jettyRequest: Request
) : RequestCookies(request) {
    override fun fetchCookies(): Map<String, String> {
        return Request.getCookies(jettyRequest).associate { it.name to it.value }
    }
}

@InternalAPI
public class JettyHeaders(
    private val jettyRequest: Request
) : Headers {
    override val caseInsensitiveName: Boolean = true

    override fun entries(): Set<Map.Entry<String, List<String>>> {
        return jettyRequest.headers.fieldNamesCollection.map {
            object : Map.Entry<String, List<String>> {
                override val key: String = it
                override val value: List<String> = jettyRequest.headers.getValuesList(it)
            }
        }.toSet()
    }

    override fun getAll(name: String): List<String>? = jettyRequest.headers.getValuesList(name).takeIf {
        it.isNotEmpty()
    }

    override fun get(name: String): String? = jettyRequest.headers.get(name).takeIf { it.isNotEmpty() }

    override fun isEmpty(): Boolean = jettyRequest.headers.size() == 0

    override fun names(): Set<String> = jettyRequest.headers.fieldNamesCollection
}

@InternalAPI
public class JettyConnectionPoint(
    request: Request
) : RequestConnectionPoint {
    @Deprecated("Use localHost or serverHost instead")
    override val host: String = request.httpURI.host

    override val localAddress: String = Request.getLocalAddr(request)

    override val localHost: String = Request.getServerName(request)

    override val localPort: Int = Request.getLocalPort(request)

    override val method: HttpMethod = HttpMethod.parse(request.method)

    @Deprecated("Use localPort or serverPort instead")
    override val port: Int = request.httpURI.port

    override val remoteAddress: String = Request.getRemoteAddr(request)

    override val remoteHost: String = Request.getServerName(request)

    override val remotePort: Int = Request.getRemotePort(request)

    override val scheme: String = request.httpURI.scheme

    override val serverHost: String = Request.getServerName(request)

    override val serverPort: Int = Request.getServerPort(request)

    override val uri: String = request.httpURI.pathQuery

    override val version: String = request.connectionMetaData.httpVersion.asString()
}
