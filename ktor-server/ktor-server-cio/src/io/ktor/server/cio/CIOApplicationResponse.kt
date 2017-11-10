package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.response.*
import io.ktor.server.engine.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import kotlin.coroutines.experimental.*

class CIOApplicationResponse(call: CIOApplicationCall,
                             private val output: ByteWriteChannel,
                             private val input: ByteReadChannel,
                             private val engineDispatcher: CoroutineContext,
                             private val userDispatcher: CoroutineContext,
                             private val upgraded: CompletableDeferred<Boolean>?) : BaseApplicationResponse(call) {
    private var statusCode: HttpStatusCode = HttpStatusCode.OK
    private val headersNames = ArrayList<String>()
    private val headerValues = ArrayList<String>()

    @Volatile
    private var chunkedChannel: ByteWriteChannel? = null

    @Volatile
    private var chunkedJob: Job? = null

    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            call.release()
        }
    }

    override val headers = object : ResponseHeaders() {
        override fun engineAppendHeader(name: String, value: String) {
            headersNames.add(name)
            headerValues.add(value)
        }

        override fun getEngineHeaderNames(): List<String> {
            return headersNames
        }

        override fun getEngineHeaderValues(name: String): List<String> = headersNames.indices
                .filter { headersNames[it].equals(name, ignoreCase = true) }
                .map { headerValues[it] }
    }

    private fun hasHeader(name: String) = headersNames.any { it.equals(name, ignoreCase = true) }

    suspend override fun responseChannel(): WriteChannel {
        sendResponseMessage(true, -1, false)

        val j = encodeChunked(output, engineDispatcher)
        val chunked = j.channel

        chunkedChannel = chunked
        chunkedJob = j

        return CIOWriteChannelAdapter(chunked)
    }

    suspend override fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        upgraded?.complete(true) ?: throw IllegalStateException("Unable to perform upgrade as it is not requested by the client: request should have Upgrade and Connection headers filled properly")

        sendResponseMessage(false, -1, false)

        val upgradedJob = Job()
        upgrade.upgrade(CIOReadChannelAdapter(input), CIOWriteChannelAdapter(output), Closeable {
            output.close()
            upgradedJob.cancel()
        }, engineDispatcher, userDispatcher)

        upgradedJob.join()
    }

    suspend override fun respondFromBytes(bytes: ByteArray) {
        sendResponseMessage(false, bytes.size, true)
        output.writeFully(bytes)
        output.close()
    }

    suspend override fun respondOutgoingContent(content: OutgoingContent) {
        super.respondOutgoingContent(content)
        if (content is OutgoingContent.NoContent) {
            sendResponseMessage(false, 0, true)
            output.close()
            return
        }

        chunkedChannel?.close()
        chunkedJob?.join()
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        this.statusCode = statusCode
    }

    private suspend fun sendResponseMessage(chunked: Boolean, contentLength: Int, contentReady: Boolean) {
        val builder = RequestResponseBuilder()
        try  {
            builder.responseLine("HTTP/1.1", statusCode.value, statusCode.description)
            for (i in 0 until headersNames.size) {
                builder.headerLine(headersNames[i], headerValues[i])
            }
            if (chunked) {
                if (!hasHeader("Transfer-Encoding")) {
                    builder.headerLine("Transfer-Encoding", "chunked")
                }
            }
            if (contentLength != -1) {
                if (!hasHeader("Content-Length")) {
                    builder.headerLine("Content-Length", contentLength.toString())
                }
            }
            builder.emptyLine()
            output.writePacket(builder.build())

            if (!contentReady) {
                output.flush()
            }
        } finally {
            builder.release()
        }
    }
}