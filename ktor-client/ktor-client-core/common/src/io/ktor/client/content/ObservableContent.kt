/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.content

import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

public typealias ProgressListener = (bytesSendTotal: Long, contentLength: Long) -> Unit

public class ObservableContent(
    private val delegate: OutgoingContent,
    private val callContext: CoroutineContext,
    private val listener: ProgressListener
) : OutgoingContent.ReadChannelContent() {

    private val content: ByteReadChannel = when (delegate) {
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

    override fun readFrom(): ByteReadChannel = GlobalScope.writer(callContext, autoFlush = true) {
        val byteArray = ByteArray(8 * 1024)
        var bytesSend = 0L
        val total = contentLength ?: -1
        while (!content.isClosedForRead) {
            val read = content.readAvailable(byteArray)
            channel.writeFully(byteArray, offset = 0, length = read)
            bytesSend += read
            listener(bytesSend, total)
        }
        val closedCause = content.closedCause
        if (closedCause != null) {
            channel.close(closedCause)
        } else if (bytesSend == 0L) {
            listener(bytesSend, total)
        }
    }.channel
}
