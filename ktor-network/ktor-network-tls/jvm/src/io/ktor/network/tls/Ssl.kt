/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import java.nio.*
import javax.net.ssl.*
import kotlin.coroutines.*

@InternalAPI
public fun Socket.ssl(
    coroutineContext: CoroutineContext,
    engine: SSLEngine
): Socket = SslSocket(this, engine, coroutineContext)

private class SslSocket(
    private val socket: Socket,
    private val engine: SSLEngine,
    override val coroutineContext: CoroutineContext
) : Socket, CoroutineScope {
    private val closed = atomic(false)
    private val lock = Mutex()

    private val buffers = Buffers(engine)
    private val wrapper = Wrapper(engine, buffers, socket.openWriteChannel())
    private val unwrapper = Unwrapper(engine, buffers, socket.openReadChannel())

    override val socketContext: Job get() = socket.socketContext
    override val remoteAddress: SocketAddress get() = socket.remoteAddress
    override val localAddress: SocketAddress get() = socket.localAddress

    override fun attachForReading(channel: ByteChannel): WriterJob =
        writer(CoroutineName("cio-ssl-input"), channel) {
            var error: Throwable? = null
            try {
                var destination = buffers.allocateApplication(0)
                loop@ while (true) {
                    destination.clear()
                    //println("READING_BEFORE_UNWRAP: $destination")
                    val result = unwrapper.readAndUnwrap(destination) { destination = it } ?: break@loop
                    //println("READING_AFTER_UNWRAP: $destination")
                    destination.flip()
                    //println("READING_WRITE_BEFORE: $destination")
                    if (destination.remaining() > 0) {
                        this.channel.writeFully(destination)
                        this.channel.flush()
                    }
                    //println("READING_WRITE_AFTER: $destination")

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
        reader(CoroutineName("cio-ssl-output"), channel) {
            var error: Throwable? = null
            try {
                val source = buffers.allocateApplication(0)
                loop@ while (true) {
                    source.clear()
                    //println("WRITING_READ_BEFORE: $source")
                    if (this.channel.readAvailable(source) == -1) break@loop
                    //println("WRITING_READ_AFTER: $source")
                    source.flip()
                    //println("WRITING_BEFORE_SOURCE: $source")
                    while (source.remaining() > 0) {
                        //println("WRITING_BEFORE_WRAP: $source")
                        val result = wrapper.wrapAndWrite(source)
                        //println("WRITING_AFTER_WRAP: $source")

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
        var temp = buffers.allocateApplication(0)
        var status = initialStatus
        while (true) {
            //println("HANDSHAKE: $status")
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
                    //println("HANDSHAKE: wrapAndWrite")
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

    private class Buffers(private val engine: SSLEngine) {
        private var packetBufferSize = 0
        private var applicationBufferSize = 0

        fun allocatePacket(length: Int): ByteBuffer = allocate(
            length,
            get = { packetBufferSize },
            set = { packetBufferSize = it },
            new = { engine.session.packetBufferSize }
        )

        fun allocateApplication(length: Int): ByteBuffer = allocate(
            length,
            get = { applicationBufferSize },
            set = { applicationBufferSize = it },
            new = { engine.session.applicationBufferSize }
        )

        fun reallocatePacket(buffer: ByteBuffer, flip: Boolean): ByteBuffer =
            reallocate(buffer, flip, ::allocatePacket)

        fun reallocateApplication(buffer: ByteBuffer, flip: Boolean): ByteBuffer =
            reallocate(buffer, flip, ::allocateApplication)

        private inline fun allocate(
            length: Int,
            get: () -> Int,
            set: (Int) -> Unit,
            new: () -> Int
        ): ByteBuffer = synchronized(this) {
            if (get() == 0) {
                set(new())
            }
            if (length > get()) {
                set(length)
            }
            return ByteBuffer.allocate(get())
        }

        private inline fun reallocate(
            buffer: ByteBuffer,
            flip: Boolean,
            allocate: (length: Int) -> ByteBuffer
        ): ByteBuffer {
            val newSize = buffer.capacity() * 2
            val new = allocate(newSize)
            if (flip) {
                new.flip()
            }
            new.put(buffer)
            return new
        }

    }

    private class Wrapper(
        private val engine: SSLEngine,
        private val buffers: Buffers,
        private val writer: ByteWriteChannel
    ) {
        private val wrapLock = Mutex()
        private var wrapDestination = buffers.allocatePacket(0)

        @Suppress("BlockingMethodInNonBlockingContext")
        suspend fun wrapAndWrite(wrapSource: ByteBuffer): SSLEngineResult = wrapLock.withLock {
            wrapAndWriteX(wrapSource)
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        private suspend fun wrapAndWriteX(wrapSource: ByteBuffer): SSLEngineResult {
            var result: SSLEngineResult
            wrapDestination.clear()
            while (true) {
                //println("WRAP_BEFORE: $wrapDestination")
                result = engine.wrap(wrapSource, wrapDestination)
                //println("WRAP_RESULT: $result")
                //println("WRAP_AFTER: $wrapDestination")
                if (result.status != SSLEngineResult.Status.BUFFER_OVERFLOW) break
                //println("WRAP_OVERFLOW: $wrapDestination")
                wrapDestination = buffers.reallocatePacket(wrapDestination, flip = true)
                //println("WRAP_OVERFLOW_REALLOCATE: $wrapDestination")
            }
            //println("WRAP_WRITE_BEFORE: $wrapDestination")
            if (result.bytesProduced() > 0) {
                wrapDestination.flip()
                //println("WRAP_WRITE: $wrapDestination")
                writer.writeFully(wrapDestination)
                writer.flush()
            }
            //println("WRAP_WRITE_AFTER: $wrapDestination")
            return result
        }

        suspend fun close(cause: Throwable?): SSLEngineResult = wrapLock.withLock {
            //println("CLOSE: $cause")
            val temp = buffers.allocateApplication(0)
            var result: SSLEngineResult
            do {
                result = wrapAndWriteX(temp)
            } while (
                result.status != SSLEngineResult.Status.CLOSED &&
                !(result.status == SSLEngineResult.Status.OK && result.handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)
            )

            writer.close(cause)

            return result
        }
    }

    private class Unwrapper(
        private val engine: SSLEngine,
        private val buffers: Buffers,
        private val reader: ByteReadChannel
    ) {
        private val unwrapLock = Mutex()
        private var unwrapSource = buffers.allocatePacket(0)
        private var unwrapRemaining = 0

        fun cancel(cause: Throwable?) {
            reader.cancel(cause)
        }

        suspend inline fun readAndUnwrap(
            initialUnwrapDestination: ByteBuffer,
            updateUnwrapDestination: (ByteBuffer) -> Unit
        ): SSLEngineResult? = unwrapLock.withLock {
            var unwrapDestination: ByteBuffer = initialUnwrapDestination
            var result: SSLEngineResult?

            if (unwrapRemaining > 0) {
                unwrapSource.compact()
                unwrapSource.flip()
            } else {
                unwrapSource.clear()
                if (!readData()) return@withLock null
            }

            //println("UNWRAP_INIT[DST]: $unwrapDestination")
            //println("UNWRAP_INIT[SRC]: $unwrapSource")
            while (true) {
                //println("UNWRAP_BEFORE[DST]: $unwrapDestination")
                //println("UNWRAP_BEFORE[SRC]: $unwrapSource")
                result = engine.unwrap(unwrapSource, unwrapDestination)
                //println("UNWRAP_RESULT: $result")
                //println("UNWRAP_AFTER[DST]: $unwrapDestination")
                //println("UNWRAP_AFTER[SRC]: $unwrapSource")

                when (result.status!!) {
                    SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                        //println("UNWRAP_UNDERFLOW_BEFORE[SRC]: $unwrapSource")
                        if (unwrapSource.limit() == unwrapSource.capacity()) {
                            //println("UNWRAP_UNDERFLOW_1")
                            //buffer is too small to read all needed data
                            unwrapSource = buffers.reallocatePacket(unwrapSource, flip = false)
                        } else {
                            //println("UNWRAP_UNDERFLOW_2")
                            //not all data received
                            unwrapSource.position(unwrapSource.limit())
                            unwrapSource.limit(unwrapSource.capacity())
                        }
                        //println("UNWRAP_UNDERFLOW_AFTER[SRC]: $unwrapSource")
                        if (!readData()) return@withLock null
                    }
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                        //println("UNWRAP_OVERFLOW_BEFORE[DST]: $unwrapDestination")
                        unwrapDestination = buffers.reallocateApplication(unwrapDestination, flip = true)
                        //println("UNWRAP_OVERFLOW_AFTER[DST]: $unwrapDestination")
                    }
                    else -> break
                }
            }
            //println("UNWRAP_FINAL[DST]: $unwrapDestination")
            //println("UNWRAP_FINAL[SRC]: $unwrapSource")
            unwrapRemaining = unwrapSource.remaining()
            if (unwrapDestination !== initialUnwrapDestination) updateUnwrapDestination(unwrapDestination)
            result
        }

        private suspend fun readData(): Boolean {
            //println("UNWRAP_READ_BEFORE[SRC]: $unwrapSource")
            val read = reader.readAvailable(unwrapSource)
            unwrapSource.flip()
            //println("UNWRAP_READ_AFTER[SRC]: $unwrapSource")

            //println("UNWRAP_READ_COMPLETE[SRC]: $unwrapSource")
            return read != -1
        }
    }

}
