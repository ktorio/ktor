package io.ktor.client.engine.cio

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import io.ktor.util.cio.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import kotlinx.io.pool.*
import java.nio.channels.*
import kotlin.coroutines.*

internal class ConnectionPipeline(
    keepAliveTime: Long,
    pipelineMaxSize: Int,
    socket: Socket,
    tasks: Channel<RequestTask>,
    parentContext: CoroutineContext
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = parentContext + Job()

    private val networkInput = socket.openReadChannel()
    private val networkOutput = socket.openWriteChannel()
    private val requestLimit = Semaphore(pipelineMaxSize)
    private val responseChannel = Channel<ConnectionResponseTask>(Channel.UNLIMITED)

    val pipelineContext: Job = launch(start = CoroutineStart.LAZY) {
        try {
            while (true) {
                val task = withTimeout(keepAliveTime) {
                    tasks.receive()
                }

                try {
                    requestLimit.enter()
                    responseChannel.send(ConnectionResponseTask(GMTDate(), task))
                } catch (cause: Throwable) {
                    task.response.completeExceptionally(cause)
                    throw cause
                }

                task.request.write(networkOutput, task.context)
                networkOutput.flush()
            }
        } catch (_: ClosedChannelException) {
        } catch (_: ClosedReceiveChannelException) {
        } catch (_: CancellationException) {
        } finally {
            responseChannel.close()
            /**
             * Workaround bug with socket.close
             */
//            outputChannel.close()
        }
    }

    private val responseHandler = launch(start = CoroutineStart.LAZY) {
        try {
            var shouldClose = false
            for ((requestTime, task) in responseChannel) {
                requestLimit.leave()
                try {
                    val rawResponse = parseResponse(networkInput)
                        ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")

                    val callContext = task.context

                    val method = task.request.method
                    val contentLength = rawResponse.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
                    val transferEncoding = rawResponse.headers[HttpHeaders.TransferEncoding]
                    val chunked = transferEncoding == "chunked"
                    val connectionType = ConnectionOptions.parse(rawResponse.headers[HttpHeaders.Connection])

                    shouldClose = (connectionType == ConnectionOptions.Close)

                    val hasBody = (contentLength > 0 || chunked) && method != HttpMethod.Head
                    val responseChannel = if (hasBody) ByteChannel() else null

                    var skipTask: Job? = null
                    val body: ByteReadChannel = if (responseChannel != null) {
                        val proxyChannel = ByteChannel()
                        skipTask = skipCancels(responseChannel, proxyChannel)
                        proxyChannel
                    } else ByteReadChannel.Empty

                    val response = CIOHttpResponse(
                        task.request, requestTime,
                        body,
                        rawResponse,
                        coroutineContext = callContext
                    )

                    task.response.complete(response)

                    responseChannel?.use {
                        try {
                            parseHttpBody(
                                contentLength,
                                transferEncoding,
                                connectionType,
                                networkInput,
                                this
                            )
                        } finally {
                            callContext[Job]?.invokeOnCompletion {
                                rawResponse.release()
                            }
                        }
                    }

                    skipTask?.join()
                } catch (cause: Throwable) {
                    task.response.completeExceptionally(cause)
                }

                task.context[Job]?.join()

                if (shouldClose) break
            }
        } finally {
            networkOutput.close()
            socket.close()
        }
    }

    init {
        pipelineContext.start()
        responseHandler.start()
    }
}

private fun CoroutineScope.skipCancels(
    input: ByteReadChannel,
    output: ByteWriteChannel
): Job = launch {
    try {
        HttpClientDefaultPool.useInstance { buffer ->
            while (true) {
                buffer.clear()

                val count = input.readAvailable(buffer)
                if (count < 0) break

                buffer.flip()
                try {
                    output.writeFully(buffer)
                } catch (_: Throwable) {
                    // Output channel has been canceled, discard remaining
                    input.discard()
                }
            }
        }
    } catch (cause: Throwable) {
        output.close(cause)
        throw cause
    } finally {
        output.close()
    }
}
