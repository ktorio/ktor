/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.network.tls.certificates.*
import io.ktor.utils.io.*
import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.ssl.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.*
import java.io.*
import java.net.*
import java.net.ServerSocket
import java.security.*
import java.security.cert.*
import javax.net.ssl.*

class ConnectionTests {

    @get:Rule
    val timeout = CoroutinesTimeout.seconds(20)

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

        socket.openReadChannel().readRemaining()
        Unit
    }

    @Test
    fun certificateTest(): Unit = runBlocking {
        val keyStore = generateCertificate(
            File.createTempFile("test", "certificate"),
            algorithm = "SHA256withRSA",
            keySizeInBits = 4096
        )

        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore, "changeit".toCharArray())

        val socket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
            .connect(InetSocketAddress("chat.freenode.net", 6697))
            .tls(Dispatchers.IO) {
                addKeyStore(keyStore, "changeit".toCharArray())
            }


        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = true)
        output.close()
        socket.close()
    }

    @Test
    fun test() {
        val keyStoreFile = File("build/temp.jks")
        val keyStore = generateCertificate(keyStoreFile, algorithm = "SHA256withRSA", keySizeInBits = 4096)
        val chain1 = keyStore.getCertificateChain("mykey").toList() as List<X509Certificate>
        val certs = chain1.toList().toTypedArray()
        val password = "changeit".toCharArray()
        val pk = keyStore.getKey("mykey", password) as PrivateKey
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()) .also { it.init(keyStore) }
        val workerGroup: EventLoopGroup = NioEventLoopGroup()
        try {
            val b = ServerBootstrap()
            b.group(workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        val sslContext = SslContextBuilder.forServer(pk, *certs).trustManager(trustManagerFactory).build()
                        val sslEngine = sslContext.newEngine(ch.alloc()).apply {
                            useClientMode = false
                            needClientAuth = true
                        }
                        ch.pipeline().addLast(SslHandler(sslEngine))
                    }
                })
            val port = firstFreePort()
            b.bind(port).sync()

            runBlocking {
                val socket = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp()
                    .connect(InetSocketAddress("127.0.0.1", port))
                    .tls(Dispatchers.IO) {
                        addKeyStore(keyStore, password)
                        trustManager = trustManagerFactory
                            .trustManagers
                            .filterIsInstance<X509TrustManager>()
                            .first()
                    }

                val output = socket.openWriteChannel(autoFlush = true)
                output.close()
                socket.close()
            }
        } finally {
            workerGroup.shutdownGracefully()
        }
    }

    private fun firstFreePort(): Int {
        while(true) {
            try {
                val socket = ServerSocket(0, 1)
                val port = socket.localPort
                socket.close()
                return port
            } catch (ignore: IOException) { }
        }
    }
}
