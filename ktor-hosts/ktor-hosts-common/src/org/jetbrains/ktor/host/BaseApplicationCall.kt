package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.nio.*

abstract class BaseApplicationCall(override val application: Application) : ApplicationCall {
    final override val attributes = Attributes()

    var responded = false
        private set

    suspend override fun respond(message: Any) {
        val phases = response.pipeline.phases
        val pipelineContext = PipelineContext(phases.interceptors(), message)
        pipelineContext.proceed()
        if (responded)
            return
        responded = true
        val value = pipelineContext.subject
        when (value) {
            is FinalContent -> {
                respondFinalContent(value)
            }
            else -> {
                application.log.warning("Response pipeline didn't finish with the FinalContent, but ended with $value")
            }
        }
        pipelineContext.finish()
    }

    protected fun commitHeaders(o: FinalContent) {
        o.status?.let { response.status(it) } ?: response.status() ?: response.status(HttpStatusCode.OK)
        for ((name, values) in o.headers.entries()) {
            for (value in values) {
                response.header(name, value)
            }
        }

        val connection = request.headers["Connection"]
        if (connection != null) {
            when {
                connection.equals("close", true) -> response.header("Connection", "close")
                connection.equals("keep-alive", true) -> response.header("Connection", "keep-alive")
            }
        }
    }

    protected open suspend fun respondFinalContent(content: FinalContent) {
        commitHeaders(content)
        return when (content) {
            is FinalContent.ProtocolUpgrade -> respondUpgrade(content)

        // ByteArrayContent is most efficient
            is FinalContent.ByteArrayContent -> respondFromBytes(content.bytes())

        // WriteChannelContent is more efficient than ReadChannelContent
            is FinalContent.WriteChannelContent -> content.writeTo(responseChannel())

        // Pipe is least efficient
            is FinalContent.ReadChannelContent -> respondFromChannel(content.readFrom())

        // Do nothing, but maintain `when` exhaustiveness
            is FinalContent.NoContent -> { /* no-op */
            }
        }
    }

    protected open suspend fun respondFromBytes(bytes: ByteArray) {
        val response = responseChannel()
        response.write(ByteBuffer.wrap(bytes))
    }

    protected open suspend fun respondFromChannel(readChannel: ReadChannel) {
        val writeChannel = responseChannel()
        readChannel.copyTo(writeChannel, bufferPool, 65536)
        readChannel.close()
        writeChannel.close()
    }

    protected abstract suspend fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade)
    protected abstract suspend fun responseChannel(): WriteChannel
    protected open val bufferPool: ByteBufferPool get() = NoPool

    override val parameters: ValuesMap get() = request.queryParameters
}