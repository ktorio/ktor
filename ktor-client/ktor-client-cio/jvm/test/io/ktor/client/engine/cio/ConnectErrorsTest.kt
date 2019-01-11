package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import java.net.*
import kotlin.concurrent.*
import kotlin.test.*

class ConnectErrorsTest {
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
                } catch (expected: Throwable) {
                }
            }

            ServerSocket(serverSocket.localPort).use { newServer ->
                val th = thread {
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
                th.join()
            }
        }
    }
}
