package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Represents a test application call that is used in [withTestApplication] and [handleRequest]
 */
class TestApplicationCall(
    application: Application, readResponse: Boolean = false,
    override val coroutineContext: CoroutineContext
) : BaseApplicationCall(application), CoroutineScope {

    /**
     * Set to `true` when the request has been handled and a response has been produced
     */
    @Volatile
    var requestHandled: Boolean = false
        internal set

    override val request: TestApplicationRequest = TestApplicationRequest(this)
    override val response: TestApplicationResponse = TestApplicationResponse(this, readResponse)

    override fun toString(): String = "TestApplicationCall(uri=${request.uri}) : handled = $requestHandled"

    init {
        putResponseAttribute()
    }
}
