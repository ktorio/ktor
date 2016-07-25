package org.jetbrains.ktor.netty.http2

import io.netty.handler.codec.http2.*

class Http2PushPromiseFrame : Http2StreamFrame {
    private var streamId: Int = -1
    val headers = DefaultHttp2Headers(true, 10)
    var promisedStreamId: Int = -1

    override fun setStreamId(streamId: Int): Http2StreamFrame {
        this.streamId = streamId
        return this
    }

    override fun streamId() = streamId

    override fun name() = "PUSH_PROMISE"
}