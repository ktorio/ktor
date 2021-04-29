/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.gson

import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import java.io.*
import java.lang.reflect.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.test.*

class GsonBlockingTest {
    private val dispatcher = UnsafeDispatcher()
    private val environment = createTestEnvironment {
        parentCoroutineContext = dispatcher
    }

    @AfterTest
    fun cleanup() {
        dispatcher.close()
    }

    @Test
    fun testReceive(): Unit = withApplication(environment) {
        application.intercept(ApplicationCallPipeline.Setup) {
            withContext(dispatcher) {
                proceed()
            }
        }
        application.install(ContentNegotiation) {
            gson()
        }
        application.routing {
            post("/") {
                assertEquals(K(77), call.receive())
                call.respondText("OK")
            }
        }

        runBlocking {
            assertEquals(
                "OK",
                client.post("/") {
                    body = "{\"i\": 77}"
                    contentType(ContentType.Application.Json)
                }
            )
        }
    }

    data class K(var i: Int)

    private class UnsafeDispatcher : CoroutineDispatcher(), Closeable {
        private val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatcher.dispatch(context + dispatcher) {
                markParkingProhibited()
                block.run()
            }
        }

        override fun close() {
            dispatcher.close()
        }

        private val prohibitParkingFunction: Method? by lazy {
            try {
                Class.forName("io.ktor.utils.io.jvm.javaio.PollersKt")
                    .getMethod("prohibitParking")
            } catch (cause: Throwable) {
                null
            }
        }

        private fun markParkingProhibited() {
            try {
                prohibitParkingFunction?.invoke(null)
            } catch (cause: Throwable) {
            }
        }
    }
}
