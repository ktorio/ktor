/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.server.testing.client.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.future.*
import kotlin.coroutines.*

/**
 * ktor test engine that provides way to simulate application calls to existing application module(s)
 * without actual HTTP connection
 */
public class TestApplicationEngine(
    environment: ApplicationEngineEnvironment = createTestEnvironment(),
    configure: Configuration.() -> Unit = {}
) : BaseApplicationEngine(environment, EnginePipeline(environment.developmentMode)), CoroutineScope {

    private val testEngineJob = Job(environment.parentCoroutineContext[Job])
    private var cancellationDeferred: CompletableJob? = null

    override val coroutineContext: CoroutineContext
        get() = testEngineJob

    /**
     * Test application engine configuration
     * @property dispatcher to run handlers and interceptors on
     */
    public class Configuration : BaseApplicationEngine.Configuration() {
        var dispatcher: CoroutineContext = Dispatchers.IO
    }

    private val configuration = Configuration().apply(configure)

    init {
        pipeline.intercept(EnginePipeline.Call) { callInterceptor(Unit) }
    }

    /**
     * interceptor for engine calls. can be modified to emulate certain engine behaviour (e.g. error handling)
     */
    var callInterceptor: PipelineInterceptor<Unit, ApplicationCall> =
        {
            try {
                call.application.execute(call)
            } catch (cause: Throwable) {
                handleTestFailure(cause)
            }
        }

    /**
     * An instance of client engine user to be used in [client].
     */
    val engine: HttpClientEngine = TestHttpClientEngine.create { app = this@TestApplicationEngine }

    /**
     * A client instance connected to this test server instance. Only works until engine stop invocation.
     */
    val client: HttpClient = HttpClient(engine)

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

    override fun start(wait: Boolean): ApplicationEngine {
        check(testEngineJob.isActive) { "Test engine is already completed" }
        environment.start()
        cancellationDeferred = stopServerOnCancellation()
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        try {
            cancellationDeferred?.complete()
            client.close()
            engine.close()
            environment.monitor.raise(ApplicationStopPreparing, environment)
            environment.stop()
        } finally {
            testEngineJob.cancel()
        }
    }

    private var processRequest: TestApplicationRequest.(setup: TestApplicationRequest.() -> Unit) -> Unit = { it() }
    private var processResponse: TestApplicationCall.() -> Unit = { }

    /**
     * Install a hook for test requests
     */
    public fun hookRequests(
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
    @OptIn(InternalAPI::class)
    public fun handleRequest(
        closeRequest: Boolean = true,
        setup: TestApplicationRequest.() -> Unit
    ): TestApplicationCall {
        val call = createCall(readResponse = true, closeRequest = closeRequest, setup = { processRequest(setup) })

        val context = configuration.dispatcher + SupervisorJob() + CoroutineName("request")
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

    private fun createWebSocketCall(uri: String, setup: TestApplicationRequest.() -> Unit): TestApplicationCall =
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
    public fun handleWebSocket(uri: String, setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
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

        pipelineExecuted.asCompletableFuture().join()

        return call
    }

    /**
     * Make a test request that setup a websocket session and invoke [callback] function
     * that does conversation with server
     */
    @OptIn(WebSocketInternalAPI::class)
    public fun handleWebSocketConversation(
        uri: String,
        setup: TestApplicationRequest.() -> Unit = {},
        callback: suspend TestApplicationCall.(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) -> Unit
    ): TestApplicationCall {
        val websocketChannel = ByteChannel(true)
        val call = createWebSocketCall(uri) {
            setup()
            bodyChannel = websocketChannel
        }

        // we need this to wait for response channel appearance
        // otherwise we get NPE at websocket reader start attempt
        val responseSent: CompletableJob = Job()
        call.response.responseChannelDeferred.invokeOnCompletion { cause ->
            when (cause) {
                null -> responseSent.complete()
                else -> responseSent.completeExceptionally(cause)
            }
        }

        launch(configuration.dispatcher) {
            try {
                // execute server-side
                pipeline.execute(call)
            } catch (t: Throwable) {
                responseSent.completeExceptionally(t)
                throw t
            }
        }

        val pool = KtorDefaultPool
        val engineContext = Dispatchers.Unconfined
        val job = Job()
        val webSocketContext = engineContext + job

        runBlocking(configuration.dispatcher) {
            responseSent.join()
            processResponse(call)

            val writer = WebSocketWriter(websocketChannel, webSocketContext, pool = pool)
            val responseChannel = call.response.websocketChannel()!!
            val reader = WebSocketReader(responseChannel, webSocketContext, Int.MAX_VALUE.toLong(), pool)

            try {
                // execute client side
                call.callback(reader.incoming, writer.outgoing)
            } finally {
                writer.flush()
                writer.outgoing.close()
                job.cancelAndJoin()
            }
        }

        return call
    }

    /**
     * Creates an instance of test call but doesn't start request processing
     */
    public fun createCall(
        readResponse: Boolean = false,
        closeRequest: Boolean = true,
        setup: TestApplicationRequest.() -> Unit
    ): TestApplicationCall =
        TestApplicationCall(application, readResponse, closeRequest, Dispatchers.IO).apply { setup(request) }
}

/**
 * Keep cookies between requests inside the [callback].
 *
 * This processes [HttpHeaders.SetCookie] from the responses and produce [HttpHeaders.Cookie] in subsequent requests.
 */
public fun TestApplicationEngine.cookiesSession(callback: () -> Unit) {
    var trackedCookies: List<Cookie> = listOf()

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
