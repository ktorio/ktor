package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

class TestApplicationEngine(environment: ApplicationEngineEnvironment = createTestEnvironment()) : BaseApplicationEngine(environment, EnginePipeline()) {
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