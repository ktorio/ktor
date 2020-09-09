/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import okhttp3.*
import java.time.*
import kotlin.test.*

class RequestTests : TestWithKtor() {

    override val server = embeddedServer(CIO, serverPort) {
        routing {
            get("/delay") {
                val delay = call.parameters["delay"]!!.toLong()
                delay(delay)
                call.respondText("OK")
            }
        }
    }

    class LoggingInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            return response
        }
    }

    @Test
    fun testFeatures() = testWithEngine(OkHttp) {
        config {
            engine {
                addInterceptor(LoggingInterceptor())
                addNetworkInterceptor(LoggingInterceptor())
            }
        }

        test { client ->
            client.get<String>("https://google.com")
        }
    }

    @Test
    fun testReusingRequestBuilderOnMultipleClients() {
        val rb = HttpRequestBuilder()
        rb.url.takeFrom("http://0.0.0.0:$serverPort/delay?delay=500")

        val clientFail = HttpClient(OkHttp) {
            engine {
                config {
                    readTimeout(Duration.ofMillis(100)) // SocketTimeoutException
                }
            }
        }
        val clientSuccess = HttpClient(OkHttp) {
            engine {
                config {
                    readTimeout(Duration.ofMillis(1000)) // success
                }
            }
        }

        runBlocking {
            assertFailsWith<SocketTimeoutException> { clientFail.get<HttpResponseData>(rb) }

            val response = clientSuccess.get<String>(rb)
            assertEquals("OK", response)
        }
    }
}
