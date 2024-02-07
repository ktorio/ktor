/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.certificates.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.security.*
import java.security.cert.*
import javax.net.ssl.*
import kotlin.test.*

class TLSHandshakeMessageClientServerTest {

    companion object {
        private const val HOST = "0.0.0.0"
    }

    private val testPwd: CharArray = "changeit".toCharArray()
    private val certKey: String = "mykey"

    private val keyStore: KeyStore by lazy {
        generateCertificate(
            File("build/temp.jks"),
            algorithm = "SHA256withRSA",
            keySizeInBits = 4096,
            keyAlias = certKey,
            keyPassword = testPwd.concatToString(),
        )
    }

    private val testTrustManager: TrustManager by lazy {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .also { it.init(keyStore) }
        trustManagerFactory
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
    }

    private val certificate by lazy {
        val certsChain = keyStore.getCertificateChain(certKey).toList()
            .filterIsInstance<X509Certificate>()
        val certs = certsChain.toTypedArray()
        val privateKey = keyStore.getKey(certKey, testPwd) as PrivateKey
        CertificateAndKey(certs, privateKey)
    }

    private val coroutineContext = Dispatchers.Default
    private val selectorManager = ActorSelectorManager(coroutineContext)

    @Test
    fun clientServerTLSHandshakeWithLogging() = runBlocking {
        val port = firstFreePort()
        val expected = "Hello, I am being secure"
        val start = System.currentTimeMillis()
        val timing = { (System.currentTimeMillis() - start).toString().padEnd(4) }
        val sendOrReceive: NetworkRole.(NetworkRole) -> String = { role: NetworkRole ->
            if (this == role) "SEND   " else "RECEIVE"
        }
        val messages = mutableListOf<String>()
        val log = { msg: String ->
            messages += msg
            println(msg)
        }

        val serverJob = launch(coroutineContext) {
            val serverSocketBinding = aSocket(selectorManager)
                .tcp()
                .bind(HOST, port)

            val serverSocket = serverSocketBinding.accept()
                .tls(coroutineContext) {
                    role = NetworkRole.SERVER
                    certificates += certificate
                    trustManager = testTrustManager
                    onHandshake = { type, role ->
                        log("${timing()} SERVER ${this@tls.role.sendOrReceive(role)} $type")
                    }
                }

            val actual = serverSocket.openReadChannel()
                .readPacket(expected.length)
                .readText()
            assertEquals(expected, actual)

            serverSocket.openWriteChannel().use {
                writeStringUtf8(expected)
            }
        }

        val clientJob = launch(coroutineContext) {
            val clientSocket = aSocket(selectorManager)
                .tcp()
                .connect(HOST, port)
                .tls(coroutineContext) {
                    trustManager = testTrustManager
                    onHandshake = { type, role ->
                        log("${timing()} CLIENT ${this@tls.role.sendOrReceive(role)} $type")
                    }
                }

            val writeChannel = clientSocket.openWriteChannel().apply {
                writeStringUtf8(expected)
                flush()
            }

            val actual = clientSocket.openReadChannel()
                .readPacket(expected.length)
                .readText()
            assertEquals(expected, actual)

            writeChannel.close()
        }

        joinAll(serverJob, clientJob)

        messages.forEach { message ->
            assert(message.matches(Regex("\\d+\\s+(?:SERVER|CLIENT)\\s+(?:SEND|RECEIVE)\\s+\\w+"))) {
                "Expected $message to match log regex"
            }
        }
    }

    @Test
    fun clientServerTLSWithTimeout() {
        assertFailsWith<TLSHandshakeTimeoutException> {
            runBlocking {
                val port = firstFreePort()
                val expected = "Hello, I am being secure"

                val serverJob = launch(coroutineContext) {
                    val serverSocketBinding = aSocket(selectorManager)
                        .tcp()
                        .bind(HOST, port)

                    val serverSocket = serverSocketBinding.accept()
                        .tls(coroutineContext) {
                            role = NetworkRole.SERVER
                            certificates += certificate
                            trustManager = testTrustManager
                            onHandshake = { _, _ ->
                                Thread.sleep(100L)
                            }
                        }

                    val actual = serverSocket.openReadChannel()
                        .readPacket(expected.length)
                        .readText()
                    assertEquals(expected, actual)

                    serverSocket.openWriteChannel().use {
                        writeStringUtf8(expected)
                    }
                }

                val clientJob = launch(coroutineContext) {
                    val clientSocket = aSocket(selectorManager)
                        .tcp()
                        .connect(HOST, port)
                        .tls(coroutineContext) {
                            trustManager = testTrustManager
                            handshakeTimeoutMillis = 50L
                        }

                    val writeChannel = clientSocket.openWriteChannel().apply {
                        writeStringUtf8(expected)
                        flush()
                    }

                    val actual = clientSocket.openReadChannel()
                        .readPacket(expected.length)
                        .readText()
                    assertEquals(expected, actual)

                    writeChannel.close()
                }

                joinAll(serverJob, clientJob)
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
            } catch (ignore: IOException) { }
        }
    }
}
