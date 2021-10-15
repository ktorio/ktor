/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class CallTest {
    @Test
    fun sendPipeline() = runBlocking {
        val fakeCall = FakeCall().apply {
            request.headers = HeadersBuilder().apply { append("my-header", "my-value") }.build()
        }

        val result = ApplicationSendPipeline().apply {
            intercept(ApplicationSendPipeline.Render) {
                proceedWith(call.request.headers["my-header"]!!)
            }
        }.execute(fakeCall, "")

        assertEquals("my-value", result)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun callPipeline(): Unit = runBlocking {
        val pipeline = ApplicationCallPipeline().apply {
            install(ContentNegotiation) {}

            receivePipeline.intercept(ApplicationReceivePipeline.Transform) {
                proceedWith(ApplicationReceiveRequest(typeInfo<Int>(), 123))
            }
        }

        val fakeCall = FakeCall().apply {
            request.headers = HeadersBuilder().apply { append("Content-Type", "application/x") }.build()
        }

        val result = pipeline.receivePipeline.execute(
            fakeCall,
            ApplicationReceiveRequest(typeInfo<Int>(), ByteReadChannel.Empty)
        )
        assertEquals(123, result.value)
    }
}
