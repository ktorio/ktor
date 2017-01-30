package org.jetbrains.ktor.host

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

abstract class BaseApplicationCall(override val application: Application) : ApplicationCall {
    final override val attributes = Attributes()

    protected val respondPipeline = RespondPipeline()

    suspend override fun respond(message: Any) {
        val responseMessage = ResponseMessage(this, message)
        val phases = respondPipeline.phases
        val pipelineContext = PipelineContext(phases.interceptors(), responseMessage)
        pipelineContext.proceed()

        val value = responseMessage.message
        when (value) {
            is FinalContent -> respondFinalContent(value)
            is ProtocolUpgrade -> pipelineContext.handleUpgrade(value)
            is StreamConsumer -> respondStream(value)
        }
        pipelineContext.finish()
    }

    suspend fun respondStream(value: StreamConsumer) {
        // note: it is very important to resend it here rather than just handle right here
        // because we need compression, ranges and etc to work properly
        val pipe = OutputStreamChannel()
        launch(CommonPool) {
            value.stream(pipe)
            pipe.close()
        }

        val pipeContent = object : FinalContent.ChannelContent() {
            override val headers: ValuesMap get() = value.headers
            override fun channel(): ReadChannel = pipe
        }
        respond(pipeContent)
    }

    protected fun commitHeaders(o: HostResponse) {
        o.status?.let { response.status(it) } ?: response.status() ?: response.status(HttpStatusCode.OK)
        for ((name, values) in o.headers.entries()) {
            for (value in values) {
                response.header(name, value)
            }
        }
    }

    open suspend fun respondFinalContent(content: FinalContent) {
        commitHeaders(content)
        return when (content) {
            is FinalContent.ChannelContent -> respondFromChannel(content.channel())
            is FinalContent.StreamContentProvider -> respondFromChannel(content.stream().toReadChannel())
            is FinalContent.NoContent -> {
            }
        }
    }

    protected suspend fun respondFromChannel(channel: ReadChannel) {
        // note: it is important to open response channel before we open content channel
        // otherwise we can hit deadlock on event-based hosts

        val response = responseChannel()
        channel.copyTo(response, bufferPool = bufferPool)
        channel.close()
    }

    protected abstract fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade)
    protected abstract fun responseChannel(): WriteChannel
    protected open val bufferPool: ByteBufferPool get() = NoPool


    override val parameters: ValuesMap by lazy { request.queryParameters + request.content.get() }
}