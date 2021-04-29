/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal class CIOApplicationResponse(
    call: CIOApplicationCall,
    private val output: ByteWriteChannel,
    private val input: ByteReadChannel,
    private val engineDispatcher: CoroutineContext,
    private val userDispatcher: CoroutineContext,
    private val upgraded: CompletableDeferred<Boolean>?
) : BaseApplicationResponse(call) {
    private var statusCode: HttpStatusCode = HttpStatusCode.OK
    private val headersNames = ArrayList<String>()
    private val headerValues = ArrayList<String>()

    @Volatile
    private var chunkedChannel: ByteWriteChannel? = null

    @Volatile
    private var chunkedJob: Job? = null

    override val headers = object : ResponseHeaders() {
        override fun engineAppendHeader(name: String, value: String) {
            headersNames.add(name)
            headerValues.add(value)
        }

        override fun getEngineHeaderNames(): List<String> {
            return headersNames
        }

        override fun getEngineHeaderValues(name: String): List<String> {
            val names = headersNames
            val values = headerValues
            val size = headersNames.size
            var firstIndex = -1

            for (i in 0 until size) {
                if (names[i].equals(name, ignoreCase = true)) {
                    firstIndex = i
                    break
                }
            }

            if (firstIndex == -1) return emptyList()

            var secondIndex = -1
            for (i in firstIndex until size) {
                if (names[i].equals(name, ignoreCase = true)) {
                    secondIndex = i
                    break
                }
            }

            if (secondIndex == -1) return listOf(values[firstIndex])

            val result = ArrayList<String>(size - secondIndex + 1)
            result.add(values[firstIndex])
            result.add(values[secondIndex])

            for (i in secondIndex until size) {
                if (names[i].equals(name, ignoreCase = true)) {
                    result.add(values[i])
                }
            }

            return result
        }
    }

    override suspend fun responseChannel(): ByteWriteChannel {
        sendResponseMessage(false)
        return preparedBodyChannel()
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        upgraded?.complete(true) ?: throw IllegalStateException(
            "Unable to perform upgrade as it is not requested by the client: " +
                "request should have Upgrade and Connection headers filled properly"
        )

        sendResponseMessage(contentReady = false)

        val upgradedJob = upgrade.upgrade(input, output, engineDispatcher, userDispatcher)
        upgradedJob.invokeOnCompletion { output.close(); input.cancel() }
        upgradedJob.join()
    }

    override suspend fun respondFromBytes(bytes: ByteArray) {
        sendResponseMessage(contentReady = true)
        val channel = preparedBodyChannel()
        return withContext<Unit>(Dispatchers.Unconfined) {
            channel.writeFully(bytes)
            channel.close()
        }
    }

    override suspend fun respondNoContent(content: OutgoingContent.NoContent) {
        sendResponseMessage(contentReady = true)
        output.close()
    }

    override suspend fun respondOutgoingContent(content: OutgoingContent) {
        super.respondOutgoingContent(content)
        chunkedChannel?.close()
        chunkedJob?.join()
    }

    override fun setStatus(statusCode: HttpStatusCode) {
        this.statusCode = statusCode
    }

    private suspend fun sendResponseMessage(contentReady: Boolean) {
        val builder = RequestResponseBuilder()
        try {
            builder.responseLine("HTTP/1.1", statusCode.value, statusCode.description)
            for (i in 0 until headersNames.size) {
                builder.headerLine(headersNames[i], headerValues[i])
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

    private suspend fun preparedBodyChannel(): ByteWriteChannel {
        val chunked = headers[HttpHeaders.TransferEncoding] == "chunked"
        if (!chunked) return output

        val encoderJob = encodeChunked(output, Dispatchers.Unconfined)
        val chunkedOutput = encoderJob.channel

        chunkedChannel = chunkedOutput
        chunkedJob = encoderJob

        return chunkedOutput
    }
}
