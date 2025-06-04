/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty.jakarta

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.eclipse.jetty.http.HostPortHttpField
import org.eclipse.jetty.http.HttpFields
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.http.MetaData
import org.eclipse.jetty.http2.api.Session
import org.eclipse.jetty.http2.client.HTTP2Client
import org.eclipse.jetty.http2.client.HTTP2ClientSession
import org.eclipse.jetty.http2.frames.HeadersFrame
import org.eclipse.jetty.http2.frames.SettingsFrame
import org.eclipse.jetty.util.Callback
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

internal suspend fun HttpRequestData.executeRequest(
    client: HTTP2Client,
    config: JettyEngineConfig,
    callContext: CoroutineContext
): HttpResponseData {
    val requestTime = GMTDate()
    val session: HTTP2ClientSession = client.connect(url, config).apply {
        settings(SettingsFrame(emptyMap(), true), Callback.NOOP)
    } as HTTP2ClientSession

    val headersFrame = prepareHeadersFrame()
    val responseChannel = ByteChannel()
    val responseListener = JettyResponseListener(this, session, responseChannel, callContext)

    val jettyRequest = JettyHttp2Request(
        withPromise { promise ->
            session.newStream(headersFrame, promise, responseListener)
        }
    )

    sendRequestBody(jettyRequest, body, callContext)

    val (status, headers) = responseListener.awaitHeaders()

    return HttpResponseData(
        status,
        requestTime,
        headers,
        HttpProtocolVersion.HTTP_2_0,
        responseChannel,
        callContext
    )
}

internal suspend fun HTTP2Client.connect(
    url: Url,
    config: JettyEngineConfig
): Session = withPromise { promise ->
    val factory = if (url.protocol.isSecure()) config.sslContextFactory else null
    connect(factory, InetSocketAddress(url.host, url.port), Session.Listener.Adapter(), promise)
}

@OptIn(InternalAPI::class)
private fun HttpRequestData.prepareHeadersFrame(): HeadersFrame {
    val rawHeaders = HttpFields.build()
    forEachHeader(rawHeaders::add)

    val meta = MetaData.Request(
        method.value,
        url.protocol.name,
        HostPortHttpField("${url.host}:${url.port}"),
        url.fullPath,
        HttpVersion.HTTP_2,
        rawHeaders,
        Long.MIN_VALUE
    )

    return HeadersFrame(meta, null, body is OutgoingContent.NoContent)
}

@OptIn(DelicateCoroutinesApi::class)
private fun sendRequestBody(request: JettyHttp2Request, content: OutgoingContent, callContext: CoroutineContext) {
    when (content) {
        is OutgoingContent.NoContent -> return
        is OutgoingContent.ByteArrayContent -> GlobalScope.launch(callContext) {
            request.write(ByteBuffer.wrap(content.bytes()))
            request.endBody()
        }
        is OutgoingContent.ReadChannelContent -> writeRequest(content.readFrom(), request, callContext)
        is OutgoingContent.WriteChannelContent -> {
            val source = GlobalScope.writer(callContext) { content.writeTo(channel) }.channel
            writeRequest(source, request, callContext)
        }
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(content)
        is OutgoingContent.ContentWrapper -> sendRequestBody(request, content.delegate(), callContext)
    }
}

@OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
private fun writeRequest(
    from: ByteReadChannel,
    request: JettyHttp2Request,
    callContext: CoroutineContext
): Job = GlobalScope.launch(callContext) {
    HttpClientDefaultPool.useInstance { buffer ->
        from.pass(buffer) { request.write(it) }
        request.endBody()
    }
}
