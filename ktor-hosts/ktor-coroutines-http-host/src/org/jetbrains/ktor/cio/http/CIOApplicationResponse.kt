package org.jetbrains.ktor.cio.http

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.http.*
import kotlinx.sockets.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import java.io.Closeable
import java.nio.ByteBuffer
import kotlin.coroutines.experimental.*

class CIOApplicationResponse(call: ApplicationCall,
                             private val output: ByteWriteChannel,
                             private val input: ByteReadChannel,
                             private val hostDispatcher: CoroutineContext,
                             private val appDispatcher: CoroutineContext) : BaseApplicationResponse(call) {
    private var statusCode: HttpStatusCode = HttpStatusCode.OK
    private val headersNames = ArrayList<String>()
    private val headerValues = ArrayList<String>()

    @Volatile
    private var chunkedChannel: ByteWriteChannel? = null

    @Volatile
    private var chunkedJob: Job? = null

    override val headers = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            headersNames.add(name)
            headerValues.add(value)
        }

        override fun getHostHeaderNames(): List<String> {
            return headersNames
        }

        override fun getHostHeaderValues(name: String): List<String> = headersNames.indices
                .filter { headersNames[it] == name }
                .map { headerValues[it] }
    }

    suspend override fun responseChannel(): WriteChannel {
        sendResponseMessage(true)

        val chunked = ByteChannel()
        val j = launch(ioCoroutineDispatcher) {
            try {
                encodeChunked(chunked, output)
            } catch (t: Throwable) {
                output.close(t)
                chunked.close(t)
            } finally {
                output.close()
            }
        }

        chunkedChannel = chunked
        chunkedJob = j

        return WriteChannelAdapter(chunked)
    }

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        sendResponseMessage(false)

        upgrade.upgrade(ReadChannelAdapter(input), WriteChannelAdapter(output), Closeable {
            output.close()
        }, hostDispatcher, appDispatcher)
    }

    suspend override fun respondFromBytes(bytes: ByteArray) {
        sendResponseMessage(false)
        output.writeFully(bytes)
        output.flush()
        output.close()
    }

    suspend override fun respondFinalContent(content: FinalContent) {
        super.respondFinalContent(content)
        if (content is FinalContent.NoContent) {
            sendResponseMessage(false)
            output.close()
            return
        }

        chunkedChannel?.close()
        chunkedJob?.join()
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        this.statusCode = statusCode
    }

    private suspend fun sendResponseMessage(chunked: Boolean) {
        val builder = RequestResponseBuilder()
        try  {
            builder.responseLine("HTTP/1.1", statusCode.value, statusCode.description)
            for (i in 0 until headersNames.size) {
                builder.headerLine(headersNames[i], headerValues[i])
            }
            if (chunked) {
                if ("Transfer-Encoding" !in headersNames) {
                    builder.headerLine("Transfer-Encoding", "chunked")
                }
            }
            builder.emptyLine()
            builder.writeTo(output)
            output.flush()
        } finally {
            builder.release()
        }
    }
}