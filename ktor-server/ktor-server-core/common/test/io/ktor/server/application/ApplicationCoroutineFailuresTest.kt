/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.events.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAPI::class)
class ApplicationCoroutineFailuresTest {

    private fun createApplication(parentCoroutineContext: CoroutineExceptionHandler): Application {
        val engine = object : ApplicationEngine {
            override suspend fun resolvedConnectors(): List<EngineConnectorConfig> = emptyList()
            override val environment: ApplicationEnvironment get() = error("Not used")
            override fun start(wait: Boolean): ApplicationEngine = this
            override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {}
        }
        return Application(createTestEnvironment { }, false, "/", Events(), parentCoroutineContext) { engine }
    }

    @Test
    fun `application coroutine failures go to the failure handler first`() = runTestWithRealTime {
        val handled = CompletableDeferred<String>()
        val parent = CompletableDeferred<String>()
        val application = createApplication(CoroutineExceptionHandler { _, e -> parent.complete(e.message!!) })
        application.setCoroutineFailureHandler { _, e ->
            if (e !is IllegalStateException) return@setCoroutineFailureHandler false
            handled.complete(e.message!!)
            true
        }

        application.launch { error("handled") }
        assertEquals("handled", withTimeout(5.seconds) { handled.await() })

        application.launch { throw IllegalArgumentException("unhandled") }
        assertEquals("unhandled", withTimeout(5.seconds) { parent.await() })

        application.disposeAndJoin()
    }

    @Test
    fun `the engine's default handler passes failures to the application first`() {
        var handled: Throwable? = null
        val application = createApplication(CoroutineExceptionHandler { _, _ -> })
        application.setCoroutineFailureHandler { _, e ->
            handled = e
            true
        }
        val handler = DefaultUncaughtExceptionHandler({ application.log }, { application })

        val exception = kotlinx.io.IOException("connection reset")
        handler.handleException(CoroutineName("engine"), exception)

        assertSame(exception, handled)
    }
}
