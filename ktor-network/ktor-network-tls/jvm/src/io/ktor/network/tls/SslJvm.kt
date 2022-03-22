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
import java.security.cert.*
import javax.net.ssl.*
import kotlin.coroutines.*
import kotlin.synchronized

@InternalAPI
public actual fun SslContext(builder: TLSConfigBuilder): SslContext = SslContext(SSLContext.getInstance("TLS").also {
    it.init(null, arrayOf(builder.build().trustManager), null)
})

@InternalAPI
public actual class SslContext(
    private val jvmContext: SSLContext
) {
    public actual constructor() : this(
        SSLContext.getInstance("TLS").also {
            it.init(
                null,
                arrayOf( //TODO
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                    }
                ),
//                    TrustManagerFactory . getInstance (TrustManagerFactory.getDefaultAlgorithm()).also {
//                    it.init(null as KeyStore?)
//                }.trustManagers,
                null
            )
        }
    )

    public actual fun createClientEngine(): SslEngine {
        val engine = jvmContext.createSSLEngine()
        engine.useClientMode = true
        return engine
    }

    public actual fun createClientEngine(peerHost: String, peerPort: Int): SslEngine {
        val engine = jvmContext.createSSLEngine(peerHost, peerPort)
        engine.useClientMode = true
        return engine
    }

    public actual fun createServerEngine(): SslEngine {
        val engine = jvmContext.createSSLEngine()
        engine.useClientMode = false
        return engine
    }
}

@InternalAPI
public actual typealias SslEngine = SSLEngine

@InternalAPI
public actual fun Socket.ssl(
    coroutineContext: CoroutineContext,
    engine: SslEngine
): Socket = SslSocket(
    socket = this,
    writer = openWriteChannel(),
    reader = openReadChannel(),
    engine = engine,
    coroutineContext = coroutineContext
)

@InternalAPI
public actual fun Connection.ssl(
    coroutineContext: CoroutineContext,
    engine: SslEngine
): Connection = SslSocket(socket, output, input, engine, coroutineContext).connection()

private class SslSocket(
    private val socket: Socket,
    writer: ByteWriteChannel,
    reader: ByteReadChannel,
    private val engine: SSLEngine,
    override val coroutineContext: CoroutineContext
) : Socket, CoroutineScope {
    private val coroutineName = coroutineContext[CoroutineName]?.name

    private val closed = atomic(false)

    private val lock = Mutex()

    private val buffers = Buffers(engine)
    private val wrapper = Wrapper(writer)
    private val unwrapper = Unwrapper(reader)

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
                    //println("[$coroutineName] READING: readAndUnwrap.START")
                    //println("[$coroutineName] READING_BEFORE_UNWRAP: $destination")
                    val result = unwrapper.readAndUnwrap(destination) { destination = it } ?: break@loop
                    //println("[$coroutineName] READING_AFTER_UNWRAP: $destination")
                    //println("[$coroutineName] READING: readAndUnwrap.STOP")

                    destination.flip()
                    //println("[$coroutineName] READING_WRITE_BEFORE: $destination")
                    if (destination.remaining() > 0) {
                        this.channel.writeFully(destination)
                        this.channel.flush()
                    }
                    //println("[$coroutineName] READING_WRITE_AFTER: $destination")

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
                    //println("[$coroutineName] WRITING_READ_BEFORE: $source")
                    if (this.channel.readAvailable(source) == -1) break@loop
                    //println("[$coroutineName] WRITING_READ_AFTER: $source")
                    source.flip()
                    //println("[$coroutineName] WRITING_BEFORE_SOURCE: $source")
                    while (source.remaining() > 0) {
                        //println("[$coroutineName] WRITING: wrapAndWrite.START")
                        //println("[$coroutineName] WRITING_BEFORE_WRAP: $source")
                        val result = wrapper.wrapAndWrite(source)
                        //println("[$coroutineName] WRITING_AFTER_WRAP: $source")
                        //println("[$coroutineName] WRITING: wrapAndWrite.STOP")

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
            //println("[$coroutineName] HANDSHAKE: $status")
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
                    //println("[$coroutineName] HANDSHAKE: wrapAndWrite.START")
                    status = wrapper.wrapAndWrite(temp).handshakeStatus
                    //println("[$coroutineName] HANDSHAKE: wrapAndWrite.STOP")
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    temp.clear()
                    //println("[$coroutineName] HANDSHAKE: readAndUnwrap.START")
                    status = unwrapper.readAndUnwrap(temp) { temp = it }?.handshakeStatus ?: break
                    //println("[$coroutineName] HANDSHAKE: readAndUnwrap.STOP")
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

    private inner class Wrapper(
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
                //println("[$coroutineName] WRAP_BEFORE: $wrapDestination")
                result = engine.wrap(wrapSource, wrapDestination)
                //println("[$coroutineName] WRAP_RESULT: $result")
                //println("[$coroutineName] WRAP_AFTER: $wrapDestination")
                if (result.status != SSLEngineResult.Status.BUFFER_OVERFLOW) break
                //println("[$coroutineName] WRAP_OVERFLOW: $wrapDestination")
                wrapDestination = buffers.reallocatePacket(wrapDestination, flip = true)
                //println("[$coroutineName] WRAP_OVERFLOW_REALLOCATE: $wrapDestination")
            }
            //println("[$coroutineName] WRAP_WRITE_BEFORE: $wrapDestination")
            if (result.bytesProduced() > 0) {
                wrapDestination.flip()
                //println("[$coroutineName] WRAP_WRITE: $wrapDestination")
                writer.writeFully(wrapDestination)
                writer.flush()
            }
            //println("[$coroutineName] WRAP_WRITE_AFTER: $wrapDestination")
            return result
        }

        suspend fun close(cause: Throwable?): SSLEngineResult = wrapLock.withLock {
            //println("[$coroutineName] CLOSE: $cause")
            val temp = buffers.allocateApplication(0)
            var result: SSLEngineResult
            do {
                result = wrapAndWriteX(temp)
            } while (
                result.status != SSLEngineResult.Status.CLOSED &&
                !(result.status == SSLEngineResult.Status.OK && result.handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)
            )

            writer.close(cause)

            result
        }
    }

    private inner class Unwrapper(
        private val reader: ByteReadChannel
    ) {
        private val unwrapLock = Mutex()
        private var unwrapSource = buffers.allocatePacket(0)
        private var unwrapRemaining = 0
        
        //TODO revisit
        private var unwrapResultCont: CancellableContinuation<SSLEngineResult?>? = null

        fun cancel(cause: Throwable?) {
            reader.cancel(cause)
        }

        suspend inline fun readAndUnwrap(
            initialUnwrapDestination: ByteBuffer,
            updateUnwrapDestination: (ByteBuffer) -> Unit
        ): SSLEngineResult? {
            if (!unwrapLock.tryLock()) return suspendCancellableCoroutine {
                synchronized(this) {
                    unwrapResultCont = it
                }
            }
            try {
                var unwrapDestination: ByteBuffer = initialUnwrapDestination
                var result: SSLEngineResult?

                if (unwrapRemaining > 0) {
                    unwrapSource.compact()
                    unwrapSource.flip()
                } else {
                    unwrapSource.clear()
                    if (!readData()) {
                        synchronized(this) {
                            unwrapResultCont?.resume(null)
                            unwrapResultCont = null
                        }
                        return null
                    }
                }

                //println("[$coroutineName] UNWRAP_INIT[DST]: $unwrapDestination")
                //println("[$coroutineName] UNWRAP_INIT[SRC]: $unwrapSource")
                while (true) {
                    //println("[$coroutineName] UNWRAP_BEFORE[DST]: $unwrapDestination")
                    //println("[$coroutineName] UNWRAP_BEFORE[SRC]: $unwrapSource")
                    result = engine.unwrap(unwrapSource, unwrapDestination)
                    //println("[$coroutineName] UNWRAP_RESULT: $result")
                    //println("[$coroutineName] UNWRAP_AFTER[DST]: $unwrapDestination")
                    //println("[$coroutineName] UNWRAP_AFTER[SRC]: $unwrapSource")

                    when (result.status!!) {
                        SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                            //println("[$coroutineName] UNWRAP_UNDERFLOW_BEFORE[SRC]: $unwrapSource")
                            if (unwrapSource.limit() == unwrapSource.capacity()) {
                                //println("[$coroutineName] UNWRAP_UNDERFLOW_1")
                                //buffer is too small to read all needed data
                                unwrapSource = buffers.reallocatePacket(unwrapSource, flip = false)
                            } else {
                                //println("[$coroutineName] UNWRAP_UNDERFLOW_2")
                                //not all data received
                                unwrapSource.position(unwrapSource.limit())
                                unwrapSource.limit(unwrapSource.capacity())
                            }
                            //println("[$coroutineName] UNWRAP_UNDERFLOW_AFTER[SRC]: $unwrapSource")
                            if (!readData()) {
                                synchronized(this) {
                                    unwrapResultCont?.resume(null)
                                    unwrapResultCont = null
                                }
                                return null
                            }
                        }
                        SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                            //println("[$coroutineName] UNWRAP_OVERFLOW_BEFORE[DST]: $unwrapDestination")
                            unwrapDestination = buffers.reallocateApplication(unwrapDestination, flip = true)
                            //println("[$coroutineName] UNWRAP_OVERFLOW_AFTER[DST]: $unwrapDestination")
                        }
                        else -> break
                    }
                }
                //println("[$coroutineName] UNWRAP_FINAL[DST]: $unwrapDestination")
                //println("[$coroutineName] UNWRAP_FINAL[SRC]: $unwrapSource")
                unwrapRemaining = unwrapSource.remaining()
                if (unwrapDestination !== initialUnwrapDestination) updateUnwrapDestination(unwrapDestination)
                synchronized(this) {
                    unwrapResultCont?.resume(result)
                    unwrapResultCont = null
                }
                return result
            } catch (cause: Throwable) {
                synchronized(this) {
                    unwrapResultCont?.resumeWithException(cause)
                    unwrapResultCont = null
                }
                throw cause
            } finally {
                unwrapLock.unlock()
            }
        }

        private suspend fun readData(): Boolean {
            //println("[$coroutineName] UNWRAP_READ_BEFORE[SRC]: $unwrapSource")
            val read = reader.readAvailable(unwrapSource)
            unwrapSource.flip()
            //println("[$coroutineName] UNWRAP_READ_AFTER[SRC]: $unwrapSource")

            //println("[$coroutineName] UNWRAP_READ_COMPLETE[SRC]: $unwrapSource")
            return read != -1
        }
    }

}
