/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.benchmarks.cio

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.server.benchmarks.*
import io.ktor.server.cio.*
import io.netty.util.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*

class CIOPlatformBenchmark : PlatformBenchmark() {
    private var server: HttpServer? = null

    private var sayOK = RequestResponseBuilder().apply {
        responseLine("HTTP/1.1", 200, "OK")
        headerLine(HttpHeaders.ContentType, "text/plain")
        headerLine(HttpHeaders.ContentLength, STATIC_PLAINTEXT_LEN.toString())
        emptyLine()
        bytes(STATIC_PLAINTEXT)
    }.build()

    private var notFound = RequestResponseBuilder().apply {
        responseLine("HTTP/1.1", 404, "Not Found")
        headerLine(HttpHeaders.ContentLength, "0")
        emptyLine()
    }.build()

    override fun runServer(port: Int) {
        server = GlobalScope.httpServer(HttpServerSettings(port = port), handler =  { request: Request, input: ByteReadChannel, output: ByteWriteChannel, _: CompletableDeferred<Boolean>? ->
            val uri = request.uri
            if (uri.length == 6 && uri.startsWith("/sayOK")) {
                output.writePacket(sayOK.copy())
                output.close()
            } else {
                output.writePacket(notFound.copy())
                output.close()
            }
            request.release()
            input.discard()
        })
    }

    override fun stopServer() {
        server?.apply {
            acceptJob.cancel()
            rootServerJob.cancel()
            runBlocking {
                rootServerJob.join()
            }
            server = null
        }
        sayOK.release()
        notFound.release()
    }

    companion object {
        private val STATIC_PLAINTEXT = "OK".toByteArray(CharsetUtil.UTF_8)
        private val STATIC_PLAINTEXT_LEN = STATIC_PLAINTEXT.size
    }
}
