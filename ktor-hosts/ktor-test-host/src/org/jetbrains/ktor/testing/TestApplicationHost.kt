package org.jetbrains.ktor.testing

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.transform.*
import org.jetbrains.ktor.util.*

class TestApplicationHost(val environment: ApplicationEnvironment = emptyTestEnvironment()) {
    private val applicationLoader = ApplicationLoader(environment, false)

    init {
        applicationLoader.onBeforeInitializeApplication {
            install(ApplicationTransform).registerDefaultHandlers()
        }
    }

    val application: Application = applicationLoader.application
    private val hostPipeline = ApplicationCallPipeline()

    init {
        hostPipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            call.response.pipeline.intercept(ApplicationResponsePipeline.Before) {
                proceed()
                (call as? TestApplicationCall)?.requestHandled = true
            }

            application.execute(call)
        }
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
        application.dispose()
    }

    fun createCall(setup: TestApplicationRequest.() -> Unit): TestApplicationCall {
        return TestApplicationCall(application).apply {
            setup(request)
        }
    }
}