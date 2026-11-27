/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class IODispatcherUsageTest {

    private lateinit var executor: ExecutorService
    private lateinit var customDispatcher: CountingDispatcher

    @BeforeTest
    fun setUp() {
        executor = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "custom-io-thread").apply { isDaemon = true }
        }
        customDispatcher = CountingDispatcher(executor.asCoroutineDispatcher())
    }

    @AfterTest
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `respondOutputStream runs producer on configured dispatcher`() = testApplication {
        engine { ioDispatcher = customDispatcher }
        var threadName: String? = null
        routing {
            get("/") {
                call.respondOutputStream {
                    threadName = Thread.currentThread().name
                    write("hello".toByteArray())
                }
            }
        }

        assertEquals("hello", client.get("/").bodyAsText())
        assertTrue(threadName?.startsWith("custom-io-thread") == true, "Unexpected thread: $threadName")
    }

    @Test
    fun `respondTextWriter runs producer on configured dispatcher`() = testApplication {
        engine { ioDispatcher = customDispatcher }
        var threadName: String? = null
        routing {
            get("/") {
                call.respondTextWriter {
                    threadName = Thread.currentThread().name
                    write("hello")
                }
            }
        }

        assertEquals("hello", client.get("/").bodyAsText())
        assertTrue(threadName?.startsWith("custom-io-thread") == true, "Unexpected thread: $threadName")
    }

    @Test
    fun `respond with WriteChannelContent runs on configured dispatcher`() = testApplication {
        engine { ioDispatcher = customDispatcher }
        var threadName: String? = null
        routing {
            get("/") {
                call.respond(object : OutgoingContent.WriteChannelContent() {
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        threadName = Thread.currentThread().name
                        channel.writeStringUtf8("hello")
                    }
                })
            }
        }

        assertEquals("hello", client.get("/").bodyAsText())
        assertTrue(threadName?.startsWith("custom-io-thread") == true, "Unexpected thread: $threadName")
    }

    @Test
    fun `respondFile reads file on configured dispatcher`() = testApplication {
        engine { ioDispatcher = customDispatcher }
        val file = tempFile("hello.txt", "file content")
        routing {
            get("/") { call.respondFile(file) }
        }

        assertEquals("file content", client.get("/").bodyAsText())
        assertTrue(
            customDispatcher.dispatchCount.get() > 0,
            "Expected the file to be read on the configured dispatcher"
        )
    }

    @Test
    fun `staticFiles reads file on configured dispatcher`() = testApplication {
        engine { ioDispatcher = customDispatcher }
        val dir = tempFile("hello.txt", "static content").parentFile
        routing {
            staticFiles("/static", dir)
        }

        assertEquals("static content", client.get("/static/hello.txt").bodyAsText())
        assertTrue(
            customDispatcher.dispatchCount.get() > 0,
            "Expected the file to be read on the configured dispatcher"
        )
    }

    @Test
    fun `respondOutputStream still works with default dispatcher`() = testApplication {
        routing {
            get("/") {
                call.respondOutputStream { write("default".toByteArray()) }
            }
        }

        assertEquals("default", client.get("/").bodyAsText())
        assertEquals(0, customDispatcher.dispatchCount.get())
    }

    private fun tempFile(name: String, content: String): File {
        val dir = createTempDirectory("io-dispatcher-test").toFile().apply { deleteOnExit() }
        return File(dir, name).apply {
            writeText(content)
            deleteOnExit()
        }
    }

    private class CountingDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
        val dispatchCount = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount.incrementAndGet()
            delegate.dispatch(context, block)
        }
    }
}
