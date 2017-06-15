package org.jetbrains.ktor.netty

import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.cookie.*
import io.netty.handler.codec.http.multipart.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.*
import java.util.concurrent.atomic.*
import kotlin.collections.LinkedHashSet

internal class NettyApplicationRequest(
        override val call: NettyApplicationCall,
        private val request: HttpRequest,
        override val local: NettyConnectionPoint,
        val contentQueue: NettyContentQueue) : BaseApplicationRequest(), Closeable {

    override val headers: ValuesMap = NettyHeadersValuesMap(request)

    class NettyHeadersValuesMap(request: HttpRequest) : ValuesMap {
        private val headers: HttpHeaders = request.headers()
        override fun get(name: String): String? = headers.get(name)
        override fun contains(name: String): Boolean = headers.contains(name)
        override fun contains(name: String, value: String): Boolean = headers.contains(name, value, true)
        override fun getAll(name: String): List<String> = headers.getAll(name)
        override fun forEach(body: (String, List<String>) -> Unit) {
            val names = headers.names()
            names.forEach { body(it, headers.getAll(it)) }
        }

        override fun entries(): Set<Map.Entry<String, List<String>>> {
            val names = headers.names()
            return names.mapTo(LinkedHashSet(names.size)) {
                object : Map.Entry<String, List<String>> {
                    override val key: String get() = it
                    override val value: List<String> get() = headers.getAll(it)
                }
            }
        }

        override fun isEmpty(): Boolean = headers.isEmpty
        override val caseInsensitiveKey: Boolean get() = true
        override fun names(): Set<String> = headers.names()
    }

    override val queryParameters by lazy {
        parseQueryString(request.uri().substringAfter("?", ""))
    }

    override fun receiveContent() = NettyHttpIncomingContent(this)

    private val contentChannelState = AtomicReference<ReadChannelState>(ReadChannelState.NEUTRAL)

    private val multipart = lazy {
        if (!isMultipart())
            throw IOException("The request content is not multipart encoded")
        val decoder = HttpPostMultipartRequestDecoder(request)
        NettyMultiPartData(decoder, contentQueue)
    }

    private val contentChannel = lazy { HttpContentReadChannel(contentQueue) }

    override val cookies: RequestCookies = NettyRequestCookies(this)

    override fun close() {
        if (multipart.isInitialized()) {
            multipart.value.destroy()
        }

        if (contentChannel.isInitialized()) {
            contentChannel.value.close()
        }
    }


    internal enum class ReadChannelState {
        NEUTRAL,
        RAW_CHANNEL,
        MULTIPART_HANDLER
    }

    class NettyHttpIncomingContent internal constructor(override val request: NettyApplicationRequest) : IncomingContent {
        private fun AtomicReference<NettyApplicationRequest.ReadChannelState>.switchTo(newState: NettyApplicationRequest.ReadChannelState) =
                get() == newState || compareAndSet(NettyApplicationRequest.ReadChannelState.NEUTRAL, newState)

        override fun readChannel(): ReadChannel {
            if (request.contentChannelState.switchTo(NettyApplicationRequest.ReadChannelState.RAW_CHANNEL)) {
                return request.contentChannel.value
            }

            throw IllegalStateException("Couldn't get channel, most likely multipart processing was already started, state is ${request.contentChannelState.get()}")
        }

        override fun multiPartData(): MultiPartData {
            if (request.contentChannelState.switchTo(NettyApplicationRequest.ReadChannelState.MULTIPART_HANDLER)) {
                return request.multipart.value
            }

            throw IllegalStateException("Couldn't get multipart, most likely a raw channel already acquired, state is ${request.contentChannelState.get()}")
        }
    }
}

private class NettyRequestCookies(val owner: ApplicationRequest) : RequestCookies(owner) {
    override val parsedRawCookies: Map<String, String> by lazy {
        owner.headers.getAll("Cookie")?.fold(HashMap<String, String>()) { acc, e ->
            val cookieHeader = owner.header("Cookie") ?: ""
            acc.putAll(ServerCookieDecoder.LAX.decode(cookieHeader).associateBy({ it.name() }, { it.value() }))
            acc
        } ?: emptyMap<String, String>()
    }
}
