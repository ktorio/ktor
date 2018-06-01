package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.junit.*
import org.junit.Test
import org.junit.rules.*
import java.io.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import kotlin.concurrent.*
import kotlin.jvm.*
import kotlin.test.*

class ServerSocketTest {
    private val exec = Executors.newCachedThreadPool()
    private var tearDown = false
    private val selector = ActorSelectorManager(exec.asCoroutineDispatcher())
    private var client: Pair<java.net.Socket, Thread>? = null
    private var serverSocket = CompletableDeferred<ServerSocket>()
    @Volatile
    private var server: Job? = null
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
        serverSocket.cancel()
        server?.cancel()

        runBlocking {
            serverSocket.join()
            server?.join()
        }

        selector.close()
        exec.shutdown()
        failure?.let { throw it }
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
        val job = launch(CommonPool, start = CoroutineStart.LAZY) {
            try {
                val server = aSocket(selector).tcp().bind(null)
                this@ServerSocketTest.serverSocket.complete(server)

                bound.countDown()

                loop@ while (failure == null && isActive) {
                    server.accept().use {
                        try {
                            block(it)
                        } catch (ignore: CancellationException) {
                        } catch (t: Throwable) {
                            addFailure(IOException("client handler failed", t))
                        }
                    }
                }
            } catch (ignore: CancellationException) {
            } catch (e: ClosedChannelException) {
            } catch (e: CancelledKeyException) {
            } catch (t: Throwable) {
                val e = IOException("server failed", t)
                this@ServerSocketTest.serverSocket.completeExceptionally(e)
                addFailure(e)
            }
        }
        server = job
        job.start()
    }

    private fun client(block: (java.net.Socket) -> Unit) {
        val address = runBlocking { serverSocket.await().localAddress }
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