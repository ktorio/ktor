/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

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
import io.ktor.util.collections.*
import io.ktor.util.logging.*
import io.ktor.utils.io.concurrent.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

private val TEST_SELECTOR_MANAGER = SelectorManager()

public actual abstract class EngineTestBase<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    >
actual constructor(
    public actual val applicationEngineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
) : BaseTest(), CoroutineScope {
    private val testJob = Job()
    actual override val coroutineContext: CoroutineContext = testJob + Dispatchers.Default

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected actual annotation class Http2Only actual constructor()

    protected actual var port: Int = aSocket(TEST_SELECTOR_MANAGER).tcp().bind().use {
        val inetAddress = it.localAddress as? InetSocketAddress ?: error("Expected inet socket address")
        inetAddress.port
    }

    protected actual var sslPort: Int = 0
    protected actual var server: TEngine? = null

    protected actual var enableHttp2: Boolean = false
    protected actual var enableSsl: Boolean = false
    protected actual var enableCertVerify: Boolean = false

    protected actual fun createAndStartServer(
        log: Logger?,
        parent: CoroutineContext,
        routingConfigurer: Routing.() -> Unit
    ): TEngine {
        var lastFailures = emptyList<Throwable>()
        for (attempt in 1..5) {
            val server = createServer(log, parent) {
                plugins(this, routingConfigurer)
            }

            lastFailures = startServer(server)
            if (lastFailures.isEmpty()) {
                return server
            }

            server.stop(1L, 1L)
        }

        error(lastFailures)
    }

    protected open fun createServer(
        log: Logger? = null,
        parent: CoroutineContext = EmptyCoroutineContext,
        module: Application.() -> Unit
    ): TEngine {
        val _port = this.port
        val environment = applicationEngineEnvironment {
            this.parentCoroutineContext = parent
            val delegate = KtorSimpleLogger("io.ktor.test")
            this.log = log ?: object : Logger by delegate {
                override fun error(message: String, cause: Throwable) {
                    collectUnhandledException(cause)
                    println("Critical test exception: $cause")
                    cause.printStackTrace()
                    println("From origin:")
                    Exception().printStackTrace()
                    delegate.error(message, cause)
                }
            }

            connector { port = _port }
            module(module)
        }

        return embeddedServer(applicationEngineFactory, environment)
    }

    @OptIn(DelicateCoroutinesApi::class)
    protected fun startServer(server: TEngine): List<Throwable> {
        this.server = server

        // we start it on the global scope because we don't want it to fail the whole test
        // as far as we have retry loop on call side
        val starting = GlobalScope.async {
            server.start(wait = false)
            delay(500)
        }

        return try {
            runBlocking {
                starting.join()
                @OptIn(ExperimentalCoroutinesApi::class)
                starting.getCompletionExceptionOrNull()?.let { listOf(it) } ?: emptyList()
            }
        } catch (t: Throwable) { // InterruptedException?
            starting.cancel()
            listOf(t)
        }
    }

    protected actual open fun plugins(application: Application, routingConfigurer: Routing.() -> Unit) {
        application.install(Routing, routingConfigurer)
    }

    protected actual fun withUrl(
        path: String,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ) {
        withUrl("http://127.0.0.1:$port$path", port, builder, block)
    }

    private fun withUrl(
        urlString: String,
        port: Int,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ): Unit = runTest {
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
