package org.jetbrains.ktor.testing

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.transform.*
import org.jetbrains.ktor.util.*

class TestApplicationHost(val environment: ApplicationHostEnvironment = emptyTestEnvironment()) {
    init {
        environment.monitor.applicationStart += {
            it.install(ApplicationTransform).registerDefaultHandlers()
        }
    }

    val application: Application get() = environment.application
    private val hostPipeline = ApplicationCallPipeline()

    init {
        hostPipeline.intercept(ApplicationCallPipeline.Call) { call ->
            call.response.pipeline.intercept(ApplicationResponsePipeline.Before) {
                proceed()
                (call as? TestApplicationCall)?.requestHandled = true
            }

            application.execute(call)
        }
        environment.start()
    }

    fun handleRequest(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        val call = createCall(setup)
        runBlocking {
            hostPipeline.execute(call)
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

        runBlocking(Unconfined) { hostPipeline.execute(call) }

        return call
    }

    fun dispose() {
        environment.stop()
    }

    fun createCall(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        return TestApplicationCall(application).apply {
            setup(request)
        }
    }
}