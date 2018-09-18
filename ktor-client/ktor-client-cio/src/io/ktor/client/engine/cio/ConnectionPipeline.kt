package io.ktor.client.engine.cio

import io.ktor.util.cio.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.io.*
import java.io.*
import java.nio.channels.*

internal class ConnectionPipeline(
    dispatcher: CoroutineDispatcher,
    keepAliveTime: Int,
    pipelineMaxSize: Int,
    socket: Socket,
    tasks: Channel<RequestTask>
) {
    private val inputChannel = socket.openReadChannel()
    private val outputChannel = socket.openWriteChannel()
    private val requestLimit = Semaphore(pipelineMaxSize)
    private val responseChannel = Channel<ConnectionResponseTask>(Channel.UNLIMITED)

    val pipelineContext: Job = launch(dispatcher, start = CoroutineStart.LAZY) {
        try {
            while (true) {
                val task = withTimeout(keepAliveTime) {
                    tasks.receive()
                }

                try {
                    requestLimit.enter()
                    responseChannel.send(ConnectionResponseTask(GMTDate(), task.response, task.request))
                } catch (cause: Throwable) {
                    task.response.completeExceptionally(cause)
                    throw cause
                }

                task.request.write(outputChannel)
                outputChannel.flush()
            }
        } catch (cause: ClosedChannelException) {
        } catch (cause: ClosedReceiveChannelException) {
        } finally {
            responseChannel.close()
            /**
             * Workaround bug with socket.close
             */
//            outputChannel.close()
        }
    }

    private val responseHandler = launch(dispatcher, start = CoroutineStart.LAZY) {
        socket.use {
            var shouldClose = false
            for (task in responseChannel) {
                requestLimit.leave()
                val job: Job? = try {
                    val response = parseResponse(inputChannel)
                        ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")

                    val method = task.request.method
                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
                    val transferEncoding = response.headers[HttpHeaders.TransferEncoding]
                    val chunked = transferEncoding == "chunked"
                    val connectionType = ConnectionOptions.parse(response.headers[HttpHeaders.Connection])
                    shouldClose = (connectionType == ConnectionOptions.Close)

                    val hasBody = (contentLength > 0 || chunked) && method != HttpMethod.Head

                    val writerJob = if (hasBody) writer(Unconfined, autoFlush = true) {
                        parseHttpBody(contentLength, transferEncoding, connectionType, inputChannel, channel)
                    } else null

                    task.response.complete(
                        CIOHttpResponse(
                            task.request,
                            task.requestTime,
                            writerJob?.channel ?: ByteReadChannel.Empty,
                            response,
                            pipelined = hasBody && !chunked
                        )
                    )
                    writerJob
                } catch (cause: ClosedChannelException) {
                    null
                } catch (cause: Throwable) {
                    task.response.completeExceptionally(cause)
                    null
                }

                job?.join()
                if (shouldClose) break
            }
        }
    }

    init {
        pipelineContext.start()
        responseHandler.start()
    }
}
