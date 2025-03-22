/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.time.Duration.Companion.seconds

private val TEST_SELECTOR_MANAGER = SelectorManager()

actual abstract class EngineTestBase<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    >
actual constructor(
    actual val applicationEngineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
) : BaseTest(), CoroutineScope {
    private val testJob = Job()
    actual override val coroutineContext: CoroutineContext = testJob + Dispatchers.Default

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected actual annotation class Http2Only actual constructor()

    protected actual var port: Int = findFreePort()

    private fun findFreePort(): Int = runBlocking {
        val socket = aSocket(TEST_SELECTOR_MANAGER).tcp().bind()
        val port = socket.use { it.port }
        socket.awaitClosed()
        port
    }

    protected actual var sslPort: Int = 0
    protected actual var server: EmbeddedServer<TEngine, TConfiguration>? = null

    protected actual var enableHttp2: Boolean = false
    protected actual var enableSsl: Boolean = false
    protected actual var enableCertVerify: Boolean = false

    override fun afterTest() {
        try {
            server?.stop(0, 500)
        } finally {
            testJob.cancel()
            super.afterTest()
        }
    }

    protected actual suspend fun createAndStartServer(
        log: Logger?,
        parent: CoroutineContext,
        routingConfigurer: Route.() -> Unit
    ): EmbeddedServer<TEngine, TConfiguration> {
        var lastFailures = emptyList<Throwable>()
        for (attempt in 1..5) {
            val server = createServer(log, parent) {
                plugins(this, routingConfigurer)
            }

            lastFailures = startServer(server)
            if (lastFailures.isEmpty()) {
                return server
            }

            port = findFreePort()
            server.stop(1L, 1L)
        }

        error(lastFailures)
    }

    protected open fun createServer(
        log: Logger? = null,
        parent: CoroutineContext = EmptyCoroutineContext,
        module: Application.() -> Unit
    ): EmbeddedServer<TEngine, TConfiguration> {
        val savedPort = this.port
        val environment = applicationEnvironment {
            val delegate = KtorSimpleLogger("io.ktor.test")
            this.log = log ?: object : Logger by delegate {
                override fun error(message: String, cause: Throwable) {
                    if (cause is ExpectedTestException) return
                    collectUnhandledException(cause)
                    println("Critical test exception: $cause")
                    cause.printStackTrace()
                    println("From origin:")
                    Exception().printStackTrace()
                    delegate.error(message, cause)
                }
            }
        }
        val properties = serverConfig(environment) {
            this.parentCoroutineContext = parent
            module(module)
        }

        return embeddedServer(applicationEngineFactory, properties) {
            connector { port = savedPort }
            shutdownGracePeriod = 1000
            shutdownTimeout = 1000
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    protected actual suspend fun startServer(server: EmbeddedServer<TEngine, TConfiguration>): List<Throwable> {
        this.server = server

        // we start it on the global scope because we don't want it to fail the whole test
        // as far as we have retry loop on call side
        val starting = GlobalScope.async {
            server.start(wait = false)
            // await for a server to be started
            server.engine.resolvedConnectors()
        }

        return try {
            starting.join()
            @OptIn(ExperimentalCoroutinesApi::class)
            starting.getCompletionExceptionOrNull()?.let { listOf(it) } ?: emptyList()
        } catch (t: Throwable) {
            starting.cancel()
            listOf(t)
        }
    }

    protected actual open fun plugins(application: Application, routingConfig: Route.() -> Unit) {
        application.install(RoutingRoot, routingConfig)
    }

    protected actual suspend fun withUrl(
        path: String,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ) {
        withUrl("http://127.0.0.1:$port$path", port, builder, block)
    }

    private suspend fun withUrl(
        urlString: String,
        port: Int,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ) {
        HttpClient(CIO) {
            followRedirects = false
            expectSuccess = false

            install(HttpTimeout) {
                requestTimeoutMillis = 30.seconds.inWholeMilliseconds
            }
        }.use { client ->
            client.prepareRequest {
                url.takeFrom(urlString)
                builder()
            }.execute { response ->
                block(response, port)
            }
        }
    }
}
