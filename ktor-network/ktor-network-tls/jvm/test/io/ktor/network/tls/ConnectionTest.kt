/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.certificates.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.debug.junit5.CoroutinesTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.Sink
import kotlinx.io.readByteArray
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

@Suppress("UNCHECKED_CAST")
@CoroutinesTimeout(20_000)
class ConnectionTest {

    @Test
    fun tlsWithoutCloseTest(): Unit = runBlocking {
        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager)
            .tcp()
            .connect("www.google.com", port = 443)
            .tls(Dispatchers.Default)

        val channel = socket.openWriteChannel()

        channel.apply {
            writeStringUtf8("GET / HTTP/1.1\r\n")
            writeStringUtf8("Host: www.google.com\r\n")
            writeStringUtf8("Connection: close\r\n\r\n")
            flush()
        }

        socket.openReadChannel().readBuffer()
    }

    @Test
    fun `close notify is not written concurrently with the last record`() = runBlocking {
        val payload = ByteArray(16 * 1024) { it.toByte() }
        val rawInput = ByteChannel()
        val delegate = ByteChannel()
        val rawOutput = SlowSingleWriterChannel(delegate)
        val job = Job()
        val handshake = TLSClientHandshake(
            rawInput,
            rawOutput,
            TLSConfigBuilder().build(),
            Dispatchers.Default + job
        )

        try {
            val reader = async(Dispatchers.Default) {
                val first = delegate.readTLSRecord()
                val second = delegate.readTLSRecord()
                Triple(first, second, delegate.awaitContent())
            }

            handshake.output.send(
                TLSRecord(TLSRecordType.ApplicationData, packet = buildPacket { writeFully(payload) })
            )
            handshake.close().join()

            assertFalse(rawOutput.concurrentAccess.get(), "rawOutput was accessed by two writers concurrently")

            val (data, alert, hasMore) = reader.await()
            assertEquals(TLSRecordType.ApplicationData, data.type)
            assertContentEquals(payload, data.packet.readByteArray())

            assertEquals(TLSRecordType.Alert, alert.type)
            assertContentEquals(
                byteArrayOf(TLSAlertLevel.WARNING.code.toByte(), TLSAlertType.CloseNotify.code.toByte()),
                alert.packet.readByteArray()
            )
            assertFalse(hasMore, "Unexpected data after close_notify")
        } finally {
            rawInput.cancel()
            job.cancel()
        }
    }

    @Test
    @Ignore
    fun clientCertificatesAuthTest() {
        val keyStoreFile = File("build/temp.jks")
        val keyStore = generateCertificate(keyStoreFile, algorithm = "SHA256withRSA", keySizeInBits = 4096)
        val certsChain = keyStore.getCertificateChain("mykey").toList() as List<X509Certificate>
        val certs = certsChain.toTypedArray()
        val password = "changeit".toCharArray()
        val privateKey = keyStore.getKey("mykey", password) as PrivateKey
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .also { it.init(keyStore) }
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        val port = firstFreePort()
        try {
            ServerBootstrap()
                .group(workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(
                    object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            val sslContext = SslContextBuilder.forServer(privateKey, *certs)
                                .trustManager(trustManagerFactory)
                                .build()
                            val sslEngine = sslContext.newEngine(ch.alloc()).apply {
                                useClientMode = false
                                needClientAuth = true
                            }
                            ch.pipeline().addLast(SslHandler(sslEngine))
                        }
                    }
                )
                .bind(port)
                .sync()

            tryToConnect(port, trustManagerFactory, keyStore to password)

            try {
                tryToConnect(port, trustManagerFactory)
                fail("TLSException was expected because client has no certificate to authenticate")
            } catch (_: TLSException) {
            }
        } finally {
            workerGroup.shutdownGracefully()
        }
    }

    private fun tryToConnect(
        port: Int,
        trustManagerFactory: TrustManagerFactory,
        keyStoreAndPassword: Pair<KeyStore, CharArray>? = null
    ) {
        runBlocking {
            aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
                .connect(InetSocketAddress("127.0.0.1", port))
                .tls(Dispatchers.IO) {
                    keyStoreAndPassword?.let { addKeyStore(it.first, it.second) }
                    trustManager = trustManagerFactory
                        .trustManagers
                        .filterIsInstance<X509TrustManager>()
                        .first()
                }
        }.use {
            it.openWriteChannel(autoFlush = true).use {
                @Suppress("DEPRECATION")
                close()
            }
        }
    }

    private fun firstFreePort(): Int {
        while (true) {
            try {
                val socket = ServerSocket(0, 1)
                val port = socket.localPort
                socket.close()
                return port
            } catch (_: IOException) {
            }
        }
    }

    /**
     * Simulates a slow socket: every flush suspends for a while, and any access to the channel
     * from another writer during that time is recorded.
     */
    private class SlowSingleWriterChannel(private val delegate: ByteChannel) : ByteWriteChannel {
        private val flushing = AtomicInteger(0)
        val concurrentAccess = AtomicBoolean(false)

        override val isClosedForWrite: Boolean get() = delegate.isClosedForWrite
        override val closedCause: Throwable? get() = delegate.closedCause

        @InternalAPI
        override val writeBuffer: Sink
            get() {
                checkNoFlushInProgress()
                return delegate.writeBuffer
            }

        override suspend fun flush() {
            checkNoFlushInProgress()
            flushing.incrementAndGet()
            try {
                delay(200.milliseconds)
                delegate.flush()
            } finally {
                flushing.decrementAndGet()
            }
        }

        override suspend fun flushAndClose() {
            checkNoFlushInProgress()
            delegate.flushAndClose()
        }

        override fun cancel(cause: Throwable?) {
            delegate.cancel(cause)
        }

        private fun checkNoFlushInProgress() {
            if (flushing.get() > 0) concurrentAccess.set(true)
        }
    }
}
