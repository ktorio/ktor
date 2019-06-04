/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.network.tls.certificates.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.junit4.*
import org.junit.*
import java.io.*
import java.net.*
import java.util.concurrent.*
import javax.net.ssl.*
import kotlin.concurrent.*
import kotlin.test.*
import kotlin.test.Test

@Suppress("KDocMissingDocumentation", "BlockingMethodInNonBlockingContext")
class ConnectErrorsTest {
    @get:Rule
    val timeout = CoroutinesTimeout.seconds(60)

    private val serverSocket = ServerSocket(0, 1)

    @AfterTest
    fun teardown() {
        serverSocket.close()
    }

    @Test
    fun testConnectAfterConnectionErrors(): Unit = runBlocking<Unit> {
        HttpClient(CIO.config {
            maxConnectionsCount = 1
            endpoint.connectTimeout = 200
            endpoint.connectRetryAttempts = 3
        }).use { client ->
            serverSocket.close()

            repeat(10) {
                try {
                    client.call("http://localhost:${serverSocket.localPort}/").close()
                    fail("Shouldn't reach here")
                } catch (_: java.net.ConnectException) {
                }
            }

            ServerSocket(serverSocket.localPort).use { newServer ->
                val thread = thread {
                    try {
                        newServer.accept().use { client ->
                            client.getOutputStream().let { out ->
                                out.write("HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: 2\r\n\r\nOK".toByteArray())
                                out.flush()
                            }
                            client.getInputStream().readBytes()
                        }
                    } catch (ignore: SocketException) {
                    }
                }
                withTimeout(10000L) {
                    assertEquals("OK", client.get<String>("http://localhost:${serverSocket.localPort}/"))
                }
                thread.join()
            }
        }
    }

    @Test
    fun testLateServerStart(): Unit = runBlocking<Unit> {
        val keyStoreFile = File("build/temp.jks")
        val keyStore = generateCertificate(keyStoreFile, algorithm = "SHA256withECDSA", keySizeInBits = 256)

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }

        val client = HttpClient(CIO.config {
            maxConnectionsCount = 3

            endpoint {
                connectTimeout = 200
                connectRetryAttempts = 1
            }

            https {
                trustManager = trustManagerFactory.trustManagers.first { it is X509TrustManager } as X509TrustManager
            }
        })

        val serverPort = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(Netty, environment = applicationEngineEnvironment {
            sslConnector(keyStore, "mykey", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
                port = serverPort
                keyStorePath = keyStoreFile.absoluteFile
            }
            module {
                routing {
                    get {
                        call.respondText("OK")
                    }
                }
            }
        })

        try {
            client.get<String>(scheme = "https", path = "/", port = serverPort)
        } catch (_: java.net.ConnectException) {
        }

        try {
            server.start()

            val message = client.get<String>(scheme = "https", path = "/", port = serverPort)
            assertEquals("OK", message)
        } finally {
            server.stop(0, 0, TimeUnit.MILLISECONDS)
        }

    }
}
