/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.*
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.random.*
import kotlin.test.*

public actual abstract class EngineTestBase<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
actual constructor(
    public val applicationEngineFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : CoroutineScope {
    private val testJob = Job()

    @OptIn(ExperimentalCoroutinesApi::class)
    protected val testDispatcher by lazy {
        newSingleThreadContext("test-dispatcher")
    }

    protected actual var port: Int by atomic(Random.nextInt(8000, 9000))
    protected actual var sslPort: Int by atomic(-1) //should be not used
    protected actual var server: TEngine? by atomic(null)
    protected actual var callGroupSize: Int by atomic(-1)
    protected actual val exceptions: MutableList<Throwable> = ConcurrentList()
    protected actual var enableHttp2: Boolean by atomic(false)
    protected actual var enableSsl: Boolean by atomic(false)

    public actual val testLog: Logger = LoggerFactory.getLogger("EngineTestBase")

    override actual val coroutineContext: CoroutineContext
        get() = testJob + testDispatcher

    public open val timeout: Long = 4 * 60 * 1000

    @BeforeTest
    public fun setUpBase() {
        testLog.trace("Starting server on port $port")
        exceptions.clear()
    }

    @AfterTest
    public fun tearDownBase() {
        try {
            testLog.trace("Disposing server on port $port)")
            (server as? ApplicationEngine)?.stop(1000, 5000)
            if (exceptions.isNotEmpty()) {
                fail("Server exceptions logged, consult log output for more information")
            }
        } finally {
            testJob.cancel()
            testJob.invokeOnCompletion {
                testDispatcher.close()
            }
        }
    }

    protected actual open fun createServer(
        log: Logger?,
        parent: CoroutineContext,
        module: Application.() -> Unit
    ): TEngine {
        val _port = this.port
        val environment = applicationEngineEnvironment {
            this.parentCoroutineContext = parent
            val delegate = LoggerFactory.getLogger("ktor.test")
            this.log = log ?: object : Logger by delegate {
                override fun error(message: String, cause: Throwable) {
                    exceptions.add(cause)
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

        return embeddedServer(applicationEngineFactory, environment) {
            configure(this)
            this@EngineTestBase.callGroupSize = callGroupSize
        }
    }

    protected actual open fun configure(configuration: TConfiguration) {
        // Empty, intended to be override in derived types when necessary
    }

    protected actual open fun features(application: Application, routingConfigurer: Routing.() -> Unit) {
        application.install(CallLogging)
        application.install(Routing, routingConfigurer)
    }

    protected actual fun createAndStartServer(
        log: Logger?,
        parent: CoroutineContext,
        routingConfigurer: Routing.() -> Unit
    ): TEngine {
        for (attempt in 1..5) {
            val server = createServer(log, parent) {
                features(this, routingConfigurer)
            }

            val failures = startServer(server)
            when {
                failures.isEmpty() -> return server
                else -> {
                    server.stop(1000L, 1000L)
                    error(failures.toString()) //TODO
                }
            }
        }
        error("") //TODO
    }

    protected actual fun startServer(server: TEngine): List<Throwable> {
        this.server = server

        // we start it on the global scope because we don't want it to fail the whole test
        // as far as we have retry loop on call side
        val starting = GlobalScope.async(testDispatcher) {
            server.start(wait = false)
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
    ) = runBlocking {
        withTimeout(timeout * 1000) {
            HttpClient(CIO) {
                followRedirects = false
                expectSuccess = false
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

    protected actual fun createServer(
        module: Application.() -> Unit
    ): TEngine = createServer(null, EmptyCoroutineContext, module)

    protected actual fun createServer(
        log: Logger?,
        module: Application.() -> Unit
    ): TEngine= createServer(log, EmptyCoroutineContext, module)

    protected actual fun createServer(
        parent: CoroutineContext,
        module: Application.() -> Unit
    ): TEngine= createServer(null, parent, module)


    protected actual fun createAndStartServer(
        routingConfigurer: Routing.() -> Unit
    ): TEngine = createAndStartServer(null, EmptyCoroutineContext, routingConfigurer)

    protected actual fun createAndStartServer(
        parent: CoroutineContext,
        routingConfigurer: Routing.() -> Unit
    ): TEngine = createAndStartServer(null, parent, routingConfigurer)

    protected actual fun createAndStartServer(
        log: Logger?,
        routingConfigurer: Routing.() -> Unit
    ): TEngine = createAndStartServer(log, EmptyCoroutineContext, routingConfigurer)


    protected actual fun withUrl(
        path: String,
        block: suspend HttpResponse.(Int) -> Unit
    ) = withUrl(path, {}, block)


}
