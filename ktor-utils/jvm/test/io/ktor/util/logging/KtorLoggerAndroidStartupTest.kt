/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

import io.ktor.util.*
import org.junit.jupiter.api.io.TempDir
import org.slf4j.ILoggerFactory
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLogger
import org.slf4j.helpers.NOP_FallbackServiceProvider
import org.slf4j.spi.SLF4JServiceProvider
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.tools.ToolProvider
import kotlin.test.*

class KtorLoggerAndroidStartupTest {

    @TempDir
    lateinit var stubDirectory: Path

    @BeforeTest
    fun compileAndroidStub() {
        val source = Files.createDirectories(stubDirectory.resolve("android/os")).resolve("Build.java")
        Files.write(source, "package android.os; public class Build {}".toByteArray())
        val compiler = assertNotNull(ToolProvider.getSystemJavaCompiler(), "This test requires a JDK")
        assertEquals(0, compiler.run(null, null, null, "-d", stubDirectory.toString(), source.toString()))
    }

    @Test
    fun `logger uses the SLF4J provider after initialization on Android`() {
        val services = Files.createDirectories(stubDirectory.resolve("META-INF/services"))
        val descriptor = services.resolve(SLF4JServiceProvider::class.java.name)
        Files.write(descriptor, BlockingProvider::class.java.name.toByteArray())

        val app = ProviderApp::class.java
        val classpath = listOf(
            app,
            Attributes::class.java,
            LoggerFactory::class.java,
            Unit::class.java,
            Asserter::class.java
        ).map { it.protectionDomain.codeSource.location } + stubDirectory.toUri().toURL()
        // Load SLF4J separately so it binds to the test provider.
        URLClassLoader(classpath.toTypedArray(), null).use { loader ->
            val thread = Thread.currentThread()
            val previousContextLoader = thread.contextClassLoader
            // kotlin.test discovers its asserter using the thread context class loader.
            thread.contextClassLoader = loader
            try {
                (loader.loadClass(app.name).getDeclaredConstructor().newInstance() as Runnable).run()
            } finally {
                thread.contextClassLoader = previousContextLoader
            }
        }
    }

    class ProviderApp : Runnable {
        override fun run() {
            val logger = KtorSimpleLogger("io.ktor.app")
            val worker = BlockingProvider.bindingThread.poll(10, TimeUnit.SECONDS)
            try {
                assertNotNull(worker, "SLF4J binding did not start")
                assertFalse(logger.isInfoEnabled, "INFO should be disabled while SLF4J is binding")
                logger.info("during binding")
            } finally {
                BlockingProvider.finishBinding.countDown()
                worker?.join(10_000)
            }
            assertFalse(worker.isAlive, "SLF4J binding did not finish")
            assertTrue(logger.isInfoEnabled, "INFO should be enabled by the bound provider")
            logger.info("after binding")
            assertEquals(listOf("after binding"), BlockingProvider.messages.toList())
        }
    }

    class BlockingProvider : NOP_FallbackServiceProvider() {
        override fun initialize() {
            bindingThread.add(Thread.currentThread())
            assertTrue(finishBinding.await(10, TimeUnit.SECONDS), "Test did not release SLF4J binding")
        }

        override fun getLoggerFactory(): ILoggerFactory = ILoggerFactory {
            object : Logger by NOPLogger.NOP_LOGGER {
                override fun isInfoEnabled(): Boolean = true

                override fun info(msg: String?) {
                    messages += assertNotNull(msg)
                }
            }
        }

        companion object {
            val bindingThread = LinkedBlockingQueue<Thread>()
            val finishBinding = CountDownLatch(1)
            val messages = ConcurrentLinkedQueue<String>()
        }
    }
}
