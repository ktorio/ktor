/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

@OptIn(InternalAPI::class)
@PublicAPICandidate("2.2.0")
internal const val CONFIG_KEY_THROW_ON_EXCEPTION = "ktor.test.throwOnException"

/**
 * A test engine that provides a way to simulate application calls to the existing application module(s)
 * without actual HTTP connection.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationEngine)
 */
public class TestApplicationEngine(
    environment: ApplicationEnvironment = createTestEnvironment(),
    monitor: Events,
    developmentMode: Boolean = true,
    private val applicationProvider: () -> Application,
    internal val configuration: Configuration
) : BaseApplicationEngine(environment, monitor, developmentMode, EnginePipeline(developmentMode)), CoroutineScope {

    private val testEngineJob = Job(applicationProvider().parentCoroutineContext[Job])
    private var cancellationJob: CompletableJob? = null

    override val coroutineContext: CoroutineContext =
        applicationProvider().parentCoroutineContext + testEngineJob + configuration.dispatcher

    public val application: Application
        get() = applicationProvider()

    /**
     * An engine configuration for a test application.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationEngine.Configuration)
     *
     * @property dispatcher to run handlers and interceptors on
     */
    public class Configuration : BaseApplicationEngine.Configuration() {
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
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.testing.TestApplicationEngine.engine)
     */
    public val engine: HttpClientEngine = TestHttpClientEngine.create { app = this@TestApplicationEngine }

    /**
     * A client instance connected to this test server instance. Only works until engine stop invocation.
     */
    private val _client = atomic<HttpClient?>(null)

    public val client: HttpClient
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
        } catch (ignore: BaseApplicationResponse.ResponseAlreadySentException) {
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

    override fun start(wait: Boolean): ApplicationEngine {
        check(testEngineJob.isActive) { "Test engine is already completed" }
        cancellationJob = stopServerOnCancellation(
            applicationProvider(),
            configuration.shutdownGracePeriod,
            configuration.shutdownTimeout
        )
        launch(start = CoroutineStart.UNDISPATCHED) {
            resolvedConnectorsDeferred.complete(resolvedConnectors())
        }
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        try {
            cancellationJob?.complete()
            client.close()
            engine.close()
            monitor.raise(ApplicationStopPreparing, environment)
        } finally {
            testEngineJob.cancel()
        }
    }

    internal suspend fun handleRequest(
        closeRequest: Boolean = true,
        timeoutAttributes: HttpTimeoutConfig? = null,
        setup: TestApplicationRequest.() -> Unit
    ): TestApplicationCall {
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

    internal fun createWebSocketCall(uri: String, setup: TestApplicationRequest.() -> Unit): TestApplicationCall =
        createCall(closeRequest = false) {
            this.uri = uri
            addHeader(HttpHeaders.Connection, "Upgrade")
            addHeader(HttpHeaders.Upgrade, "websocket")
            addHeader(HttpHeaders.SecWebSocketKey, "test".toByteArray().encodeBase64())

            processRequest(setup)
        }

    /**
     * Creates an instance of a test call but doesn't start request processing.
     */
    private fun createCall(
        readResponse: Boolean = false,
        closeRequest: Boolean = true,
        context: CoroutineContext = Dispatchers.IOBridge,
        setup: TestApplicationRequest.() -> Unit
    ): TestApplicationCall = TestApplicationCall(applicationProvider(), readResponse, closeRequest, context).apply {
        setup(request)
    }
}

internal val timeoutAttributesKey = AttributeKey<HttpTimeoutConfig>("TimeoutAttributes")
