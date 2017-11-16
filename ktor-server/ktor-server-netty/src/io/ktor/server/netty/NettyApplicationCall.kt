package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.server.engine.*
import io.netty.channel.*
import io.netty.util.*
import kotlinx.coroutines.experimental.*

internal abstract class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    private val requestMessage: Any) : BaseApplicationCall(application) {

    override val bufferPool = NettyByteBufferPool(context)

    override abstract val request: NettyApplicationRequest
    override abstract val response: NettyApplicationResponse

    internal val responseWriteJob = Job()

    internal suspend fun finish() {
        try {
            response.ensureResponseSent()
            responseWriteJob.join()
        } finally {
            request.close()
            ReferenceCountUtil.release(requestMessage)
        }
    }

    internal fun dispose() {
        response.close()
        request.close()
        ReferenceCountUtil.release(requestMessage)
    }
}