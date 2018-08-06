package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.junit.*
import org.junit.Test
import org.junit.rules.*
import java.net.ServerSocket
import java.nio.*
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.test.*

class ClientSocketTest {
    private val exec = Executors.newCachedThreadPool()
    private val selector = ActorSelectorManager(exec.asCoroutineDispatcher())
    private var server: Pair<ServerSocket, Thread>? = null

    @get:Rule
    val timeout = Timeout(15L, TimeUnit.SECONDS)

    @get:Rule
    val errors = ErrorCollector()

    @After
    fun tearDown() {
        server?.let { (server, thread) ->
            server.close()
            thread.interrupt()
        }
        selector.close()
        exec.shutdown()
    }

    @Test
    fun testConnect() {
        server { it.close() }

        client {
        }
    }

    @Test
    fun testRead() {
        server { client ->
            client.getOutputStream().use { o ->
                o.write("123".toByteArray())
                o.flush()
            }
        }

        client { socket ->
            val bb = ByteBuffer.allocate(3)
            val channel = socket.openReadChannel()
            channel.readFully(bb)
            assertEquals("123", String(bb.array()))
        }
    }

    @Test
    fun testWrite() {
        server { client ->
            assertEquals("123", client.getInputStream().reader().readText())
        }

        client { socket ->
            val channel = socket.openWriteChannel(true)
            channel.writeStringUtf8("123")
        }
    }

    @Test
    fun testReadParts() {
        server { client ->
            client.getOutputStream().use { o ->
                o.write("0123456789".toByteArray())
                o.flush()
            }
        }

        client { socket ->
            assertEquals("0123456789", socket.openReadChannel().readUTF8Line())
        }
    }

    private fun client(block: suspend (Socket) -> Unit) {
        runBlocking {
            aSocket(selector).tcp().connect(server!!.first.localSocketAddress).use {
                block(it)
            }
        }
    }

    private fun server(block: (java.net.Socket) -> Unit) {
        val server = java.net.ServerSocket(0)
        val thread = thread(start = false) {
            try {
                while (true) {
                    val client = try {
                        server.accept()
                    } catch (t: Throwable) {
                        break
                    }

                    client.use(block)
                }
            } catch (t: Throwable) {
                errors.addError(t)
            }
        }

        this.server = Pair(server, thread)
        thread.start()
    }
}