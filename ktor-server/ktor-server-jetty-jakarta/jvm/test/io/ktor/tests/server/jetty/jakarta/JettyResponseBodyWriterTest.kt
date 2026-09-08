/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.server.jetty.jakarta.bodyWriter
import io.ktor.utils.io.writeByteArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.util.Callback
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class JettyResponseBodyWriterTest {
    @Test
    fun `failed write completes body writer job`() = runTest {
        val callback = CompletableDeferred<Callback>()
        val response = responseProxy { buffer, writeCallback ->
            if (buffer.hasRemaining()) {
                callback.complete(writeCallback)
            } else {
                writeCallback.succeeded()
            }
        }
        val writer = bodyWriter(response)

        writer.channel.writeByteArray(byteArrayOf(1))
        writer.channel.flush()

        callback.await().failed(Exception("client aborted write"))

        withTimeout(5.seconds) {
            writer.job.join()
        }
        assertTrue(writer.job.isCompleted)
    }
}

private fun responseProxy(write: (ByteBuffer, Callback) -> Unit): Response =
    Proxy.newProxyInstance(
        Response::class.java.classLoader,
        arrayOf(Response::class.java)
    ) { _, method, arguments ->
        when (method.name) {
            "write" -> write(arguments!![1] as ByteBuffer, arguments[2] as Callback)
            "isCommitted" -> true
            else -> error("Unexpected Response.${method.name} call")
        }
    } as Response
