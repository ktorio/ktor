package io.ktor.testing

import io.ktor.application.*
import io.ktor.host.*

class TestApplicationCall(application: Application) : BaseApplicationCall(application) {

    override val request: TestApplicationRequest = TestApplicationRequest(this)
    override val response = TestApplicationResponse(this)

    override fun toString(): String = "TestApplicationCall(uri=${request.uri}) : handled = $requestHandled"

    @Volatile
    var requestHandled = false
}