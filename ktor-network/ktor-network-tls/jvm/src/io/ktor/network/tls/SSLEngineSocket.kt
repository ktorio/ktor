/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import javax.net.ssl.*
import kotlin.coroutines.*

internal class SSLEngineSocket(
    override val coroutineContext: CoroutineContext,
    private val engine: SSLEngine,
    connection: Connection
) : Socket, CoroutineScope {
    private val socket = connection.socket

    override val socketContext: Job get() = socket.socketContext
    override val remoteAddress: SocketAddress get() = socket.remoteAddress
    override val localAddress: SocketAddress get() = socket.localAddress

    private val lock = Mutex()
    private val closed = atomic(false)

    private val bufferAllocator = SSLEngineBufferAllocator(engine)
    private val wrapper = SSLEngineWrapper(engine, bufferAllocator, connection.output)
    private val unwrapper = SSLEngineUnwrapper(engine, bufferAllocator, connection.input)

    override fun attachForReading(channel: ByteChannel): WriterJob =
        writer(CoroutineName("network-tls-input"), channel) {
            var error: Throwable? = null
            try {
                var destination = bufferAllocator.allocateApplication(0)
                loop@ while (true) {
                    destination.clear()
                    val result = unwrapper.readAndUnwrap(destination) { destination = it } ?: break@loop
                    destination.flip()

                    if (destination.remaining() > 0) {
                        this.channel.writeFully(destination)
                        this.channel.flush()
                    }

                    handleResult(result)
                    if (result.status == SSLEngineResult.Status.CLOSED) break@loop
                }
            } catch (cause: Throwable) {
                error = cause
                throw cause
            } finally {
                unwrapper.cancel(error)
                engine.closeOutbound()
                doClose(error)
            }
        }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun attachForWriting(channel: ByteChannel): ReaderJob =
        reader(CoroutineName("network-tls-output"), channel) {
            var error: Throwable? = null
            try {
                val source = bufferAllocator.allocateApplication(0)
                loop@ while (true) {
                    source.clear()
                    if (this.channel.readAvailable(source) == -1) break@loop
                    source.flip()

                    while (source.remaining() > 0) {
                        val result = wrapper.wrapAndWrite(source)

                        handleResult(result)
                        if (result.status == SSLEngineResult.Status.CLOSED) break@loop
                    }
                }
            } catch (cause: Throwable) {
                error = cause
                throw cause
            } finally {
                engine.closeInbound()
                doClose(error)
            }
        }

    override fun close() {
        engine.closeOutbound()
        if (isActive) launch {
            doClose(null)
        } else {
            if (closed.compareAndSet(expect = false, update = true)) socket.close()
        }
    }

    private suspend fun handleResult(result: SSLEngineResult) {
        if (result.status == SSLEngineResult.Status.CLOSED) {
            doClose(null)
            return
        }
        if (result.handshakeStatus.needHandshake) {
            doHandshake(result.handshakeStatus)
        }
    }

    private suspend fun doClose(cause: Throwable?) = lock.withLock {
        if (closed.compareAndSet(expect = false, update = true)) {
            socket.use {
                wrapper.close(cause)
            }
        }
    }

    private suspend fun doHandshake(initialStatus: SSLEngineResult.HandshakeStatus) = lock.withLock {
        var temp = bufferAllocator.allocateApplication(0)
        var status = initialStatus
        while (true) {
            when (status) {
                SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                    coroutineScope {
                        while (true) {
                            val task = engine.delegatedTask ?: break
                            launch(Dispatchers.IO) { task.run() }
                        }
                    }
                    status = engine.handshakeStatus
                }
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    temp.clear()
                    temp.flip()
                    status = wrapper.wrapAndWrite(temp).handshakeStatus
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    temp.clear()
                    status = unwrapper.readAndUnwrap(temp) { temp = it }?.handshakeStatus ?: break
                }
                else -> break
            }
        }
    }

    private val SSLEngineResult.HandshakeStatus.needHandshake: Boolean
        get() = this != SSLEngineResult.HandshakeStatus.FINISHED && this != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
}
