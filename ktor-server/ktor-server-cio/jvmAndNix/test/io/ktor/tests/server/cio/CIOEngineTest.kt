// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.suites.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.test.*

class CIOHttpServerTest : HttpServerCommonTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableHttp2 = false
        enableSsl = PlatformUtils.IS_JVM
    }

    @Test
    fun testChunkedResponse() {
        createAndStartServer {
            get("/") {
                val byteStream = ByteChannel(autoFlush = true)
                byteStream.writeStringUtf8("test")
                byteStream.close(null)
                call.respond(object : OutgoingContent.ReadChannelContent() {
                    override val status: HttpStatusCode = HttpStatusCode.OK
                    override val headers: Headers = Headers.Empty
                    override fun readFrom() = byteStream
                })
            }
        }

        withUrl("/") {
            assertEquals("test", bodyAsText())
        }
    }
}
