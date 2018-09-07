package io.ktor.server.netty

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.netty.channel.*
import io.netty.util.*
import kotlinx.coroutines.*

@Suppress("KDocMissingDocumentation")
@InternalAPI
abstract class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    private val requestMessage: Any) : BaseApplicationCall(application) {

    abstract override val request: NettyApplicationRequest
    abstract override val response: NettyApplicationResponse

    val responseWriteJob: Job = Job()

    internal suspend fun finish() {
        try {
            response.ensureResponseSent()
        } catch (t: Throwable) {
            finishComplete()
            throw t
        }

        if (responseWriteJob.isCompleted) {
            finishComplete()
            return
        }

        return finishSuspend()
    }

    private suspend fun finishSuspend() {
        try {
            responseWriteJob.join()
        } finally {
            finishComplete()
        }
    }

    private fun finishComplete() {
        responseWriteJob.cancel()
        request.close()
        ReferenceCountUtil.release(requestMessage)
    }

    internal fun dispose() {
        response.close()
        request.close()
        ReferenceCountUtil.release(requestMessage)
    }
}
