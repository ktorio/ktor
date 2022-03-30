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

    private val debugString = coroutineContext[CoroutineName]?.name ?: "DEBUG"

    private val lock = Mutex()
    private val closed = atomic(false)

    private val bufferAllocator = SSLEngineBufferAllocator(engine)
    private val wrapper = SSLEngineWrapper(engine, bufferAllocator, connection.output, debugString)
    private val unwrapper = SSLEngineUnwrapper(engine, bufferAllocator, connection.input, debugString)

    override fun attachForReading(channel: ByteChannel): WriterJob =
        writer(CoroutineName("network-tls-input"), channel) {
            var error: Throwable? = null
            try {
                var destination = bufferAllocator.allocateApplication(0)
                loop@ while (true) {
                    destination.clear()
                    //println("[$debugString] READING: readAndUnwrap.START")
                    //println("[$debugString] READING_BEFORE_UNWRAP: $destination")
                    val result = unwrapper.readAndUnwrap(destination) { destination = it } ?: break@loop
                    //println("[$debugString] READING_AFTER_UNWRAP: $destination")
                    //println("[$debugString] READING: readAndUnwrap.STOP")

                    destination.flip()
                    //println("[$debugString] READING_WRITE_BEFORE: $destination")
                    if (destination.remaining() > 0) {
                        this.channel.writeFully(destination)
                        this.channel.flush()
                    }
                    //println("[$debugString] READING_WRITE_AFTER: $destination")

                    handleResult(result)
                    if (result.status == SSLEngineResult.Status.CLOSED) break@loop
                }
            } catch (cause: Throwable) {
                error = cause
                throw cause
            } finally {
                //println(error)
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
                    //println("[$debugString] WRITING_READ_BEFORE: $source")
                    if (this.channel.readAvailable(source) == -1) break@loop
                    //println("[$debugString] WRITING_READ_AFTER: $source")
                    source.flip()
                    //println("[$debugString] WRITING_BEFORE_SOURCE: $source")
                    while (source.remaining() > 0) {
                        //println("[$debugString] WRITING: wrapAndWrite.START")
                        //println("[$debugString] WRITING_BEFORE_WRAP: $source")
                        val result = wrapper.wrapAndWrite(source)
                        //println("[$debugString] WRITING_AFTER_WRAP: $source")
                        //println("[$debugString] WRITING: wrapAndWrite.STOP")

                        handleResult(result)
                        if (result.status == SSLEngineResult.Status.CLOSED) break@loop
                    }
                }
            } catch (cause: Throwable) {
                error = cause
                throw cause
            } finally {
                //println(error)
                engine.closeInbound() //TODO: when this should be called???
                doClose(error)
            }
        }

    //TODO proper close implementation?
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
            //println("[$debugString] HANDSHAKE: $status")
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
                    //println("[$debugString] HANDSHAKE: wrapAndWrite.START")
                    status = wrapper.wrapAndWrite(temp).handshakeStatus
                    //println("[$debugString] HANDSHAKE: wrapAndWrite.STOP")
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    temp.clear()
                    //println("[$debugString] HANDSHAKE: readAndUnwrap.START")
                    status = unwrapper.readAndUnwrap(temp) { temp = it }?.handshakeStatus ?: break
                    //println("[$debugString] HANDSHAKE: readAndUnwrap.STOP")
                }
                else -> break
            }
        }
    }

    private val SSLEngineResult.HandshakeStatus.needHandshake: Boolean
        get() = this != SSLEngineResult.HandshakeStatus.FINISHED && this != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING

}
