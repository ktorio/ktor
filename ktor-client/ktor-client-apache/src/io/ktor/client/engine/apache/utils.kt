package io.ktor.client.engine.apache

import io.ktor.util.*
import kotlinx.coroutines.experimental.channels.*
import org.apache.http.*
import org.apache.http.concurrent.*
import org.apache.http.nio.*
import org.apache.http.nio.client.methods.*
import org.apache.http.nio.protocol.*
import org.apache.http.protocol.*
import java.nio.*
import kotlin.coroutines.experimental.*


internal suspend fun suspendRequest(
        data: Channel<ByteBuffer>,
        block: (HttpAsyncResponseConsumer<Unit>, FutureCallback<Unit>) -> Unit
): HttpResponse {
    return suspendCoroutine { continuation ->
        val consumer = object : AsyncByteConsumer<Unit>() {
            override fun buildResult(context: HttpContext) {
                data.close()
            }

            override fun onByteReceived(buffer: ByteBuffer, io: IOControl) {
                val content = buffer.copy()
                if (content.remaining() > 0 && !data.offer(content)) {
                    throw IllegalStateException("data.offer() failed")
                }
            }

            override fun onResponseReceived(response: HttpResponse) {
                continuation.resume(response)
            }
        }

        val callback = object : FutureCallback<Unit> {
            override fun failed(exception: Exception) {
                continuation.resumeWithException(exception)
            }

            override fun completed(result: Unit) {}

            override fun cancelled() {}
        }

        block(consumer, callback)
    }
}
