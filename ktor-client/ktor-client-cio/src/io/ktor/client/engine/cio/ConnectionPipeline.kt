package io.ktor.client.engine.cio

import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.io.*
import java.io.*
import java.net.*
import java.nio.channels.*
import java.util.*

internal class ConnectorRequestTask(
        val request: CIOHttpRequest,
        val content: OutgoingContent,
        val continuation: CancellableContinuation<CIOHttpResponse>
)

private class ConnectorResponseTask(
        val requestTime: Date,
        val continuation: CancellableContinuation<CIOHttpResponse>,
        val call: CIOHttpRequest
)

internal class ConnectionPipeline(
        dispatcher: CoroutineDispatcher,
        keepAliveTime: Int,
        address: SocketAddress,
        tasks: Channel<ConnectorRequestTask>,
        onDone: () -> Unit
) {
    private lateinit var socket: Socket
    private val inputChannel by lazy { socket.openReadChannel() }
    private val outputChannel by lazy { socket.openWriteChannel() }

    val pipelineContext: Job = launch(dispatcher) {
        try {
            socket = aSocket().tcpNoDelay().tcp().connect(address)
            while (true) {
                val task = withTimeout(keepAliveTime) {
                    tasks.receive()
                }
                onDone()

                try {
                    responseHandler.send(ConnectorResponseTask(Date(), task.continuation, task.request))
                } catch (cause: Throwable) {
                    task.continuation.resumeWithException(cause)
                    throw cause
                }

                task.request.write(outputChannel, task.content)
            }
        } catch (cause: ClosedChannelException) {
        } catch (cause: ClosedReceiveChannelException) {
        } finally {
            responseHandler.close()
            outputChannel.close()
            socket.close()
        }
    }

    private val responseHandler = actor<ConnectorResponseTask>(dispatcher, capacity = Channel.UNLIMITED) {
        for (task in channel) {
            val job: Job? = try {
                val response = parseResponse(inputChannel)
                        ?: throw EOFException("Failed to parse HTTP response: unexpected EOF")
                val contentLength = response.headers[HttpHeaders.ContentLength]?.toString()?.toLong() ?: -1L
                val transferEncoding = response.headers[HttpHeaders.TransferEncoding]
                val connectionType = ConnectionOptions.parse(response.headers[HttpHeaders.Connection])

                val writerJob = writer(Unconfined, autoFlush = true) {
                    parseHttpBody(contentLength, transferEncoding, connectionType, inputChannel, channel)
                }

                task.continuation.resume(CIOHttpResponse(task.call, task.requestTime, writerJob.channel, response))

                writerJob
            } catch (cause: ClosedChannelException) {
                null
            } catch (cause: Throwable) {
                task.continuation.resumeWithException(cause)
                null
            }

            job?.join()
        }
    }
}
