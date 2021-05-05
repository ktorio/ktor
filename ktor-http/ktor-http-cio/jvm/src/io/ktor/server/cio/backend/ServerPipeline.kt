/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio.backend

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import java.io.*

/**
 * Start connection HTTP pipeline invoking [handler] for every request.
 * Note that [handler] could be invoked multiple times concurrently due to HTTP pipeline nature
 *
 * @param connection incoming client connection info
 * @param timeout number of IDLE seconds after the connection will be closed
 * @param handler to be invoked for every incoming request
 *
 * @return pipeline job
 */
@Suppress("DEPRECATION")
@InternalAPI
public fun CoroutineScope.startServerConnectionPipeline(
    connection: ServerIncomingConnection,
    timeout: WeakTimeoutQueue,
    handler: HttpRequestHandler
): Job = launch(HttpPipelineCoroutine) {
    @OptIn(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    val outputsActor = actor<ByteReadChannel>(
        context = HttpPipelineWriterCoroutine,
        capacity = 3,
        start = CoroutineStart.UNDISPATCHED
    ) {
        try {
            pipelineWriterLoop(channel, timeout, connection)
        } catch (t: Throwable) {
            connection.output.close(t)
        } finally {
            connection.output.close()
        }
    }

    val requestContext = RequestHandlerCoroutine + Dispatchers.Unconfined

    try {
        while (true) { // parse requests loop
            val request = try {
                parseRequest(connection.input) ?: break
            } catch (io: IOException) {
                throw io
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (parseFailed: Throwable) { // try to write 400 Bad Request
                // TODO log parseFailed?
                val bc = ByteChannel()
                if (outputsActor.offer(bc)) {
                    bc.writePacket(BadRequestPacket.copy())
                    bc.close()
                }
                outputsActor.close()
                break // end pipeline loop
            }

            val response = ByteChannel()

            val transferEncoding = request.headers["Transfer-Encoding"]
            val upgrade = request.headers["Upgrade"]
            val contentType = request.headers["Content-Type"]
            val http11 = request.version == "HTTP/1.1"

            val connectionOptions: ConnectionOptions?
            val contentLength: Long
            val expectedHttpBody: Boolean
            val expectedHttpUpgrade: Boolean

            try {
                outputsActor.send(response)
            } catch (cause: Throwable) {
                request.release()
                throw cause
            }

            try {
                val contentLengthIndex = request.headers.find("Content-Length")
                connectionOptions = ConnectionOptions.parse(request.headers["Connection"])

                if (contentLengthIndex != -1) {
                    contentLength = request.headers.valueAt(contentLengthIndex).parseDecLong()
                    if (request.headers.find("Content-Length", contentLengthIndex + 1) != -1) {
                        throw ParserException("Duplicate Content-Length header")
                    }
                } else {
                    contentLength = -1
                }
                expectedHttpBody = expectHttpBody(
                    request.method,
                    contentLength,
                    transferEncoding,
                    connectionOptions,
                    contentType
                )
                expectedHttpUpgrade = !expectedHttpBody && expectHttpUpgrade(request.method, upgrade, connectionOptions)
            } catch (cause: Throwable) {
                request.release()
                response.writePacket(BadRequestPacket.copy())
                response.close()
                break
            }

            val requestBody = if (expectedHttpBody || expectedHttpUpgrade) {
                ByteChannel(true)
            } else {
                ByteReadChannel.Empty
            }

            val upgraded = if (expectedHttpUpgrade) CompletableDeferred<Boolean>() else null

            @OptIn(ExperimentalCoroutinesApi::class)
            launch(requestContext, start = CoroutineStart.UNDISPATCHED) {
                val handlerScope = ServerRequestScope(
                    coroutineContext,
                    requestBody,
                    response,
                    connection.remoteAddress,
                    connection.localAddress,
                    upgraded
                )

                try {
                    handler(handlerScope, request)
                } catch (cause: Throwable) {
                    response.close(cause)
                    upgraded?.completeExceptionally(cause)
                } finally {
                    response.close()
                    upgraded?.complete(false)
                }
            }

            if (upgraded != null) {
                if (upgraded.await()) { // suspend pipeline until we know if upgrade performed?
                    outputsActor.close()
                    connection.input.copyAndClose(requestBody as ByteChannel)
                    break
                } else if (!expectedHttpBody && requestBody is ByteChannel) { // not upgraded, for example 404
                    requestBody.close()
                }
            }

            if (expectedHttpBody && requestBody is ByteWriteChannel) {
                try {
                    parseHttpBody(
                        contentLength,
                        transferEncoding,
                        connectionOptions,
                        connection.input,
                        requestBody
                    )
                } catch (cause: Throwable) {
                    requestBody.close(ChannelReadException("Failed to read request body", cause))
                    response.writePacket(BadRequestPacket.copy())
                    response.close()
                    break
                } finally {
                    requestBody.close()
                }
            }

            if (isLastHttpRequest(http11, connectionOptions)) break
        }
    } catch (cause: IOException) { // already handled
        coroutineContext.cancel()
    } finally {
        outputsActor.close()
    }
}

private suspend fun pipelineWriterLoop(
    channel: ReceiveChannel<ByteReadChannel>,
    timeout: WeakTimeoutQueue,
    connection: ServerIncomingConnection
) {
    val receiveChildOrNull =
        suspendLambda<CoroutineScope, ByteReadChannel?> {
            @OptIn(ExperimentalCoroutinesApi::class)
            channel.receiveOrNull()
        }
    while (true) {
        val child = timeout.withTimeout(receiveChildOrNull) ?: break
        try {
            child.joinTo(connection.output, false)
            connection.output.flush()
        } catch (t: Throwable) {
            if (child is ByteWriteChannel) {
                child.close(t)
            }
        }
    }
}

private val BadRequestPacket =
    RequestResponseBuilder().apply {
        responseLine("HTTP/1.0", HttpStatusCode.BadRequest.value, "Bad Request")
        headerLine("Connection", "close")
        emptyLine()
    }.build()

internal fun isLastHttpRequest(http11: Boolean, connectionOptions: ConnectionOptions?): Boolean {
    return when {
        connectionOptions == null -> !http11
        connectionOptions.keepAlive -> false
        connectionOptions.close -> true
        else -> false
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun <S, R> suspendLambda(noinline block: suspend S.() -> R): suspend S.() -> R = block
