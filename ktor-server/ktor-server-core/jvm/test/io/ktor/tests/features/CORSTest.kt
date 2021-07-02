/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class CORSTest {

    @Test
    fun originValidation() {
        val feature = CORS(
            CORS.Configuration().apply {
                allowSameOrigin = false
                anyHost()
            }
        )

        assertEquals(OriginCheckResult.OK, feature.checkOrigin("hyp-hen://host", dummyPoint()))
        assertEquals(OriginCheckResult.OK, feature.checkOrigin("plus+://host", dummyPoint()))
        assertEquals(OriginCheckResult.OK, feature.checkOrigin("do.t://host", dummyPoint()))
        assertEquals(OriginCheckResult.OK, feature.checkOrigin("digits11://host", dummyPoint()))

        assertEquals(OriginCheckResult.SkipCORS, feature.checkOrigin("a()://host", dummyPoint()))
        assertEquals(OriginCheckResult.SkipCORS, feature.checkOrigin("1abc://host", dummyPoint()))
    }

    private fun dummyPoint(): RequestConnectionPoint {
        return getConnectionPoint("scheme", "host", 12345)
    }

    private fun getConnectionPoint(scheme: String, host: String, port: Int): RequestConnectionPoint {
        val point = mockk<RequestConnectionPoint>()
        every { point.scheme } returns scheme
        every { point.host } returns host
        every { point.port } returns port
        return point
    }

    @Test
    fun expectRequestHeadersCheckPassIfHeaderHasEmptyValue(): Unit = runBlocking {
        val pipeline = ApplicationCallPipeline()
        CORS.install(pipeline) {
            allowCredentials = true
            anyHost()
            allowSameOrigin = false
            method(HttpMethod.Options)
        }

        val app = mockk<Application> {
            every { environment } returns mockk {
                every { developmentMode } returns false
            }

            every { developmentMode } returns false
            every { receivePipeline } returns ApplicationReceivePipeline()
            every { sendPipeline } returns ApplicationSendPipeline()
        }

        val call = TestApplicationCall(app, coroutineContext = EmptyCoroutineContext)
        call.request.method = HttpMethod.Options
        call.request.addHeader("Origin", "http://localhost")
        call.request.addHeader("Access-Control-Request-Headers", "")
        call.request.addHeader("Access-Control-Request-Method", "OPTIONS")

        var called = false
        call.response.pipeline.intercept(ApplicationSendPipeline.Before) { message ->
            assertEquals(HttpStatusCode.OK, message)
            called = true
        }

        pipeline.execute(call)
        assertTrue(called)
    }
}
