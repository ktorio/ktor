package io.ktor.http.cio

import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlin.coroutines.experimental.*


fun lastHttpRequest(request: Request): Boolean {
    val pre11 = !request.version.equalsLowerCase(other = "HTTP/1.1")
    val connection = request.headers["Connection"]

    return when {
        connection == null -> pre11 // connection close by default for HTTP/1.0 and HTTP/0.x
        connection.equalsLowerCase(other = "keep-alive") -> false
        connection.equalsLowerCase(other = "close") -> true
        else -> false // upgrade, etc
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
                                     handler: HttpRequestHandler) {
    val outputsActor = actor<ByteReadChannel>(ioCoroutineContext, capacity = 5) {
        try {
            consumeEach { child ->
                val copied = child.copyTo(output)
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
            val request = parseRequest(input) ?: break
            val expectedHttpBody = expectHttpBody(request)
            val expectedHttpUpgrade = !expectedHttpBody && expectHttpUpgrade(request)
            val requestBody = if (expectedHttpBody || expectedHttpUpgrade) ByteChannel(true) else EmptyByteReadChannel

            val response = ByteChannel()
            try {
                outputsActor.send(response)
            } catch (t: Throwable) {
                request.release()
                throw t
            }

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
                    parseHttpBody(request.headers, input, requestBody)
                } catch (t: Throwable) {
                    requestBody.close(t)
                } finally {
                    requestBody.close()
                }
            }

            if (lastHttpRequest(request)) break
        }
    } finally {
        outputsActor.close()
        outputsActor.join()
    }
}

