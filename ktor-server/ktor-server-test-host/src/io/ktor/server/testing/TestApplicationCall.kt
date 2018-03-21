package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.server.engine.*

class TestApplicationCall(application: Application, readResponse: Boolean = false) : BaseApplicationCall(application) {
    @Volatile
    var requestHandled = false

    override val request: TestApplicationRequest = TestApplicationRequest(this)
    override val response = TestApplicationResponse(this, readResponse)

    override fun toString(): String = "TestApplicationCall(uri=${request.uri}) : handled = $requestHandled"
}
