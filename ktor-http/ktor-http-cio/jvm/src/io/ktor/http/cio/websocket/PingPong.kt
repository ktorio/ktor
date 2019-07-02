/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.websocket

import io.ktor.util.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import io.ktor.utils.io.pool.*
import java.nio.*
import java.nio.charset.*
import java.util.concurrent.CancellationException
import kotlin.random.*

private val PongerCoroutineName = CoroutineName("ws-ponger")

private val PingerCoroutineName = CoroutineName("ws-pinger")

@Deprecated(
    "Binary compatibility.",
    level = DeprecationLevel.HIDDEN
)
@Suppress("KDocMissingDocumentation", "unused")
fun ponger(
    session: WebSocketSession,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
): SendChannel<Frame.Ping> = session.ponger(session.outgoing, pool)

/**
 * Launch a ponger actor job on the [CoroutineScope] sending pongs to [outgoing] channel.
 * It is acting for every client's ping frame and replying with corresponding pong
 */
@UseExperimental(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
fun CoroutineScope.ponger(
    outgoing: SendChannel<Frame.Pong>,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
): SendChannel<Frame.Ping> = actor(PongerCoroutineName, capacity = 5, start = CoroutineStart.LAZY) {
    consumeEach { frame ->
        val buffer = frame.buffer.copy(pool)
        outgoing.send(Frame.Pong(buffer, object : DisposableHandle {
            override fun dispose() {
                pool.recycle(buffer)
            }
        }))
    }
}

/**
 * Launch pinger coroutine on [CoroutineScope] that is sending ping every specified [periodMillis] to [outgoing] channel,
 * waiting for and verifying client's pong frames. It is also handling [timeoutMillis] and sending timeout close frame
 */
@UseExperimental(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
fun CoroutineScope.pinger(
    outgoing: SendChannel<Frame>,
    periodMillis: Long,
    timeoutMillis: Long,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
): SendChannel<Frame.Pong> {
    val actorJob = Job()
    val result = actor<Frame.Pong>(
        actorJob + PingerCoroutineName, capacity = Channel.UNLIMITED, start = CoroutineStart.LAZY
    ) {
        // note that this coroutine need to be lazy
        val buffer = pool.borrow()
        val encoder = Charsets.ISO_8859_1.newEncoder()
        val random = Random(System.currentTimeMillis())
        val pingIdBytes = ByteArray(32)

        try {
            while (!isClosedForReceive) {
                // drop pongs during period delay as they are irrelevant
                // here timeout is expected so ignore it
                withTimeoutOrNull(periodMillis) {
                    while (true) {
                        receive() // timeout causes loop to break on receive
                    }
                }

                random.nextBytes(pingIdBytes)
                val pingMessage = "[ping ${hex(pingIdBytes)} ping]"

                val rc = withTimeoutOrNull(timeoutMillis) {
                    outgoing.sendPing(buffer, encoder, pingMessage)

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
                    outgoing.send(closeFrame)
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

    coroutineContext[Job]!!.invokeOnCompletion {
        actorJob.cancel()
    }

    return result
}

private suspend fun SendChannel<Frame.Ping>.sendPing(
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
