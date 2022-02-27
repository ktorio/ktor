/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
    fun testPlugins() = testWithEngine(OkHttp) {
        config {
            engine {
                addInterceptor(LoggingInterceptor())
                addNetworkInterceptor(LoggingInterceptor())
            }
        }

        test { client ->
            client.get("https://google.com").body<String>()
        }
    }

    @Test
    fun testReusingRequestBuilderOnMultipleClients() {
        val requestBuilder = HttpRequestBuilder()
        requestBuilder.url.takeFrom("$testUrl/delay?delay=500")

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
            assertFailsWith<SocketTimeoutException> { clientFail.get(requestBuilder).body<HttpResponseData>() }

            val response = clientSuccess.get(requestBuilder).body<String>()
            assertEquals("OK", response)
        }
    }
}
