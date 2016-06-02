package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.util.*
import java.lang.ref.*
import java.nio.*
import java.time.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class ControlFrameHandler (val parent: WebSocketImpl, val exec: ScheduledExecutorService) {
    private var closeSent = false
    private var closeReceived = false
    private val closeHandlers = ArrayList<WeakReference<Future<*>>>()
    private val pingPongFuture = AtomicReference<Future<*>?>()
    private var expectedPong: String? = null
    var currentReason: CloseReason? = null
        private set

    fun send(frame: Frame) {
        when (frame.frameType) {
            FrameType.CLOSE -> {
                closeSent = true
                closeAfterTimeout()
            }
            else -> {}
        }
    }

    fun closeSent() {
        if (closeReceived) {
            parent.closeAsync(currentReason)
        }
    }

    fun received(frame: Frame) {
        require(frame.frameType.controlFrame) { "frame should be control frame" }

        when (frame) {
            is Frame.Close -> {
                currentReason = frame.readReason()

                if (closeSent) {
                    parent.closeAsync(currentReason)
                } else {
                    closeReceived = true
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
        if (!closeSent && !closeReceived) {
            pingPongFuture.getAndSet(exec.scheduleAtFixedRate({
                if (closeReceived || closeSent) {
                    cancelPingPong()
                } else {
                    doPing()
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

    private fun generatePingMessage() = "[ping ${nextNonce()} ping]"

    private fun doPing() {
        val ping = generatePingMessage()
        expectedPong = ping

        parent.send(Frame.Ping(ByteBuffer.wrap(ping.toByteArray(Charsets.UTF_8))))
        closeAfterTimeout()
    }

    private fun handlePong(frame: Frame) {
        require(frame.frameType == FrameType.PONG)

        if (frame.buffer.getString() == expectedPong) {
            expectedPong = null
            cancelAllTimeouts()
        }
    }

    private fun handleTimeout() {
        cancelAllTimeouts()
        parent.close()
    }

    private fun closeAfterTimeout(): ScheduledFuture<*> {
        val f = exec.schedule(TimeoutTask(this), parent.timeout.toMillis(), TimeUnit.MILLISECONDS)

        closeHandlers.add(WeakReference(f))

        return f
    }

    fun cancelAllTimeouts() {
        for (ref in closeHandlers) {
            ref.get()?.cancel(false)
        }
        closeHandlers.clear()
    }

    class TimeoutTask(self: ControlFrameHandler) : Runnable {
        val ref = WeakReference(self)

        override fun run() {
            ref.get()?.handleTimeout()
        }
    }
}
