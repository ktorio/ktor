package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.channels.Channel
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import java.nio.*
import java.nio.charset.*
import java.time.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*

fun ponger(ctx: CoroutineContext, ws: WebSocketSession, pool: ByteBufferPool): ActorJob<Frame.Ping> {
    return actor(ctx, 5, CoroutineStart.LAZY) {
        consumeEach { frame ->
            val message = frame.buffer.copy(pool)
            ws.send(Frame.Pong(message.buffer, object : DisposableHandle {
                override fun dispose() {
                    pool.release(message)
                }
            }))
        }
    }
}

fun pinger(ctx: CoroutineContext, ws: WebSocketSession, period: Duration, timeout: Duration, pool: ByteBufferPool, out: SendChannel<Frame>): ActorJob<Frame.Pong> {
    val periodMillis = period.toMillis()
    val timeoutMillis = timeout.toMillis()
    val encoder = Charsets.ISO_8859_1.newEncoder()

    val t = pool.allocate(128)

    val j = actor<Frame.Pong>(ctx, Channel.UNLIMITED, CoroutineStart.LAZY) {
        while (!isClosedForReceive) {
            // drop pongs during period delay as they are irrelevant
            // here timeout is expected so ignore it
            withTimeoutOrNull(periodMillis, TimeUnit.MILLISECONDS) {
                while (true) {
                    receive() // timeout causes loop to break on receive
                }
            }

            val pingMessage = "[ping ${nextNonce()} ping]"

            val rc = withTimeoutOrNull(timeoutMillis, TimeUnit.MILLISECONDS) {
                ws.sendPing(t.buffer, encoder, pingMessage)

                // wait for valid pong message
                while (true) {
                    val msg = receive()
                    if (msg.buffer.getString(Charsets.ISO_8859_1) == pingMessage) break
                }
            }

            if (rc == null) {
                // timeout
                // we were unable to send ping or hadn't get valid pong message in time
                // so we are triggering close sequence (if already started then the following close frame could be ignored)

                val closeFrame = Frame.Close(CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, "Ping timeout"))
                out.send(closeFrame)
                break
            }
        }
    }

    j.invokeOnCompletion {
        pool.release(t)
    }

    return j
}

private suspend fun WebSocketSession.sendPing(buffer: ByteBuffer, e: CharsetEncoder, content: String) {
    with(buffer) {
        clear()
        e.reset()

        e.encode(CharBuffer.wrap(content), this, true).apply {
            if (this.isError) throwException()
            else if (this.isOverflow) throwException()
        }
        flip()

        send(Frame.Ping(this))
    }
}
