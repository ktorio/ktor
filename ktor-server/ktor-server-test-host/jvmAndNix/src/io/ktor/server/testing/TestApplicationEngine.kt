/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.testing.client.*
import io.ktor.server.testing.internal.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.concurrent.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * ktor test engine that provides way to simulate application calls to existing application module(s)
 * without actual HTTP connection
 */
class TestApplicationEngine(
    environment: ApplicationEngineEnvironment = createTestEnvironment(),
    configure: Configuration.() -> Unit = {}
) : BaseApplicationEngine(environment, EnginePipeline(environment.developmentMode)), CoroutineScope {
    private val testEngineJob = Job(environment.parentCoroutineContext[Job])
    private var cancellationDeferred: CompletableJob? = null
    private val isStarted = atomic(false)

    override val coroutineContext: CoroutineContext = testEngineJob

    /**
     * An engine configuration for a test application
     * @property dispatcher to run handlers and interceptors on
     */
    class Configuration : BaseApplicationEngine.Configuration() {
        var dispatcher: CoroutineContext = Dispatchers.IOBridge
    }

    internal val configuration = Configuration().apply(configure)

    /**
     * interceptor for engine calls. can be modified to emulate certain engine behaviour (e.g. error handling)
     */
    private val _callInterceptor: AtomicRef<(suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit)?> =
        atomic(null)

    var callInterceptor: PipelineInterceptor<Unit, ApplicationCall>
        get() = _callInterceptor.value!!
        set(value) {
            _callInterceptor.value = value
        }

    /**
     * An instance of client engine user to be used in [client].
     */
    val engine: HttpClientEngine = TestHttpClientEngine.create { app = this@TestApplicationEngine }

    /**
     * A client instance connected to this test server instance. Only works until engine stop invocation.
     */
    private val _client = atomic<HttpClient?>(null)

    val client: HttpClient
        get() = _client.value!!

    private var processRequest: TestApplicationRequest.(setup: TestApplicationRequest.() -> Unit) -> Unit = {
        it()
    }

    internal var processResponse: TestApplicationCall.() -> Unit = { }

    init {
        pipeline.intercept(EnginePipeline.Call) { callInterceptor(Unit) }
        _client.value = HttpClient(engine)

        _callInterceptor.value = {
            try {
                call.application.execute(call)
            } catch (cause: Throwable) {
                handleTestFailure(cause)
            }
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.handleTestFailure(cause: Throwable) {
        tryRespondError(defaultExceptionStatusCode(cause) ?: throw cause)
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.tryRespondError(statusCode: HttpStatusCode) {
        try {
            if (call.response.status() == null) {
                call.respond(statusCode)
            }
        } catch (ignore: BaseApplicationResponse.ResponseAlreadySentException) {
        }
    }

    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> {
        return listOf(
            object : EngineConnectorConfig {
                override val type: ConnectorType = ConnectorType.HTTP
                override val host: String = environment.connectors.firstOrNull()?.host ?: "localhost"
                override val port: Int = environment.connectors.firstOrNull()?.port ?: 80
            },
            object : EngineConnectorConfig {
                override val type: ConnectorType = ConnectorType.HTTPS
                override val host: String = environment.connectors.firstOrNull()?.host ?: "localhost"
                override val port: Int = environment.connectors.firstOrNull()?.port ?: 443
            }
        )
    }

    override fun start(wait: Boolean): ApplicationEngine {
        if (isStarted.getAndSet(true)) {
            return this
        }
        check(testEngineJob.isActive) { "Test engine is already completed" }
        environment.start()
        cancellationDeferred = stopServerOnCancellation()
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        try {
            isStarted.value = false
            cancellationDeferred?.complete()
            client.close()
            engine.close()
            environment.monitor.raise(ApplicationStopPreparing, environment)
            environment.stop()
        } finally {
            testEngineJob.cancel()
        }
    }

    /**
     * Install a hook for test requests
     */
    fun hookRequests(
        processRequest: TestApplicationRequest.(setup: TestApplicationRequest.() -> Unit) -> Unit,
        processResponse: TestApplicationCall.() -> Unit,
        block: () -> Unit
    ) {
        val oldProcessRequest = this.processRequest
        val oldProcessResponse = this.processResponse
        this.processRequest = {
            oldProcessRequest {
                processRequest(it)
            }
        }
        this.processResponse = {
            oldProcessResponse()
            processResponse()
        }
        try {
            block()
        } finally {
            this.processResponse = oldProcessResponse
            this.processRequest = oldProcessRequest
        }
    }

    /**
     * Make a test request
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun handleRequest(
        closeRequest: Boolean = true,
        setup: TestApplicationRequest.() -> Unit
    ): TestApplicationCall {
        val job = Job()
        val call = createCall(
            readResponse = true,
            closeRequest = closeRequest,
            setup = { processRequest(setup) },
            context = Dispatchers.IOBridge + job
        )

        val context = configuration.dispatcher + SupervisorJob() + CoroutineName("request") + job
        val pipelineJob = GlobalScope.async(context) {
            pipeline.execute(call)
        }

        runBlocking(coroutineContext) {
            pipelineJob.await()
            call.response.awaitForResponseCompletion()
            context.cancel()
        }
        processResponse(call)

        return call
    }

    internal fun createWebSocketCall(uri: String, setup: TestApplicationRequest.() -> Unit): TestApplicationCall =
        createCall(closeRequest = false) {
            this.uri = uri
            addHeader(HttpHeaders.Connection, "Upgrade")
            addHeader(HttpHeaders.Upgrade, "websocket")
            addHeader(HttpHeaders.SecWebSocketKey, "test".toByteArray().encodeBase64())

            processRequest(setup)
        }

    /**
     * Make a test request that setup a websocket session and wait for completion
     */
    fun handleWebSocket(uri: String, setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val call = createWebSocketCall(uri, setup)

        // we can't simply do runBlocking here because runBlocking is not completing
        // until all children completion (writer is the most dangerous example that can cause deadlock here)
        val pipelineExecuted = CompletableDeferred<Unit>(coroutineContext[Job])
        launch(configuration.dispatcher) {
            try {
                pipeline.execute(call)
                pipelineExecuted.complete(Unit)
            } catch (cause: Throwable) {
                pipelineExecuted.completeExceptionally(cause)
            }
        }
        processResponse(call)

        runBlocking {
            pipelineExecuted.join()
        }

        return call
    }

    /**
     * Creates an instance of test call but doesn't start request processing
     */
    fun createCall(
        readResponse: Boolean = false,
        closeRequest: Boolean = true,
        context: CoroutineContext = Dispatchers.IOBridge,
        setup: TestApplicationRequest.() -> Unit
    ): TestApplicationCall = TestApplicationCall(application, readResponse, closeRequest, context).apply {
        setup(request)
    }
}

/**
 * Keep cookies between requests inside the [callback].
 *
 * This processes [HttpHeaders.SetCookie] from the responses and produce [HttpHeaders.Cookie] in subsequent requests.
 */
fun TestApplicationEngine.cookiesSession(callback: () -> Unit) {
    val trackedCookies: MutableList<Cookie> = mutableListOf()

    hookRequests(
        processRequest = { setup ->
            addHeader(
                HttpHeaders.Cookie,
                trackedCookies.joinToString("; ") {
                    (it.name).encodeURLParameter() + "=" + (it.value).encodeURLParameter()
                }
            )
            setup() // setup after setting the cookie so the user can override cookies
        },
        processResponse = {
            trackedCookies += response.headers.values(HttpHeaders.SetCookie).map { parseServerSetCookieHeader(it) }
        }
    ) {
        callback()
    }
}
