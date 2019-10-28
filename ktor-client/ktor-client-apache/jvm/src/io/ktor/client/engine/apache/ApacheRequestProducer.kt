/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import io.ktor.utils.io.*
import org.apache.http.*
import org.apache.http.HttpHeaders
import org.apache.http.HttpRequest
import org.apache.http.client.config.*
import org.apache.http.client.methods.*
import org.apache.http.client.utils.*
import org.apache.http.entity.*
import org.apache.http.nio.*
import org.apache.http.nio.protocol.*
import org.apache.http.protocol.*
import java.nio.ByteBuffer
import kotlin.coroutines.*

internal class ApacheRequestProducer(
    private val requestData: HttpRequestData,
    private val config: ApacheEngineConfig,
    private val callContext: CoroutineContext
) : HttpAsyncRequestProducer {
    private val requestChannel = Channel<ByteBuffer>(1)
    private val request: HttpUriRequest = setupRequest()
    private val host = URIUtils.extractHost(request.uri)!!

    private val ioControl: AtomicRef<IOControl?> = atomic(null)
    private val currentBuffer: AtomicRef<ByteBuffer?> = atomic(null)

    init {
        when (val body = requestData.body) {
            is OutgoingContent.ByteArrayContent -> {
                requestChannel.offer(ByteBuffer.wrap(body.bytes()))
                requestChannel.close()
            }
            is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(body)
            is OutgoingContent.NoContent -> requestChannel.close()
            is OutgoingContent.ReadChannelContent -> prepareBody(body.readFrom())
            is OutgoingContent.WriteChannelContent -> prepareBody(
                GlobalScope.writer(Dispatchers.Unconfined, autoFlush = true) {
                    body.writeTo(channel)
                }.channel
            )
        }
    }

    override fun isRepeatable(): Boolean = true

    override fun getTarget(): HttpHost = host

    override fun generateRequest(): HttpRequest = request

    override fun requestCompleted(context: HttpContext) {
    }

    override fun resetRequest() {}

    override fun failed(cause: Exception) {
        requestChannel.close(cause)

        try {
            requestChannel.poll()?.recycle()
        } catch (_: Throwable) {
        }
    }

    override fun produceContent(encoder: ContentEncoder, ioctrl: IOControl) {
        var buffer = currentBuffer.getAndSet(null) ?: requestChannel.poll()

        if (buffer == null) {
            @UseExperimental(ExperimentalCoroutinesApi::class)
            if (requestChannel.isClosedForReceive) {
                encoder.complete()
                return
            }

            ioctrl.suspendOutput()

            ioControl.value = ioctrl
            buffer = requestChannel.poll() ?: return

            ioControl.value = null
            try {
                ioctrl.requestOutput()
            } catch (cause: Throwable) {
                buffer.recycle()
                throw cause
            }
        }

        try {
            encoder.write(buffer)
        } catch (cause: Throwable) {
            buffer.recycle()
            throw cause
        }

        if (buffer.hasRemaining()) {
            currentBuffer.value = buffer
        } else {
            buffer.recycle()
        }
    }

    override fun close() {
        requestChannel.close()
        currentBuffer.value?.recycle()
    }

    private fun setupRequest(): HttpUriRequest = with(requestData) {
        val builder = RequestBuilder.create(method.value)!!
        builder.uri = url.toURI()

        val content = requestData.body
        val length = headers[io.ktor.http.HttpHeaders.ContentLength] ?: content.contentLength?.toString()
        val type = headers[io.ktor.http.HttpHeaders.ContentType] ?: content.contentType?.toString()

        mergeHeaders(headers, content) { key, value ->
            if (HttpHeaders.CONTENT_LENGTH == key) return@mergeHeaders
            if (HttpHeaders.CONTENT_TYPE == key) return@mergeHeaders

            builder.addHeader(key, value)
        }

        if (body !is OutgoingContent.NoContent && body !is OutgoingContent.ProtocolUpgrade) {
            builder.entity = BasicHttpEntity().apply {
                if (length == null) isChunked = true else contentLength = length.toLong()
                setContentType(type)
            }
        }

        with(config) {
            builder.config = RequestConfig.custom()
                .setRedirectsEnabled(followRedirects)
                .setSocketTimeout(socketTimeout)
                .setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .customRequest()
                .setupTimeoutAttributes(requestData)
                .build()
        }

        return builder.build()
    }

    private fun prepareBody(bodyChannel: ByteReadChannel): Job {
        val result = GlobalScope.launch(callContext) {
            while (!bodyChannel.isClosedForRead) {
                val buffer = HttpClientDefaultPool.borrow()
                try {
                    while (bodyChannel.readAvailable(buffer) != -1 && buffer.remaining() > 0) {
                    }
                    buffer.flip()
                    requestChannel.send(buffer)
                } catch (cause: Throwable) {
                    HttpClientDefaultPool.recycle(buffer)
                    currentBuffer.getAndSet(null)?.recycle()
                    throw cause
                }

                ioControl.getAndSet(null)?.requestOutput()
            }
        }

        result.invokeOnCompletion { cause ->
            requestChannel.close(cause)
            if (cause != null) callContext.cancel()
            ioControl.getAndSet(null)?.requestOutput()
        }

        return result
    }

    private fun ByteBuffer.recycle() {
        if (requestData.body is OutgoingContent.WriteChannelContent ||
            requestData.body is OutgoingContent.ReadChannelContent) {
            HttpClientDefaultPool.recycle(this)
        }
    }
}

private fun RequestConfig.Builder.setupTimeoutAttributes(requestData: HttpRequestData): RequestConfig.Builder = also {
    requestData.getCapabilityOrNull(HttpTimeout)?.let { timeoutAttributes ->
        timeoutAttributes.connectTimeoutMillis?.let { setConnectTimeout(convertLongTimeoutToIntWithInfiniteAsZero(it)) }
        timeoutAttributes.socketTimeoutMillis?.let { setSocketTimeout(convertLongTimeoutToIntWithInfiniteAsZero(it)) }
    }
}
