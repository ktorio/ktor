/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.multipart.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.coroutines.*

@Suppress("KDocMissingDocumentation")
@InternalAPI
public abstract class NettyApplicationRequest(
    call: ApplicationCall,
    override val coroutineContext: CoroutineContext,
    public val context: ChannelHandlerContext,
    private val requestBodyChannel: ByteReadChannel,
    protected val uri: String,
    internal val keepAlive: Boolean
) : BaseApplicationRequest(call), CoroutineScope {

    public final override val queryParameters: Parameters = object : Parameters {
        private val decoder = QueryStringDecoder(uri)
        override val caseInsensitiveName: Boolean get() = true
        override fun getAll(name: String) = decoder.parameters()[name]
        override fun names() = decoder.parameters().keys
        override fun entries() = decoder.parameters().entries
        override fun isEmpty() = decoder.parameters().isEmpty()
    }

    override val cookies: RequestCookies = NettyApplicationRequestCookies(this)

    override fun receiveChannel(): ByteReadChannel = requestBodyChannel

    private val contentMultipart = lazy {
        if (!isMultipart()) throw IOException("The request content is not multipart encoded")
        val decoder = newDecoder()
        NettyMultiPartData(decoder, context.alloc(), requestBodyChannel)
    }

    protected abstract fun newDecoder(): HttpPostMultipartRequestDecoder

    public fun close() {
        if (contentMultipart.isInitialized()) {
            contentMultipart.value.destroy()
        }
    }
}
