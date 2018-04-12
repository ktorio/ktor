package io.ktor.http.cio.websocket

import io.ktor.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.channels.Channel
import io.ktor.util.*
import kotlinx.io.pool.*
import java.nio.*
import java.nio.charset.*
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.experimental.*

/**
 * Launch a ponger actor job on the [coroutineContext] for websocket [session].
 * It is acting for every client's ping frame and replying with corresponding pong
 */
fun ponger(
    coroutineContext: CoroutineContext,
    session: WebSocketSession,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
): SendChannel<Frame.Ping> = actor(coroutineContext, 5, CoroutineStart.LAZY) {
    consumeEach { frame ->
        val buffer = frame.buffer.copy(pool)
        session.send(Frame.Pong(buffer, object : DisposableHandle {
            override fun dispose() {
                pool.recycle(buffer)
            }
        }))
    }
}

/**
 * Launch pinger coroutine on [coroutineContext] websocket for [session] that is sending ping every specified [period],
 * waiting for and verifying client's pong frames. It is also handling [timeout] and sending timeout close frame
 * to the dedicated [out] channel in case of failure
 */
fun pinger(
    coroutineContext: CoroutineContext,
    session: WebSocketSession,
    period: Duration,
    timeout: Duration,
    out: SendChannel<Frame>,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
): SendChannel<Frame.Pong> = actor(coroutineContext, Channel.UNLIMITED, CoroutineStart.LAZY) {
    val buffer = pool.borrow()
    val periodMillis = period.toMillis()
    val timeoutMillis = timeout.toMillis()
    val encoder = Charsets.ISO_8859_1.newEncoder()

    try {
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
                session.sendPing(buffer, encoder, pingMessage)

                // wait for valid pong message
                while (true) {
                    val msg = receive()
                    if (msg.buffer.decodeString(Charsets.ISO_8859_1) == pingMessage) break
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
    } catch (ignore: CancellationException) {
    } catch (ignore: ClosedReceiveChannelException) {
    } catch (ignore: ClosedSendChannelException) {
    } finally {
        pool.recycle(buffer)
    }
}

private suspend fun WebSocketSession.sendPing(
    buffer: ByteBuffer,
    encoder: CharsetEncoder,
    content: String
) = with(buffer) {
    clear()
    encoder.reset()

    encoder.encode(CharBuffer.wrap(content), this, true).apply {
        if (this.isError) throwException()
        else if (this.isOverflow) throwException()
    }
    flip()

    send(Frame.Ping(this))
}
