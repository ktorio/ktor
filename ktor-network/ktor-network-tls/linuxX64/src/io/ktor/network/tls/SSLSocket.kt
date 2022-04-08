/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import io.ktor.network.tls.internal.openssl.*
import kotlin.coroutines.*

internal class SSLSocket(
    override val coroutineContext: CoroutineContext,
    ssl: CPointer<SSL>,
    connection: Connection
) : Socket, CoroutineScope {
    private val socket = connection.socket

    override val socketContext: Job get() = socket.socketContext
    override val remoteAddress: SocketAddress get() = socket.remoteAddress
    override val localAddress: SocketAddress get() = socket.localAddress

    private val debugString = coroutineContext[CoroutineName]?.name ?: "DEBUG"

    private val lock = Mutex()
    private val closed = atomic(false)

    private val operations = SSLOperations(ssl, debugString)
    private val pipe = BIOPipe(ssl, connection, debugString)

    init {
        socketContext.job.invokeOnCompletion {
            SSL_free(ssl) //TODO: when to call SSL_free(ssl)
        }
    }

    override fun attachForReading(channel: ByteChannel): WriterJob =
        writer(CoroutineName("network-secure-input"), channel) {
            var error: Throwable? = null
            try {
                do {
                    val result = this.channel.write { destination, startOffset, endExclusive ->
                        val destinationLength = (endExclusive - startOffset).toInt()
                        //println("[$debugString] READING START: size=${destination.size} | offset=$startOffset | length=$destinationLength")
                        if (destinationLength <= 0) {
                            //println("[$debugString] READING STOP: NO MORE TO WRITE")
                            return@write 0
                        }
                        val initialOffset = startOffset.toInt()
                        var destinationOffset = initialOffset

                        while (true) {
                            //println("[$debugString] READING STEP: size=${destination.size} | offset=$destinationOffset | length=$destinationLength")
                            val sslError = operations.read(
                                destination.pointer, destinationOffset, destinationLength,
                                onSuccess = {
                                    destinationOffset += it //TODO: caps at 1378
                                    null
                                },
                                onError = { it },
                                onClose = { SSLError.Closed }
                            )
                            pipe.writeFully() //TODO?

                            when (sslError) {
                                null -> {}
                                SSLError.Closed -> break //TODO handle properly
                                SSLError.WantRead -> {
                                    if (destinationOffset != initialOffset) break //we read some data already
                                    if (pipe.readAvailable() == 0) {
                                        //println("[$debugString] READING STOP: NO MORE TO READ FROM SOCKET")
                                        return@write 0
                                    }
                                }
                                else -> TODO("READ_ERROR: $sslError")
                            }
                        }
                        //println("[$debugString] READING STOP: size=${destination.size} | offset=$destinationOffset | length=$destinationLength")
                        destinationOffset - initialOffset
                    }
                    this.channel.flush()
                    //println("[$debugString] READING STEP RESULT: $result")
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
        reader(CoroutineName("network-secure-output"), channel) {
            var error: Throwable? = null
            try {
                do {
                    val result = this@reader.channel.read { source, startOffset, endExclusive ->
                        val sourceLength = (endExclusive - startOffset).toInt()
                        //println("[$debugString] WRITING START: size=${source.size} | offset=$startOffset | length=$sourceLength")
                        if (sourceLength <= 0) {
                            //println("[$debugString] WRITING STOP: NO MORE TO READ")
                            return@read 0
                        }
                        var sourceOffset = startOffset.toInt()

                        while (sourceLength - sourceOffset > 0) {
                            //println("[$debugString] WRITING STEP: size=${source.size} | offset=$sourceOffset | length=$sourceLength")
                            val sslError = operations.write(
                                source.pointer, sourceOffset, sourceLength,
                                onSuccess = {
                                    sourceOffset += it
                                    null
                                },
                                onError = { it },
                                onClose = { SSLError.Closed }
                            )
                            pipe.writeFully() //TODO?

                            when (sslError) {
                                null -> {}
                                SSLError.Closed -> break //TODO handle properly
                                SSLError.WantRead -> if (pipe.readAvailable() == 0) {
                                    //println("[$debugString] WRITING STOP: NO MORE TO READ FROM SOCKET")
                                    return@read 0
                                }
                                else -> TODO("WRITE_ERROR: $sslError")
                            }
                        }
                        //println("[$debugString] WRITING STOP: size=${source.size} | offset=$sourceOffset | length=$sourceLength")
                        sourceOffset - startOffset.toInt()
                    }
                    //println("[$debugString] WRITING STEP RESULT: $result")
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
        socket.close()
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
