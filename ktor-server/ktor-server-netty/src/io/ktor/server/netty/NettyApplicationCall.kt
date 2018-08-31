package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.server.engine.*
import io.netty.channel.*
import io.netty.util.*
import kotlinx.coroutines.*

abstract class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    private val requestMessage: Any) : BaseApplicationCall(application) {

    abstract override val request: NettyApplicationRequest
    abstract override val response: NettyApplicationResponse

    val responseWriteJob = Job()

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