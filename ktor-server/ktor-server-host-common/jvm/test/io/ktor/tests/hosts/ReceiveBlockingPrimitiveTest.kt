/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.hosts

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.*
import java.lang.reflect.*
import kotlin.concurrent.*
import kotlin.test.*

class ReceiveBlockingPrimitiveTest {
    @Test
    fun testBlockingPrimitiveUsuallyAllowed() {
        testOnThread { call ->
            call.receive<InputStream>().close()
        }
    }

    @Test
    fun testBlockingPrimitiveProhibitedOnRestrictedThread() {
        assertFailsWith<IllegalStateException> {
            testOnThread { call ->
                markParkingProhibited()

                call.receive<InputStream>().close()
            }
        }.let { cause ->
            assertTrue(cause.message!!.startsWith("Acquiring blocking primitives "))
        }
    }

    private fun testOnThread(
        block: suspend (ApplicationCall) -> Unit
    ) {
        val result = CompletableDeferred<Unit>()
        val call = TestCall()

        thread {
            try {
                runBlocking {
                    block(call)
                }
                result.complete(Unit)
            } catch (cause: Throwable) {
                result.completeExceptionally(cause)
            }
        }

        try {
            runBlocking {
                result.await()
            }
        } finally {
            call.close()
        }
    }

    private class TestCall : BaseApplicationCall(Application(applicationEngineEnvironment {})) {
        init {
            application.receivePipeline.installDefaultTransformations()
        }
        override val request: BaseApplicationRequest = object : BaseApplicationRequest(this) {
            override val queryParameters: Parameters
                get() = TODO("Not yet implemented")
            override val rawQueryParameters: Parameters
                get() = TODO("Not yet implemented")
            override val headers: Headers
                get() = TODO("Not yet implemented")
            override val local: RequestConnectionPoint
                get() = TODO("Not yet implemented")
            override val cookies: RequestCookies
                get() = TODO("Not yet implemented")

            override fun receiveChannel(): ByteReadChannel = ByteReadChannel.Empty
        }

        override val response: BaseApplicationResponse
            get() = error("Shouldn't be invoked")

        override fun afterFinish(handler: (Throwable?) -> Unit) {
            error("afterFinish is not available for TestCall")
        }

        fun close() {
            application.dispose()
        }
    }

    private val prohibitParkingFunction: Method? by lazy {
        Class.forName("io.ktor.utils.io.jvm.javaio.PollersKt")
            .getMethod("prohibitParking")
    }

    private fun markParkingProhibited() {
        prohibitParkingFunction?.invoke(null)
    }
}
