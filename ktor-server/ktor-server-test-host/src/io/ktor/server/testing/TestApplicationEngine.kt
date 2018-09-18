package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.util.cio.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.network.util.*
import io.ktor.util.pipeline.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import java.util.concurrent.*
import kotlin.coroutines.*

class TestApplicationEngine(
    environment: ApplicationEngineEnvironment = createTestEnvironment(),
    configure: Configuration.() -> Unit = {}
) : BaseApplicationEngine(environment, EnginePipeline()) {

    class Configuration : BaseApplicationEngine.Configuration() {
        var dispatcher: CoroutineContext = ioCoroutineDispatcher
    }

    private val configuration = Configuration().apply(configure)

    init {
        pipeline.intercept(EnginePipeline.Call) {
            call.application.execute(call)
        }
    }

    override fun start(wait: Boolean): ApplicationEngine {
        environment.start()
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        environment.monitor.raise(ApplicationStopPreparing, environment)
        environment.stop()
    }

    fun handleRequest(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val call = createCall(readResponse = true, setup = setup)

        val pipelineJob = launch(configuration.dispatcher) {
            pipeline.execute(call)
        }

        runBlocking {
            pipelineJob.join()
            pipelineJob.getCancellationException().cause?.let { throw it }
            call.response.flush()
        }

        return call
    }

    fun handleWebSocket(uri: String, setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val call = createCall {
            this.uri = uri
            addHeader(HttpHeaders.Connection, "Upgrade")
            addHeader(HttpHeaders.Upgrade, "websocket")
            addHeader(HttpHeaders.SecWebSocketKey, encodeBase64("test".toByteArray()))

            setup()
        }

        runBlocking(configuration.dispatcher) {
            pipeline.execute(call)
        }

        return call
    }

    fun handleWebSocketConversation(
        uri: String, setup: TestApplicationRequest.() -> Unit = {},
        callback: suspend TestApplicationCall.(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) -> Unit
    ): TestApplicationCall {
        val websocketChannel = ByteChannel(true)
        val call = handleWebSocket(uri) {
            setup()
            bodyChannel = websocketChannel
        }

        val pool = KtorDefaultPool
        val engineContext = Unconfined
        val job = Job()
        val writer = @Suppress("DEPRECATION") WebSocketWriter(websocketChannel, job, engineContext, pool = pool)
        val reader = @Suppress("DEPRECATION") WebSocketReader(
            call.response.websocketChannel()!!, Int.MAX_VALUE.toLong(), job, engineContext, pool
        )

        runBlocking(configuration.dispatcher) {
            call.callback(reader.incoming, writer.outgoing)
            writer.flush()
            writer.close()
            job.cancelAndJoin()
        }
        return call
    }

    fun createCall(readResponse: Boolean = false, setup: TestApplicationRequest.() -> Unit): TestApplicationCall =
        TestApplicationCall(application, readResponse).apply { setup(request) }
}