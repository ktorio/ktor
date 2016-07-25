package org.jetbrains.ktor.netty.http2

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.*
import io.netty.handler.codec.http2.*

internal class HostHttp2Handler(val encoder: Http2ConnectionEncoder, decoder: Http2ConnectionDecoder, settings: Http2Settings) : Http2ConnectionHandler(decoder, encoder, settings) {
    private var ctx: ChannelHandlerContext? = null

    init {
        encoder.connection().addListener(ConnectionListener())
        decoder.frameListener(FrameListener())
    }

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        this.ctx = ctx
        super.handlerAdded(ctx)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any?, promise: ChannelPromise?) {
        when (msg) {
            is Http2StreamFrame -> writeStreamFrame(msg, promise)
            // TODO go away frame
            else -> super.write(ctx, msg, promise)
        }
    }

    private fun writeStreamFrame(frame: Http2StreamFrame, promise: ChannelPromise?) {
        val ctx = ctx ?: throw IllegalStateException("Not yet initialized")

        when (frame) {
            is Http2DataFrame -> encoder.writeData(ctx, frame.streamId(), frame.content().retain(), frame.padding(), frame.isEndStream, promise)
            is Http2HeadersFrame -> encoder.writeHeaders(ctx, frame.streamId(), frame.headers(), frame.padding(), frame.isEndStream, promise)
            is Http2ResetFrame -> encoder.writeRstStream(ctx, frame.streamId(), frame.errorCode(), promise)
            is Http2WindowUpdateFrame -> {
                connection().stream(frame.streamId())?.let {
                    connection().local().flowController().consumeBytes(it, frame.windowSizeIncrement())
                }
            }
            is Http2PushPromiseFrame -> encoder.writePushPromise(ctx, frame.streamId(), frame.promisedStreamId, frame.headers, 0, promise)

            else -> throw UnsupportedMessageTypeException(frame)
        }
    }

    private inner class ConnectionListener : Http2ConnectionAdapter() {
        override fun onStreamActive(stream: Http2Stream) {
            ctx?.fireUserEventTriggered(Http2StreamActiveEvent(stream.id()))
        }

        override fun onStreamClosed(stream: Http2Stream) {
            ctx?.fireUserEventTriggered(Http2StreamClosedEvent(stream.id()))
        }

        override fun onGoAwayReceived(lastStreamId: Int, errorCode: Long, debugData: ByteBuf?) {
            ctx?.fireChannelRead(DefaultHttp2GoAwayFrame(errorCode, debugData)) // TODO lastStreamId
        }
    }

    private inner class FrameListener : Http2FrameAdapter() {
        override fun onHeadersRead(ctx: ChannelHandlerContext, streamId: Int, headers: Http2Headers, padding: Int, endStream: Boolean) {
            ctx.fireChannelRead(DefaultHttp2HeadersFrame(headers, endStream, padding).apply { setStreamId(streamId) })
        }

        override fun onHeadersRead(ctx: ChannelHandlerContext, streamId: Int, headers: Http2Headers, streamDependency: Int, weight: Short, exclusive: Boolean, padding: Int, endStream: Boolean) {
            onHeadersRead(ctx, streamId, headers, padding, endStream)
        }

        override fun onDataRead(ctx: ChannelHandlerContext, streamId: Int, data: ByteBuf, padding: Int,
                                endOfStream: Boolean): Int {

            ctx.fireChannelRead(DefaultHttp2DataFrame(data.retain(), endOfStream, padding).apply { setStreamId(streamId) })

            return 0
        }

        override fun onRstStreamRead(ctx: ChannelHandlerContext, streamId: Int, errorCode: Long) {
            ctx.fireChannelRead(DefaultHttp2ResetFrame(errorCode).apply { setStreamId(streamId) })
        }
    }
}