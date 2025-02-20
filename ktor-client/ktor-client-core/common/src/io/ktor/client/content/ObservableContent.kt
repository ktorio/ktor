/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.content

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlin.coroutines.CoroutineContext

/**
 * Callback that can be registered to listen for upload/download progress.
 *
 * This class is used for callbacks in [HttpRequestBuilder.onDownload] and [HttpRequestBuilder.onUpload].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.content.ProgressListener)
 */
public fun interface ProgressListener {
    /**
     * Invokes every time some data is flushed through the [ByteReadChannel].
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.content.ProgressListener.onProgress)
     *
     * @param bytesSentTotal number of transmitted bytes.
     * @param contentLength body size. Can be null if the size is unknown.
     */
    public suspend fun onProgress(bytesSentTotal: Long, contentLength: Long?)
}

internal class ObservableContent(
    private val delegate: OutgoingContent,
    private val callContext: CoroutineContext,
    private val listener: ProgressListener
) : OutgoingContent.ReadChannelContent() {

    private val content: ByteReadChannel = getContent(delegate)

    @OptIn(DelicateCoroutinesApi::class)
    private fun getContent(delegate: OutgoingContent): ByteReadChannel = when (delegate) {
        is ContentWrapper -> getContent(delegate.delegate())
        is ByteArrayContent -> ByteReadChannel(delegate.bytes())
        is ProtocolUpgrade -> throw UnsupportedContentTypeException(delegate)
        is NoContent -> ByteReadChannel.Empty
        is ReadChannelContent -> delegate.readFrom()
        is WriteChannelContent -> GlobalScope.writer(callContext, autoFlush = true) {
            delegate.writeTo(channel)
        }.channel
    }

    override val contentType: ContentType?
        get() = delegate.contentType
    override val contentLength: Long?
        get() = delegate.contentLength
    override val status: HttpStatusCode?
        get() = delegate.status
    override val headers: Headers
        get() = delegate.headers

    override fun <T : Any> getProperty(key: AttributeKey<T>): T? = delegate.getProperty(key)
    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?): Unit = delegate.setProperty(key, value)

    override fun readFrom(): ByteReadChannel = content.observable(callContext, contentLength, listener)
}
