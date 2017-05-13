package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.util.*
import java.lang.ref.*
import java.nio.*
import java.time.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class ControlFrameHandler (val parent: WebSocketImpl, val exec: ScheduledExecutorService) {
    private val closeHandlers = ArrayList<Future<*>>()
    private val pingPongFuture = AtomicReference<Future<*>?>()

    @Volatile
    private var expectedPong: String? = null
    @Volatile
    private var closeSent = false
    @Volatile
    private var closeReceived = false
    @Volatile
    private var stopped = false

    private val timeoutTask = TimeoutTask(this)

    var currentReason: CloseReason? = null
        private set

    fun sent(frame: Frame) {
        when (frame.frameType) {
            FrameType.CLOSE -> {
                closeSent = true
                closeAfterTimeout()
            }
            else -> {}
        }
    }

    suspend fun closeSentAndWritten() {
        if (closeReceived) {
            parent.terminateConnection(currentReason)
        }
    }

    suspend fun received(frame: Frame) {
        require(frame.frameType.controlFrame) { "frame should be control frame" }

        when (frame) {
            is Frame.Close -> {
                currentReason = frame.readReason()

                closeReceived = true
                if (closeSent) {
                    parent.terminateConnection(currentReason)
                } else {
                    parent.send(frame.copy())
                    closeAfterTimeout()
                }
            }
            is Frame.Ping -> {
                if (!closeSent && !closeReceived) {
                    parent.send(frame.copy())
                }
            }
            is Frame.Pong -> {
                if (!closeSent && !closeReceived) {
                    handlePong(frame)
                }
            }
            else -> {}
        }
    }

    fun schedulePingPong(pingPeriod: Duration) {
        if (!closeSent && !closeReceived && !stopped) {
            pingPongFuture.getAndSet(exec.scheduleAtFixedRate({
                if (closeReceived || closeSent) {
                    cancelPingPong()
                } else {
                    runBlocking(Unconfined) {
                        doPing()
                    }
                }
            }, pingPeriod.toMillis(), pingPeriod.toMillis(), TimeUnit.MILLISECONDS))?.cancel(false)
        } else {
            cancelPingPong()
        }
    }

    fun cancelPingPong() {
        pingPongFuture.getAndSet(null)?.cancel(true)
        expectedPong = null
    }

    fun stop() {
        stopped = true
        cancelAllTimeouts()
        cancelPingPong()
    }

    private fun generatePingMessage() = "[ping ${nextNonce()} ping]"

    private suspend fun doPing() {
        val ping = generatePingMessage()
        expectedPong = ping

        closeAfterTimeout()
        parent.send(Frame.Ping(ByteBuffer.wrap(ping.toByteArray(Charsets.UTF_8))))
    }

    private fun handlePong(frame: Frame) {
        require(frame.frameType == FrameType.PONG)

        if (frame.buffer.getString() == expectedPong) {
            expectedPong = null
            cancelAllTimeouts()
        }
    }

    private fun handleTimeout() {
        runBlocking(Unconfined) {
            parent.terminateConnection(currentReason)
        }
    }

    fun closeAfterTimeout() {
        if (!stopped) {
            val f = exec.schedule(timeoutTask, parent.timeout.toMillis(), TimeUnit.MILLISECONDS)

            synchronized(closeHandlers) {
                closeHandlers.add(f)
            }
        }
    }

    tailrec fun cancelAllTimeouts() {
        val copy = synchronized(closeHandlers) {
            val refs = closeHandlers.toList()
            closeHandlers.clear()
            refs
        }

        if (copy.isNotEmpty()) {
            for (ref in copy) {
                ref.cancel(false)
            }
            cancelAllTimeouts()
        }
    }

    private class TimeoutTask(self: ControlFrameHandler) : Runnable {
        val ref = WeakReference(self)

        override fun run() {
            ref.get()?.handleTimeout()
        }
    }
}
