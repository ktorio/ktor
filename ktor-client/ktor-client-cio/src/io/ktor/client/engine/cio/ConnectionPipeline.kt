package io.ktor.client.engine.cio

import io.ktor.client.cio.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.nio.channels.*
import java.util.*

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
                    responseChannel.send(ConnectionResponseTask(Date(), task.continuation, task.request))
                } catch (cause: Throwable) {
                    task.continuation.resumeWithException(cause)
                    throw cause
                }

                task.request.write(outputChannel)
                outputChannel.flush()
            }
        } catch (cause: ClosedChannelException) {
        } catch (cause: ClosedReceiveChannelException) {
        } finally {
            responseChannel.close()
            outputChannel.close()
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
                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
                    val transferEncoding = response.headers[HttpHeaders.TransferEncoding]
                    val connectionType = ConnectionOptions.parse(response.headers[HttpHeaders.Connection])
                    shouldClose = connectionType == ConnectionOptions.Close

                    val writerJob = writer(Unconfined, autoFlush = true) {
                        parseHttpBody(contentLength, transferEncoding, connectionType, inputChannel, channel)
                    }

                    task.continuation.resume(
                        CIOHttpResponse(
                            task.request,
                            task.requestTime,
                            writerJob.channel,
                            response
                        )
                    )
                    writerJob
                } catch (cause: ClosedChannelException) {
                    null
                } catch (cause: Throwable) {
                    task.continuation.resumeWithException(cause)
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
