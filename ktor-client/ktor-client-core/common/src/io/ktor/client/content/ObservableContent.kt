/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.content

import io.ktor.client.call.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Callback that can be registered to listen for upload/download progress
 * @param bytesSentTotal number of transmitted bytes
 * @param contentLength body size. Can be -1 if the size is unknown
 */
public typealias ProgressListener = suspend (bytesSentTotal: Long, contentLength: Long) -> Unit

internal class ObservableContent(
    delegate: OutgoingContent,
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

    @Suppress("CanBePrimaryConstructorProperty") // required to avoid InvalidMutabilityException on native
    private val delegate = delegate

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
