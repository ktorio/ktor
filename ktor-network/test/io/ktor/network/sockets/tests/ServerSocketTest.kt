package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.junit.*
import org.junit.Test
import org.junit.rules.*
import java.nio.channels.*
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.test.*

class ServerSocketTest {
    private val exec = Executors.newCachedThreadPool()
    private var tearDown = false
    private val selector = ActorSelectorManager(exec.asCoroutineDispatcher())
    private var client: Pair<java.net.Socket, Thread>? = null
    private var server = CompletableDeferred<ServerSocket>()
    private var failure: Throwable? = null
    private val bound = CountDownLatch(1)

    @get:Rule
    val timeout = Timeout(15L, TimeUnit.SECONDS)

    @After
    fun tearDown() {
        tearDown = true

        client?.let { (s, t) ->
            s.close()
            t.interrupt()
        }
        server.cancel()

        selector.close()
        failure?.let { throw it }
        exec.shutdown()
    }

    @Test
    fun testBindAndAccept() {
        server {  }
        client {  }
    }

    @Test
    fun testRead() {
        server { client ->
            assertEquals("123", client.openReadChannel().readUTF8Line())
        }

        client { socket ->
            socket.getOutputStream().use { os ->
                os.write("123".toByteArray())
            }
        }
    }

    @Test
    fun testWrite() {
        server { client ->
            val channel = client.openWriteChannel(true)
            channel.writeStringUtf8("123")
        }

        client { socket ->
            assertEquals("123", socket.getInputStream().reader().use { it.readText() })
        }
    }

    private fun server(block: suspend (Socket) -> Unit) {
        launch(CommonPool) {
            try {
                val server = aSocket(selector).tcp().bind(null)
                this@ServerSocketTest.server.complete(server)

                bound.countDown()

                loop@ while (failure == null) {
                    server.accept().use {
                        try {
                            block(it)
                        } catch (t: Throwable) {
                            addFailure(t)
                        }
                    }
                }
            } catch (e: ClosedChannelException) {
            } catch (e: CancelledKeyException) {
            } catch (t: Throwable) {
                this@ServerSocketTest.server.completeExceptionally(t)
                addFailure(t)
            }
        }
    }

    private fun client(block: (java.net.Socket) -> Unit) {
        val address = runBlocking { server.await().localAddress }
        val client = java.net.Socket().apply { connect(address) }

        val thread = thread(start = false) {
            try {
                client.use {
                    block(it)
                }
            } catch (t: Throwable) {
                addFailure(t)
            }
        }

        this.client = Pair(client, thread)
        thread.start()
        thread.join()
    }

    private fun addFailure(t: Throwable) {
        failure?.addSuppressed(t) ?: run { failure = t }
    }
}