package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.cookie.*
import io.netty.handler.codec.http.multipart.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.*
import java.util.concurrent.atomic.*

internal class NettyApplicationRequest(private val request: HttpRequest,
                                       private val bodyConsumed: Boolean,
                                       val urlEncodedParameters: () -> ValuesMap,
                                       val context: ChannelHandlerContext,
                                       val drops: LastDropsCollectorHandler?) : ApplicationRequest, Closeable {
    override val headers by lazy {
        ValuesMap.build(caseInsensitiveKey = true) { request.headers().forEach { append(it.key, it.value) } }
    }

    override val requestLine: HttpRequestLine by lazy {
        HttpRequestLine(HttpMethod.parse(request.method.name()), request.uri, request.protocolVersion.text())
    }

    override val parameters: ValuesMap by lazy {
        ValuesMap.build {
            QueryStringDecoder(request.uri).parameters().forEach {
                appendAll(it.key, it.value)
            }
            appendAll(urlEncodedParameters())
        }
    }

    private val contentChannelState = AtomicReference<ReadChannelState>(ReadChannelState.NEUTRAL)
    private val multipart = lazy {
        val decoder = HttpPostMultipartRequestDecoder(request)
        val multipartHandler = NettyMultiPartData(decoder, this@NettyApplicationRequest)

        context.executeInLoop {
            drops?.transferTo(context, multipartHandler)

            context.pipeline().addLast(multipartHandler)
            context.channel().config().isAutoRead = true
            context.read()
        }

        multipartHandler
    }

    private val contentChannel = lazy {
        val channel = BodyHandlerChannelAdapter(context)

        context.executeInLoop {
            drops?.transferTo(context, channel)

            context.pipeline().addLast(channel)
            channel.requestNext()
        }

        channel
    }

    override val content: RequestContent = object : RequestContent(this) {
        override fun getMultiPartData(): MultiPartData {
            if (contentChannelState.switchTo(ReadChannelState.MULTIPART_HANDLER)) {
                return multipart.value
            }

            throw IllegalStateException("Couldn't get multipart, most likely a raw channel already acquired, state is ${contentChannelState.get()}")
        }

        override fun getInputStream(): InputStream = getReadChannel().asInputStream()
        override fun getReadChannel(): ReadChannel {
            if (contentChannelState.switchTo(ReadChannelState.RAW_CHANNEL)) {
                return if (bodyConsumed) EmptyReadChannel else contentChannel.value
            }

            throw IllegalStateException("Couldn't get channel, most likely multipart processing was already started, state is ${contentChannelState.get()}")
        }
    }

    override val cookies: RequestCookies = NettyRequestCookies(this)

    override fun close() {
        context.executeInLoop {
            val handlersToRemove = ArrayList<ChannelHandlerAdapter>()

            if (multipart.isInitialized()) {
                multipart.value.decoder.destroy()
                handlersToRemove.add(multipart.value)
            }
            if (contentChannel.isInitialized()) {
                contentChannel.value.close()
                handlersToRemove.add(contentChannel.value)
            }

            for (handler in handlersToRemove) {
                try {
                    context.pipeline().remove(handler)
                } catch (ignore: NoSuchElementException) {
                }
            }
        }
    }

    private fun AtomicReference<ReadChannelState>.switchTo(newState: ReadChannelState) =
            get() == newState || compareAndSet(ReadChannelState.NEUTRAL, newState)

    private enum class ReadChannelState {
        NEUTRAL,
        RAW_CHANNEL,
        MULTIPART_HANDLER
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

internal fun ChannelHandlerContext.executeInLoop(block: () -> Unit) {
    val executor = executor()
    if (channel().isRegistered && !executor.inEventLoop()) {
        executor.execute { block() }
    } else {
        block()
    }
}