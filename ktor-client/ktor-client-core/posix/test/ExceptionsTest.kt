/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*
import kotlin.test.*

class ExceptionsTest {

    @Test
    fun testResponseExceptionThreadLocal() {
        val exception = createResponseException()
            .freeze()

        val worker = Worker.start(errorReporting = true)

        worker.execute(TransferMode.SAFE, { exception }) {
            assertFails {
                it.response
            }
            Unit
        }.consume { }

        assertFalse(exception.response.isFrozen)
        worker.requestTermination(processScheduledJobs = true)
    }
}

private fun createResponseException(): ResponseException = ResponseException(
    object : HttpResponse() {
        override val call: HttpClientCall
            get() = TODO("Not yet implemented")
        override val status: HttpStatusCode
            get() = TODO("Not yet implemented")
        override val version: HttpProtocolVersion
            get() = TODO("Not yet implemented")
        override val requestTime: GMTDate
            get() = TODO("Not yet implemented")
        override val responseTime: GMTDate
            get() = TODO("Not yet implemented")
        override val content: ByteReadChannel
            get() = TODO("Not yet implemented")
        override val headers: Headers
            get() = TODO("Not yet implemented")
        override val coroutineContext: CoroutineContext
            get() = TODO("Not yet implemented")

        override fun toString(): String = "FakeCall"
    },
    cachedResponseText = "Fake text"
)
