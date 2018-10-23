package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import java.io.*
import kotlin.coroutines.*

@Deprecated("This is going to become private")
@Suppress("KDocMissingDocumentation")
fun lastHttpRequest(http11: Boolean, connectionOptions: ConnectionOptions?): Boolean {
    return isLastHttpRequest(http11, connectionOptions)
}

/**
 * HTTP request handler function
 */
typealias HttpRequestHandler = suspend CoroutineScope.(
    request: Request,
    input: ByteReadChannel,
    output: ByteWriteChannel,
    upgraded: CompletableDeferred<Boolean>?
) -> Unit

/**
 * HTTP pipeline coroutine name
 */
val HttpPipelineCoroutine: CoroutineName = CoroutineName("http-pipeline")

/**
 * HTTP pipeline writer coroutine name
 */
val HttpPipelineWriterCoroutine: CoroutineName = CoroutineName("http-pipeline-writer")

/**
 * HTTP request handler coroutine name
 */
val RequestHandlerCoroutine: CoroutineName = CoroutineName("request-handler")

@Suppress("KDocMissingDocumentation")
@Deprecated("Use startConnectionPipeline with CoroutineScope receiver")
fun startConnectionPipeline(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    parentJob: CoroutineContext?,
    ioContext: CoroutineContext,
    callContext: CoroutineContext,
    timeout: WeakTimeoutQueue,
    handler: HttpRequestHandler
): Job = CoroutineScope(ioContext + (parentJob ?: Dispatchers.Unconfined))
    .startConnectionPipeline(input, output, timeout) { request: Request,
                                                       _input: ByteReadChannel,
                                                       _output: ByteWriteChannel,
                                                       upgraded: CompletableDeferred<Boolean>? ->
        withContext(callContext) {
            handler(request, _input, _output, upgraded)
        }
    }

/**
 * Start connection HTTP pipeline invoking [handler] for every request.
 * Note that [handler] could be invoked multiple times concurrently due to HTTP pipeline nature
 *
 * @param input incoming channel
 * @param output outgoing bytes channel
 * @param timeout number of IDLE seconds after the connection will be closed
 * @param handler to be invoked for every incoming request
 *
 * @return pipeline job
 */
@KtorExperimentalAPI
@UseExperimental(ObsoleteCoroutinesApi::class, ExperimentalCoroutinesApi::class)
fun CoroutineScope.startConnectionPipeline(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    timeout: WeakTimeoutQueue,
    handler: HttpRequestHandler
): Job = launch(HttpPipelineCoroutine) {
    val outputsActor = actor<ByteReadChannel>(
        context = HttpPipelineWriterCoroutine,
        capacity = 3,
        start = CoroutineStart.UNDISPATCHED
    ) {
        try {
            val receiveChildOrNull = suspendLambda<CoroutineScope, ByteReadChannel?> {
                @Suppress("DEPRECATION")
                channel.receiveOrNull()
            }
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

    val requestContext = RequestHandlerCoroutine + Dispatchers.Unconfined

    try {
        while (true) {  // parse requests loop
            val request = try {
                parseRequest(input) ?: break
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
                    request.method, contentLength, transferEncoding, connectionOptions, contentType
                )
                expectedHttpUpgrade = !expectedHttpBody &&
                    expectHttpUpgrade(request.method, upgrade, connectionOptions)
            } catch (cause: Throwable) {
                request.release()
                response.writePacket(BadRequestPacket.copy())
                response.close()
                throw cause
            }

            val requestBody = if (expectedHttpBody || expectedHttpUpgrade)
                ByteChannel(true)
            else
                ByteReadChannel.Empty

            val upgraded = if (expectedHttpUpgrade) CompletableDeferred<Boolean>() else null

            launch(requestContext, start = CoroutineStart.UNDISPATCHED) {
                try {
                    handler(request, requestBody, response, upgraded)
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
                    input.copyAndClose(requestBody as ByteChannel)
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

            if (isLastHttpRequest(http11, connectionOptions)) break
        }
    } catch (cause: IOException) { // already handled
        coroutineContext.cancel()
    } finally {
        outputsActor.close()
    }
}

private val BadRequestPacket =
    RequestResponseBuilder().apply {
        responseLine("HTTP/1.0", HttpStatusCode.BadRequest.value, "Bad Request")
        headerLine("Connection", "close")
        emptyLine()
    }.build()

private fun isLastHttpRequest(http11: Boolean, connectionOptions: ConnectionOptions?): Boolean {
    return when {
        connectionOptions == null -> !http11
        connectionOptions.keepAlive -> false
        connectionOptions.close -> true
        else -> false
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun <S, R> suspendLambda(noinline block: suspend S.() -> R): suspend S.() -> R = block
