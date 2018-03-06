package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import java.util.concurrent.*

class TestApplicationEngine(environment: ApplicationEngineEnvironment = createTestEnvironment(), configure: Configuration.() -> Unit = {}) : BaseApplicationEngine(environment, EnginePipeline()) {

    class Configuration : BaseApplicationEngine.Configuration()
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
        return createCall(setup).apply { execute(this) }
    }

    fun handleWebSocket(uri: String, setup: TestApplicationRequest.() -> Unit): TestApplicationCall = createCall {
        this.uri = uri
        addHeader(HttpHeaders.Connection, "Upgrade")
        addHeader(HttpHeaders.Upgrade, "websocket")
        addHeader(HttpHeaders.SecWebSocketKey, encodeBase64("test".toByteArray()))

        setup()
    }.apply { execute(this) }

    fun handleWebSocketConversation(
        uri: String, setup: TestApplicationRequest.() -> Unit = {},
        callback: suspend TestApplicationCall.(incoming: ReceiveChannel<Frame>, outgoing: SendChannel<Frame>) -> Unit
    ): TestApplicationCall {
        val bc = ByteChannel(true)
        val call = handleWebSocket(uri) {
            setup()
            this.bodyChannel = bc
        }

        val pool = KtorDefaultPool
        val engineContext = Unconfined
        val job = Job()
        val writer = @Suppress("DEPRECATION") WebSocketWriter(bc, job, engineContext, pool)
        val reader = @Suppress("DEPRECATION") WebSocketReader(call.response.contentChannel()!!, { Int.MAX_VALUE.toLong() }, job, engineContext, pool)

        runBlocking {
            call.callback(reader.incoming, writer.outgoing)
            job.cancelAndJoin()
        }
        return call
    }

    fun createCall(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        return TestApplicationCall(application).apply {
            setup(request)
        }
    }

    private fun execute(call: TestApplicationCall) = launch(Unconfined) {
        try {
            pipeline.execute(call)
        } catch (t: Throwable) {
            call.response.complete(t)
        } finally {
            call.response.complete()
        }
    }
}