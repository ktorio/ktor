package io.ktor.client.engine.cio

import io.ktor.util.cio.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.io.*
import java.io.*
import java.nio.channels.*
import kotlin.coroutines.*

internal class ConnectionPipeline(
    keepAliveTime: Long,
    pipelineMaxSize: Int,
    socket: Socket,
    tasks: Channel<RequestTask>,
    val createCallContext: () -> CoroutineContext,
    parentContext: CoroutineContext
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = parentContext + Job()

    private val inputChannel = socket.openReadChannel()
    private val outputChannel = socket.openWriteChannel()
    private val requestLimit = Semaphore(pipelineMaxSize)
    private val responseChannel = Channel<ConnectionResponseTask>(Channel.UNLIMITED)

    val pipelineContext: Job = launch(start = CoroutineStart.LAZY) {
        try {
            while (true) {
                val task = withTimeout(keepAliveTime) {
                    tasks.receive()
                }

                val callContext = createCallContext()
                try {
                    requestLimit.enter()
                    responseChannel.send(ConnectionResponseTask(GMTDate(), task.response, task.request, callContext))
                } catch (cause: Throwable) {
                    task.response.cancel(cause)
                    throw cause
                }

                task.request.write(outputChannel, callContext)
                outputChannel.flush()
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
        socket.use {
            var shouldClose = false
            for (task in responseChannel) {
                requestLimit.leave()
                val job: Job? = try {
                    val response = parseResponse(inputChannel)
                        ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")

                    val callContext = task.callContext
                    val method = task.request.method
                    val contentLength = response.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
                    val transferEncoding = response.headers[HttpHeaders.TransferEncoding]
                    val chunked = transferEncoding == "chunked"
                    val connectionType = ConnectionOptions.parse(response.headers[HttpHeaders.Connection])
                    shouldClose = (connectionType == ConnectionOptions.Close)

                    val hasBody = (contentLength > 0 || chunked) && method != HttpMethod.Head

                    val writerJob = if (hasBody) GlobalScope.writer(
                        Dispatchers.Unconfined + callContext[Job]!!, autoFlush = true
                    ) {
                        parseHttpBody(contentLength, transferEncoding, connectionType, inputChannel, channel)
                    } else null

                    task.response.complete(
                        CIOHttpResponse(
                            task.request,
                            task.requestTime,
                            writerJob?.channel ?: ByteReadChannel.Empty,
                            response,
                            pipelined = hasBody && !chunked,
                            coroutineContext = callContext
                        )
                    )
                    writerJob
                } catch (cause: ClosedChannelException) {
                    null
                } catch (cause: Throwable) {
                    task.response.cancel(cause)
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
