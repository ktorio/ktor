/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio.backend

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import io.ktor.server.cio.*
import io.ktor.util.cio.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.io.IOException
import kotlinx.io.InternalIoApi
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlin.time.Duration

private val LOGGER = KtorSimpleLogger("io.ktor.server.cio.backend.ServerPipeline")

/**
 * Start connection HTTP pipeline invoking [handler] for every request.
 * Note that [handler] could be invoked multiple times concurrently due to HTTP pipeline nature
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.backend.startServerConnectionPipeline)
 *
 * @param connection incoming client connection info
 * @param timeout number of IDLE seconds after the connection will be closed
 * @param handler to be invoked for every incoming request
 *
 * @return pipeline job
 */
@Suppress("DEPRECATION_ERROR", "DEPRECATION")
@InternalAPI
public fun CoroutineScope.startServerConnectionPipeline(
    connection: ServerIncomingConnection,
    timeout: Duration,
    handler: HttpRequestHandler
): Job = launch(HttpPipelineCoroutine) {
    val actorChannel = Channel<ByteReadChannel>(capacity = 3)

    launch(
        context = HttpPipelineWriterCoroutine,
        start = CoroutineStart.UNDISPATCHED
    ) {
        try {
            pipelineWriterLoop(actorChannel, timeout, connection)
        } catch (cause: Throwable) {
            connection.output.close(cause)
        } finally {
            connection.output.close()
        }
    }

    val requestContext = RequestHandlerCoroutine + Dispatchers.Unconfined

    var handlerScope: ServerRequestScope? = null
    try {
        while (true) { // parse requests loop
            val request = try {
                parseRequest(connection.input) ?: break
            } catch (e: TooLongLineException) {
                respondBadRequest(actorChannel, e.message)
                break // end pipeline loop
            } catch (io: IOException) {
                throw io
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (parseFailed: Throwable) {
                LOGGER.trace { "Request parsing failed with exception: $parseFailed" }
                // try to write 400 Bad Request
                respondBadRequest(actorChannel, parseFailed.message)
                break // end pipeline loop
            }

            val response = ByteChannel()

            val transferEncoding = request.headers["Transfer-Encoding"]
            val upgrade = request.headers["Upgrade"]
            val contentType = request.headers["Content-Type"]
            val version = HttpProtocolVersion.parse(request.version)

            val connectionOptions: ConnectionOptions?
            val contentLength: Long
            val expectedHttpBody: Boolean
            val expectedHttpUpgrade: Boolean

            try {
                actorChannel.send(response)
            } catch (cause: Throwable) {
                request.release()
                throw cause
            }

            try {
                val contentLengthHeaders = request.headers.getAll("Content-Length").toList()
                connectionOptions = ConnectionOptions.parse(request.headers["Connection"])
                if (contentLengthHeaders.size > 1) {
                    throw ParserException("Duplicate Content-Length header")
                }

                contentLength = if (contentLengthHeaders.size == 1) contentLengthHeaders[0].parseDecLong() else -1

                expectedHttpBody = expectHttpBody(
                    request.method,
                    contentLength,
                    transferEncoding,
                    connectionOptions,
                    contentType
                )
                expectedHttpUpgrade = !expectedHttpBody && expectHttpUpgrade(request.method, upgrade, connectionOptions)
            } catch (e: Throwable) {
                request.release()
                response.writePacket(e.message.toBadRequestPacket())
                response.close()
                break
            }

            val requestBody = if (expectedHttpBody || expectedHttpUpgrade) {
                ByteChannel(true)
            } else {
                ByteReadChannel.Empty
            }

            val upgraded = if (expectedHttpUpgrade) CompletableDeferred<Boolean>() else null

            launch(requestContext, start = CoroutineStart.UNDISPATCHED) {
                handlerScope = ServerRequestScope(
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
                if (upgraded.await()) { // suspend a pipeline until we know if the upgrade performed?
                    actorChannel.close()
                    connection.input.copyAndClose(requestBody as ByteChannel)
                    break
                } else if (requestBody is ByteChannel) { // not upgraded, for example, 404
                    requestBody.close()
                }
            }

            if (expectedHttpBody && requestBody is ByteWriteChannel) {
                try {
                    parseHttpBody(
                        version,
                        contentLength,
                        transferEncoding,
                        connectionOptions,
                        connection.input,
                        requestBody
                    )
                } catch (cause: Throwable) {
                    requestBody.close(ChannelReadException("Failed to read request body", cause))
                    response.writePacket(cause.message.toBadRequestPacket())
                    response.close()
                    break
                } finally {
                    requestBody.close()
                }
            }

            if (isLastHttpRequest(version, connectionOptions)) break
        }
    } catch (_: IOException) {
        // already handled
        coroutineContext.cancel()
    } finally {
        handlerScope?.onClose?.invoke()
        actorChannel.close()
    }
}

@OptIn(InternalIoApi::class)
private fun String?.toBadRequestPacket(): Source {
    if (this == null) {
        return BadRequestPacket.buffer.copy()
    }
    val bytes = encodeToByteArray()
    return RequestResponseBuilder().apply {
        bytes(BadRequestPacketCommon)
        headerLine("Content-Length", bytes.size.toString())
        emptyLine()
        bytes(bytes)
    }.build()
}

private suspend fun respondBadRequest(actorChannel: Channel<ByteReadChannel>, message: String?) {
    val bc = ByteChannel()
    if (actorChannel.trySend(bc).isSuccess) {
        bc.writePacket(message.toBadRequestPacket())
        bc.close()
    }
    actorChannel.close()
}

@OptIn(InternalAPI::class)
private suspend fun pipelineWriterLoop(
    channel: ReceiveChannel<ByteReadChannel>,
    timeout: Duration,
    connection: ServerIncomingConnection
) {
    while (true) {
        val child = withTimeoutOrNull(timeout) {
            channel.receiveCatching().getOrNull()
        } ?: break
        try {
            child.copyTo(connection.output)
            connection.output.flush()
        } catch (cause: Throwable) {
            if (child is ByteWriteChannel) {
                child.close(cause)
            }
        }
    }
}

@OptIn(InternalIoApi::class)
private val BadRequestPacketCommon = RequestResponseBuilder().apply {
    responseLine("HTTP/1.0", HttpStatusCode.BadRequest.value, "Bad Request")
    headerLine("Connection", "close")
    headerLine("Content-Type", "text/plain; charset=utf-8")
}.build().buffer.readByteArray()

private val BadRequestPacket = RequestResponseBuilder().apply {
    responseLine("HTTP/1.0", HttpStatusCode.BadRequest.value, "Bad Request")
    headerLine("Connection", "close")
    emptyLine()
}.build()

internal fun isLastHttpRequest(version: HttpProtocolVersion, connectionOptions: ConnectionOptions?): Boolean {
    return when {
        connectionOptions == null && version == HttpProtocolVersion.HTTP_1_0 -> true
        connectionOptions == null -> version != HttpProtocolVersion.HTTP_1_1
        connectionOptions.keepAlive -> false
        connectionOptions.close -> true
        else -> false
    }
}
