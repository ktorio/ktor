package org.jetbrains.ktor.testing

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

class TestApplicationHost(environment: ApplicationHostEnvironment = createTestEnvironment()) : BaseApplicationHost(environment, HostPipeline()) {
    init {
        pipeline.intercept(HostPipeline.Call) {
            call.application.execute(call)
        }
    }

    override fun start(wait: Boolean): ApplicationHost {
        environment.start()
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        environment.stop()
    }


    fun handleRequest(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val call = createCall(setup)
        runBlocking {
            pipeline.execute(call)
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

        runBlocking(Unconfined) { pipeline.execute(call) }

        return call
    }

    fun createCall(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        return TestApplicationCall(application).apply {
            setup(request)
        }
    }
}