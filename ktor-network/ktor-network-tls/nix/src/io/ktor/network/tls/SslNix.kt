/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import openssl.*
import kotlin.coroutines.*

@InternalAPI
public actual fun SslContext(builder: TLSConfigBuilder): SslContext = SslContext()

@InternalAPI
public actual class SslContext {
    private val ctx = SSL_CTX_new(TLS_method())!!

    init {
        //TODO
        SSL_CTX_set_verify(ctx, SSL_VERIFY_NONE, null)
    }

    public actual fun createClientEngine(): SslEngine {
        val ssl = SSL_new(ctx)!!
        SSL_set_connect_state(ssl)
        return Ssl(ssl)
    }

    public actual fun createClientEngine(peerHost: String, peerPort: Int): SslEngine {
        val ssl = SSL_new(ctx)!!
        SSL_set_connect_state(ssl)
        return Ssl(ssl)
    }

    public actual fun createServerEngine(): SslEngine {
        TODO("Not yet implemented")
    }

}

@InternalAPI
public actual abstract class SslEngine

@InternalAPI
public actual fun Socket.ssl(
    coroutineContext: CoroutineContext,
    engine: SslEngine
): Socket = SslSocket(
    socket = this,
    writer = openWriteChannel(),
    reader = openReadChannel(),
    engine = engine as Ssl,
    coroutineContext = coroutineContext
)

@InternalAPI
public actual fun Connection.ssl(
    coroutineContext: CoroutineContext,
    engine: SslEngine
): Connection = SslSocket(socket, output, input, engine as Ssl, coroutineContext).connection()

private class SslSocket(
    private val socket: Socket,
    writer: ByteWriteChannel,
    reader: ByteReadChannel,
    private val engine: Ssl,
    override val coroutineContext: CoroutineContext
) : Socket, CoroutineScope {
    private val closed = atomic(false)
    private val lock = Mutex()

    override val socketContext: Job get() = socket.socketContext
    override val remoteAddress: SocketAddress get() = socket.remoteAddress
    override val localAddress: SocketAddress get() = socket.localAddress

    private val pipe = engine.attach(reader, writer)

    override fun attachForReading(channel: ByteChannel): WriterJob =
        writer(CoroutineName("cio-ssl-input"), channel) {
            var error: Throwable? = null
            try {
                do {
                    val result = this.channel.write { destination, startOffset, endExclusive ->
                        val destinationLength = (endExclusive - startOffset).toInt()
                        //println("READING START: size=${destination.size} | offset=$startOffset | length=$destinationLength")
                        if (destinationLength <= 0) {
                            //println("READING STOP: NO MORE TO WRITE")
                            return@write 0
                        }
                        var destinationOffset = startOffset.toInt()

                        while (destinationLength - destinationOffset > 0) {
                            //println("READING STEP: size=${destination.size} | offset=$destinationOffset | length=$destinationLength")
                            val sslError = engine.read(
                                destination.pointer, destinationOffset, destinationLength,
                                onSuccess = {
                                    destinationOffset += it //TODO: caps at 1378
                                    null
                                },
                                onError = { it }
                            )

                            when (sslError) {
                                null -> {}
                                SslError.WantRead -> if (pipe.readAvailable() == 0) {
                                    //println("READING STOP: NO MORE TO READ FROM SOCKET")
                                    return@write 0
                                }
                                else -> TODO("READ_ERROR: $sslError")
                            }
                        }
                        //println("READING STOP: size=${destination.size} | offset=$destinationOffset | length=$destinationLength")
                        destinationOffset - startOffset.toInt()
                    }
                    this.channel.flush()
                    //println("READING STEP RESULT: $result")
                } while (result > 0)
            } catch (cause: Throwable) {
                error = cause
                throw cause
            } finally {
                //println(error)
//                unwrapper.cancel(error)
//                engine.closeOutbound()
//                doClose(error)
            }
        }

    @Suppress("BlockingMethodInNonBlockingContext")
    override fun attachForWriting(channel: ByteChannel): ReaderJob =
        reader(CoroutineName("cio-ssl-output"), channel) {
            var error: Throwable? = null
            try {
                do {
                    val result = this@reader.channel.read { source, startOffset, endExclusive ->
                        val sourceLength = (endExclusive - startOffset).toInt()
                        //println("WRITING START: size=${source.size} | offset=$startOffset | length=$sourceLength")
                        if (sourceLength <= 0) {
                            //println("WRITING STOP: NO MORE TO READ")
                            return@read 0
                        }
                        var sourceOffset = startOffset.toInt()

                        while (sourceLength - sourceOffset > 0) {
                            //println("WRITING STEP: size=${source.size} | offset=$sourceOffset | length=$sourceLength")
                            val sslError = engine.write(
                                source.pointer, sourceOffset, sourceLength,
                                onSuccess = {
                                    sourceOffset += it
                                    null
                                },
                                onError = { it }
                            )
                            pipe.writeFully() //TODO?

                            when (sslError) {
                                null -> {}
                                SslError.WantRead -> if (pipe.readAvailable() == 0) {
                                    //println("WRITING STOP: NO MORE TO READ FROM SOCKET")
                                    return@read 0
                                }
                                else -> TODO("WRITE_ERROR: $sslError")
                            }
                        }
                        //println("WRITING STOP: size=${source.size} | offset=$sourceOffset | length=$sourceLength")
                        sourceOffset - startOffset.toInt()
                    }
                    //println("WRITING STEP RESULT: $result")
                } while (result > 0)
            } catch (cause: Throwable) {
                error = cause
                throw cause
            } finally {
                //println(error)
//                engine.closeInbound() //TODO: when this should be called???
//                doClose(error)
            }
        }

    //TODO proper close implementation?
    override fun close() {
//        engine.closeOutbound()
//        if (isActive) launch {
//            doClose(null)
//        } else {
//            if (closed.compareAndSet(expect = false, update = true)) socket.close()
//        }
    }

    private suspend fun doClose(cause: Throwable?) = lock.withLock {
        if (closed.compareAndSet(expect = false, update = true)) {
            socket.use {
//                wrapper.close(cause)
            }
        }
    }


}
