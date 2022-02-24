/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.future.*
import java.net.http.*
import java.net.http.HttpHeaders
import java.nio.*
import kotlin.coroutines.*
import kotlin.test.*

class ResponseConsumerTest {
    @Test
    fun testConsumeContent() {
        val responseBodyHandler = JavaHttpResponseBodyHandler(EmptyCoroutineContext)
        val bodySubscriber = responseBodyHandler.apply(
            object : HttpResponse.ResponseInfo {
                override fun statusCode() = 200

                override fun headers() = HttpHeaders.of(
                    mapOf(
                        io.ktor.http.HttpHeaders.ContentType to listOf("text/plain"),
                        io.ktor.http.HttpHeaders.ContentLength to listOf("4")
                    )
                ) { _, _ -> true }

                override fun version() = HttpClient.Version.HTTP_2
            }
        )

        runBlocking {
            bodySubscriber.onNext(
                mutableListOf(
                    ByteBuffer.wrap("ktor".toByteArray())
                )
            )
            delay(50)
            bodySubscriber.onComplete()

            val responseBody = bodySubscriber.body.toCompletableFuture().await()

            assertEquals(200, responseBody.statusCode.value)
            assertEquals("4", responseBody.headers[io.ktor.http.HttpHeaders.ContentLength])
            assertEquals("text/plain", responseBody.headers[io.ktor.http.HttpHeaders.ContentType])
            assertEquals(HttpProtocolVersion.HTTP_2_0, responseBody.version)
            assertEquals("ktor", (responseBody.body as ByteReadChannel).readUTF8Line())
        }
    }
}
