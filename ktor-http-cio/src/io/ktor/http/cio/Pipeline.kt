package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import kotlin.coroutines.experimental.*

fun lastHttpRequest(http11: Boolean, connectionType: ConnectionType?): Boolean {
    return when (connectionType) {
        null -> !http11
        ConnectionType.KeepAlive -> false
        ConnectionType.Close -> true
        else -> false
    }
}

typealias HttpRequestHandler = suspend (request: Request,
                                        input: ByteReadChannel,
                                        output: ByteWriteChannel,
                                        upgraded: CompletableDeferred<Boolean>?) -> Unit

suspend fun handleConnectionPipeline(input: ByteReadChannel,
                                     output: ByteWriteChannel,
                                     ioCoroutineContext: CoroutineContext,
                                     callDispatcher: CoroutineContext,
                                     timeouts: WeakTimeoutQueue,
                                     handler: HttpRequestHandler) {
    val outputsActor = actor<ByteReadChannel>(ioCoroutineContext, capacity = 5, start = CoroutineStart.UNDISPATCHED) {
        try {
            val receiveChildOrNull = suspendLambda<CoroutineScope, ByteReadChannel?> { channel.receiveOrNull() }
            while (true) {
                val child = timeouts.withTimeout(receiveChildOrNull) ?: break
//                child.joinTo(output, false)
                child.copyTo(output)
                output.flush()
            }
        } catch (t: Throwable) {
            output.close(t)
        } finally {
            output.close()
        }
    }

    try {
        while (true) {
            val request = try {
                parseRequest(input) ?: break
            } catch (io: IOException) {
                throw io
            } catch (t: Throwable) {
                val bc = ByteChannel()
                if (outputsActor.offer(bc)) {
                    bc.writePacket(BadRequestPacket.copy())
                    bc.close()
                }
                throw t
            }

            val response = ByteChannel()

            val transferEncoding = request.headers["Transfer-Encoding"]
            val upgrade = request.headers["Upgrade"]
            val contentType = request.headers["Content-Type"]
            val http11 = request.version == "HTTP/1.1"

            val connectionType: ConnectionType?
            val contentLength: Long
            val expectedHttpBody: Boolean
            val expectedHttpUpgrade: Boolean

            try {
                outputsActor.send(response)
            } catch (t: Throwable) {
                request.release()
                throw t
            }

            try {
                connectionType = ConnectionType.parse(request.headers["Connection"])
                contentLength = request.headers["Content-Length"]?.parseDecLong() ?: -1
                expectedHttpBody = expectHttpBody(request.method, contentLength, transferEncoding, connectionType, contentType)
                expectedHttpUpgrade = !expectedHttpBody && expectHttpUpgrade(request.method, upgrade, connectionType)
            } catch (t: Throwable) {
                request.release()
                response.writePacket(BadRequestPacket.copy())
                response.close()
                throw t
            }

            val requestBody = if (expectedHttpBody || expectedHttpUpgrade) ByteChannel(true) else EmptyByteReadChannel
            val upgraded = if (expectedHttpUpgrade) CompletableDeferred<Boolean>() else null

            launch(callDispatcher) {
                try {
                    handler(request, requestBody, response, upgraded)
                } catch (t: Throwable) {
                    response.close(t)
                    upgraded?.completeExceptionally(t)
                } finally {
                    response.close()
                    upgraded?.complete(false)
                }
            }

            if (upgraded != null) {
                if (upgraded.await()) { // suspend pipeline until we know if upgrade performed?
                    launch(Unconfined) {
                        input.copyAndClose(requestBody as ByteChannel)
                    }
                    break
                } else if (!expectedHttpBody && requestBody is ByteChannel) { // not upgraded, for example 404
                    requestBody.close()
                }
            }

            if (expectedHttpBody && requestBody is ByteWriteChannel) {
                try {
                    parseHttpBody(contentLength, transferEncoding, connectionType, input, requestBody)
                } catch (t: Throwable) {
                    requestBody.close(t)
                } finally {
                    requestBody.close()
                }
            }

            if (lastHttpRequest(http11, connectionType)) break
        }
    } finally {
        outputsActor.close()
        outputsActor.join()
    }
}

private val BadRequestPacket =
    RequestResponseBuilder().apply {
        responseLine("HTTP/1.0", HttpStatusCode.BadRequest.value, "Bad Request")
        headerLine("Connection", "close")
        emptyLine()
    }.build()

@Suppress("NOTHING_TO_INLINE")
private inline fun <S, R> suspendLambda(noinline block: suspend S.() -> R): suspend S.() -> R = block
