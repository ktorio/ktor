package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
import kotlin.coroutines.experimental.*

fun lastHttpRequest(http11: Boolean, connectionOptions: ConnectionOptions?): Boolean {
    return when {
        connectionOptions == null -> !http11
        connectionOptions.keepAlive -> false
        connectionOptions.close -> true
        else -> false
    }
}

typealias HttpRequestHandler = suspend (request: Request,
                                        input: ByteReadChannel,
                                        output: ByteWriteChannel,
                                        upgraded: CompletableDeferred<Boolean>?) -> Unit

val HttpPipelineCoroutine = CoroutineName("http-pipeline")
val HttpPipelineWriterCoroutine = CoroutineName("http-pipeline-writer")

fun startConnectionPipeline(input: ByteReadChannel,
                            output: ByteWriteChannel,
                            parentJob: CoroutineContext?,
                            ioContext: CoroutineContext,
                            callContext: CoroutineContext,
                            timeout: WeakTimeoutQueue,
                            handler: HttpRequestHandler): Job {

    return launch(ioContext + HttpPipelineCoroutine + (parentJob ?: EmptyCoroutineContext)) {
        val outputsActor = actor<ByteReadChannel>(
                context = coroutineContext + HttpPipelineWriterCoroutine,
                capacity = 3,
                start = CoroutineStart.UNDISPATCHED) {
            try {
                val receiveChildOrNull = suspendLambda<CoroutineScope, ByteReadChannel?> { channel.receiveOrNull() }
                while (true) {
                    val child = timeout.withTimeout(receiveChildOrNull) ?: break
                    try {
                        child.joinTo(output, false)
//                        child.copyTo(output)
                        output.flush()
                    } catch (t: Throwable) {
                        if (child is ByteWriteChannel) {
                            child.close(t)
                        }
                    }
                }
            } catch (t: Throwable) {
                output.close(t)
            } finally {
                output.close()
            }
        }

        val thisJob = coroutineContext[Job]!!

        try {
            while (true) {
                val request = try {
                    parseRequest(input) ?: break
                } catch (io: IOException) {
                    throw io
                } catch (cancelled: CancellationException) {
                    throw cancelled
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

                val connectionOptions: ConnectionOptions?
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
                    connectionOptions = ConnectionOptions.parse(request.headers["Connection"])
                    contentLength = request.headers["Content-Length"]?.parseDecLong() ?: -1
                    expectedHttpBody = expectHttpBody(request.method, contentLength, transferEncoding, connectionOptions, contentType)
                    expectedHttpUpgrade = !expectedHttpBody && expectHttpUpgrade(request.method, upgrade, connectionOptions)
                } catch (t: Throwable) {
                    request.release()
                    response.writePacket(BadRequestPacket.copy())
                    response.close()
                    throw t
                }

                val requestBody = if (expectedHttpBody || expectedHttpUpgrade) ByteChannel(true) else EmptyByteReadChannel
                val upgraded = if (expectedHttpUpgrade) CompletableDeferred<Boolean>() else null

                launch(callContext + thisJob + CoroutineName("request-handler")) {
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
                        parseHttpBody(contentLength, transferEncoding, connectionOptions, input, requestBody)
                    } catch (cause: Throwable) {
                        requestBody.close(cause)
                        throw cause
                    } finally {
                        requestBody.close()
                    }
                }

                if (lastHttpRequest(http11, connectionOptions)) break
            }
        } catch (t: IOException) { // already handled
            coroutineContext.cancel()
        } catch (t: Throwable) {
            coroutineContext.cancel(t)
        } finally {
            outputsActor.close()
        }
    }
}

@Deprecated("Use startConnectionPipeline instead",
        ReplaceWith("startConnectionPipeline(input, output, ioCoroutineContext, callContext, timeouts, handler).join()"))
suspend fun handleConnectionPipeline(input: ByteReadChannel,
                                     output: ByteWriteChannel,
                                     ioCoroutineContext: CoroutineContext,
                                     callContext: CoroutineContext,
                                     timeouts: WeakTimeoutQueue,
                                     handler: HttpRequestHandler) {
    startConnectionPipeline(input, output, null, ioCoroutineContext, callContext, timeouts, handler).join()
}

private val BadRequestPacket =
        RequestResponseBuilder().apply {
            responseLine("HTTP/1.0", HttpStatusCode.BadRequest.value, "Bad Request")
            headerLine("Connection", "close")
            emptyLine()
        }.build()

@Suppress("NOTHING_TO_INLINE")
private inline fun <S, R> suspendLambda(noinline block: suspend S.() -> R): suspend S.() -> R = block
