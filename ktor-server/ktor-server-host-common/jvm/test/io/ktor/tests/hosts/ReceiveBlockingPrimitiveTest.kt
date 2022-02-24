/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.hosts

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.server.engine.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.io.*
import java.lang.reflect.*
import kotlin.concurrent.*
import kotlin.reflect.*
import kotlin.test.*

@OptIn(ExperimentalStdlibApi::class)
class ReceiveBlockingPrimitiveTest {
    private val pipeline = ApplicationReceivePipeline()

    init {
        pipeline.installDefaultTransformations()
    }

    @Test
    fun testBlockingPrimitiveUsuallyAllowed() {
        testOnThread { call ->
            receiveInputStream(pipeline, call)
        }
    }

    @Test
    fun testBlockingPrimitiveProhibitedOnRestrictedThread() {
        assertFailsWith<IllegalStateException> {
            testOnThread { call ->
                markParkingProhibited()

                receiveInputStream(pipeline, call)
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

    private suspend fun receiveInputStream(
        pipeline: ApplicationReceivePipeline,
        call: ApplicationCall
    ) {
        val request = ApplicationReceiveRequest(typeOf<InputStream>(), ByteChannel())
        val transformed = pipeline.execute(call, request)
        val stream = transformed.value as InputStream
        @Suppress("BlockingMethodInNonBlockingContext")
        stream.close()
    }

    private class TestCall : BaseApplicationCall(Application(applicationEngineEnvironment {})) {
        override val request: BaseApplicationRequest
            get() = error("Shouldn't be invoked")
        override val response: BaseApplicationResponse
            get() = error("Shouldn't be invoked")

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
