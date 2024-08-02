/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.testing.client.*
import io.ktor.server.testing.internal.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@OptIn(InternalAPI::class)
@PublicAPICandidate("2.2.0")
internal const val CONFIG_KEY_THROW_ON_EXCEPTION = "ktor.test.throwOnException"

@Deprecated(message = "Renamed to TestServerEngine", replaceWith = ReplaceWith("TestServerEngine"))
public typealias TestApplicationEngine = TestServerEngine

/**
 * A test engine that provides a way to simulate application calls to the existing application module(s)
 * without actual HTTP connection.
 */
public class TestServerEngine(
    environment: ServerEnvironment = createTestEnvironment(),
    monitor: Events,
    developmentMode: Boolean = true,
    private val serverProvider: () -> Server,
    internal val configuration: Configuration
) : BaseServerEngine(environment, monitor, developmentMode, EnginePipeline(developmentMode)), CoroutineScope {

    private val testEngineJob = Job(serverProvider().parentCoroutineContext[Job])
    private var cancellationDeferred: CompletableJob? = null

    override val coroutineContext: CoroutineContext =
        serverProvider().parentCoroutineContext + testEngineJob + configuration.dispatcher

    public val server: Server
        get() = serverProvider()

    /**
     * An engine configuration for a test application.
     * @property dispatcher to run handlers and interceptors on
     */
    public class Configuration : BaseServerEngine.Configuration() {
        public var dispatcher: CoroutineContext = Dispatchers.IOBridge

        init {
            shutdownGracePeriod = 0
            shutdownGracePeriod = 0
        }
    }

    /**
     * An interceptor for engine calls.
     * Can be modified to emulate behaviour of a specific engine (e.g. error handling).
     */
    private val _callInterceptor: AtomicRef<(suspend PipelineContext<Unit, PipelineCall>.(Unit) -> Unit)?> =
        atomic(null)

    public var callInterceptor: PipelineInterceptor<Unit, PipelineCall>
        get() = _callInterceptor.value!!
        set(value) {
            _callInterceptor.value = value
        }

    /**
     * An instance of a client engine to be used in [client].
     */
    public val engine: HttpClientEngine = TestHttpClientEngine.create { app = this@TestServerEngine }

    /**
     * A client instance connected to this test server instance. Only works until engine stop invocation.
     */
    private val _client = atomic<HttpClient?>(null)

    public val client: HttpClient
        get() = _client.value!!

    private var processRequest: TestServerRequest.(setup: TestServerRequest.() -> Unit) -> Unit = {
        it()
    }

    internal var processResponse: TestServerCall.() -> Unit = { }

    init {
        pipeline.intercept(EnginePipeline.Call) { callInterceptor(Unit) }
        _client.value = HttpClient(engine)

        _callInterceptor.value = {
            try {
                call.server.execute(call)
            } catch (cause: Throwable) {
                handleTestFailure(cause)
            }
        }
    }

    private suspend fun PipelineContext<Unit, PipelineCall>.handleTestFailure(cause: Throwable) {
        logError(call, cause)

        val throwOnException = environment.config
            .propertyOrNull(CONFIG_KEY_THROW_ON_EXCEPTION)
            ?.getString()?.toBoolean() ?: true
        tryRespondError(
            defaultExceptionStatusCode(cause)
                ?: if (throwOnException) throw cause else HttpStatusCode.InternalServerError
        )
    }

    private suspend fun PipelineContext<Unit, PipelineCall>.tryRespondError(statusCode: HttpStatusCode) {
        try {
            call.respond(statusCode)
        } catch (ignore: BaseServerResponse.ResponseAlreadySentException) {
        }
    }

    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> {
        if (configuration.connectors.isNotEmpty()) {
            return configuration.connectors
        }
        return listOf(
            object : EngineConnectorConfig {
                override val type: ConnectorType = ConnectorType.HTTP
                override val host: String = "localhost"
                override val port: Int = 80
            },
            object : EngineConnectorConfig {
                override val type: ConnectorType = ConnectorType.HTTPS
                override val host: String = "localhost"
                override val port: Int = 443
            }
        )
    }

    override fun start(wait: Boolean): ServerEngine {
        check(testEngineJob.isActive) { "Test engine is already completed" }
        cancellationDeferred = stopServerOnCancellation(
            serverProvider(),
            configuration.shutdownGracePeriod,
            configuration.shutdownTimeout
        )
        resolvedConnectors.complete(runBlocking { resolvedConnectors() })

        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        try {
            cancellationDeferred?.complete()
            client.close()
            engine.close()
            monitor.raise(ServerStopPreparing, environment)
        } finally {
            testEngineJob.cancel()
        }
    }

    /**
     * Installs a hook for test requests.
     */
    public fun hookRequests(
        processRequest: TestServerRequest.(setup: TestServerRequest.() -> Unit) -> Unit,
        processResponse: TestServerCall.() -> Unit,
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
     * Makes a test request.
     */
    @OptIn(DelicateCoroutinesApi::class)
    public fun handleRequest(
        closeRequest: Boolean = true,
        setup: TestServerRequest.() -> Unit
    ): TestServerCall {
        val callJob = GlobalScope.async(coroutineContext) {
            handleRequestNonBlocking(closeRequest, timeoutAttributes = null, setup)
        }

        return runBlocking { callJob.await() }
    }

    internal suspend fun handleRequestNonBlocking(
        closeRequest: Boolean = true,
        timeoutAttributes: HttpTimeoutConfig? = null,
        setup: TestServerRequest.() -> Unit
    ): TestServerCall {
        val job = Job(testEngineJob)
        val call = createCall(
            readResponse = true,
            closeRequest = closeRequest,
            setup = { processRequest(setup) },
            context = Dispatchers.IOBridge + job
        )
        if (timeoutAttributes != null) {
            call.attributes.put(timeoutAttributesKey, timeoutAttributes)
        }

        val context = SupervisorJob(job) + CoroutineName("request")
        withContext(coroutineContext + context) {
            pipeline.execute(call)
            call.response.awaitForResponseCompletion()
        }
        context.cancel()
        processResponse(call)

        return call
    }

    internal fun createWebSocketCall(uri: String, setup: TestServerRequest.() -> Unit): TestServerCall =
        createCall(closeRequest = false) {
            this.uri = uri
            addHeader(HttpHeaders.Connection, "Upgrade")
            addHeader(HttpHeaders.Upgrade, "websocket")
            addHeader(HttpHeaders.SecWebSocketKey, "test".toByteArray().encodeBase64())

            processRequest(setup)
        }

    /**
     * Makes a test request that sets up a websocket session and waits for completion.
     */
    public fun handleWebSocket(uri: String, setup: TestServerRequest.() -> Unit): TestServerCall {
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
     * Creates an instance of a test call but doesn't start request processing.
     */
    public fun createCall(
        readResponse: Boolean = false,
        closeRequest: Boolean = true,
        context: CoroutineContext = Dispatchers.IOBridge,
        setup: TestServerRequest.() -> Unit
    ): TestServerCall = TestServerCall(serverProvider(), readResponse, closeRequest, context).apply {
        setup(request)
    }
}

/**
 * Keeps cookies between requests inside the [callback].
 *
 * This processes [HttpHeaders.SetCookie] from the responses and produce [HttpHeaders.Cookie] in subsequent requests.
 */
public fun TestServerEngine.cookiesSession(callback: () -> Unit) {
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

internal val timeoutAttributesKey = AttributeKey<HttpTimeoutConfig>("TimeoutAttributes")
