package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.response.*
import io.ktor.server.host.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import kotlin.coroutines.experimental.*

class CIOApplicationResponse(call: ApplicationCall,
                             private val output: ByteWriteChannel,
                             private val input: ByteReadChannel,
                             private val hostDispatcher: CoroutineContext,
                             private val appDispatcher: CoroutineContext,
                             private val upgraded: CompletableDeferred<Boolean>?) : BaseApplicationResponse(call) {
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

        val j = encodeChunked(output, hostDispatcher)
        val chunked = j.channel

        chunkedChannel = chunked
        chunkedJob = j

        return CIOWriteChannelAdapter(chunked)
    }

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        upgraded?.complete(true) ?: throw IllegalStateException("Unable to perform upgrade as it is not requested by the client: request should have Upgrade and Connection headers filled properly")

        sendResponseMessage(false)

        val upgradedJob = Job()
        upgrade.upgrade(CIOReadChannelAdapter(input), CIOWriteChannelAdapter(output), Closeable {
            output.close()
            upgradedJob.cancel()
        }, hostDispatcher, appDispatcher)

        upgradedJob.join()
    }

    suspend override fun respondFromBytes(bytes: ByteArray) {
        sendResponseMessage(false)
        output.writeFully(bytes)
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
            output.writePacket(builder.build())
            output.flush()
        } finally {
            builder.release()
        }
    }
}