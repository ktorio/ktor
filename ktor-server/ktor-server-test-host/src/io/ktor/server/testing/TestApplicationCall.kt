package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

class TestApplicationCall(
    application: Application, readResponse: Boolean = false,
    override val coroutineContext: CoroutineContext
) : BaseApplicationCall(application), CoroutineScope {
    @Volatile
    var requestHandled = false

    override val request: TestApplicationRequest = TestApplicationRequest(this)
    override val response: TestApplicationResponse = TestApplicationResponse(this, readResponse)

    override fun toString(): String = "TestApplicationCall(uri=${request.uri}) : handled = $requestHandled"
}
