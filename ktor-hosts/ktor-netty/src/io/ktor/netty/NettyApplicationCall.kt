package io.ktor.netty

import io.netty.channel.*
import io.netty.util.*
import io.ktor.application.*
import io.ktor.host.*
import io.ktor.response.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*
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
            response.close()
            responseWriteJob.join()
        } finally {
            request.close()
            ReferenceCountUtil.release(requestMessage)
        }
    }
}