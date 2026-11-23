/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.hosts

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.test.*

class IODispatcherConfigTest {

    @Test
    fun `default ioDispatcher is Dispatchers IO`() {
        assertSame(Dispatchers.IO, BaseApplicationEngine.Configuration().ioDispatcher)
    }

    @Test
    fun `takeFrom copies ioDispatcher`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val dispatcher = executor.asCoroutineDispatcher()
            val source = BaseApplicationEngine.Configuration().apply { ioDispatcher = dispatcher }
            val target = BaseApplicationEngine.Configuration()

            target.takeFrom(source)

            assertSame(dispatcher, target.ioDispatcher)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `application falls back to default dispatcher by default`() {
        val server = EmbeddedServer(serverConfig { developmentMode = false }, DummyEngineFactory)

        assertSame(Dispatchers.IO, server.application.ioDispatcher)
    }

    @Test
    fun `application exposes configured ioDispatcher`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val dispatcher = executor.asCoroutineDispatcher()
            val server = EmbeddedServer(serverConfig { developmentMode = false }, DummyEngineFactory) {
                ioDispatcher = dispatcher
            }

            assertSame(dispatcher, server.engineConfig.ioDispatcher)
            assertSame(dispatcher, server.application.ioDispatcher)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `configured ioDispatcher survives reload`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val dispatcher = executor.asCoroutineDispatcher()
            val server = EmbeddedServer(serverConfig { developmentMode = false }, DummyEngineFactory) {
                ioDispatcher = dispatcher
            }
            server.start()
            val before = server.application

            server.reload()
            val after = server.application

            assertNotSame(before, after)
            assertSame(dispatcher, after.ioDispatcher)
            server.stop()
        } finally {
            executor.shutdownNow()
        }
    }

    private object DummyEngineFactory :
        ApplicationEngineFactory<DummyEngine, BaseApplicationEngine.Configuration> {

        override fun configuration(
            configure: BaseApplicationEngine.Configuration.() -> Unit
        ): BaseApplicationEngine.Configuration = BaseApplicationEngine.Configuration().apply(configure)

        override fun create(
            environment: ApplicationEnvironment,
            monitor: Events,
            developmentMode: Boolean,
            configuration: BaseApplicationEngine.Configuration,
            applicationProvider: () -> Application
        ): DummyEngine = DummyEngine
    }

    private object DummyEngine : BaseApplicationEngine(applicationEnvironment { }, Events(), false) {
        override fun start(wait: Boolean): ApplicationEngine = this

        override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {}
    }
}
