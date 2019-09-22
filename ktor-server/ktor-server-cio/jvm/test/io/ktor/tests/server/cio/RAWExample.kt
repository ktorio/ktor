/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.server.cio.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import java.time.*

private val GreenwichMeanTime: ZoneId = ZoneId.of("GMT")

@Volatile
private var cachedDateText: String = ZonedDateTime.now(GreenwichMeanTime).toHttpDateString()

private val HelloWorld = "Hello, World!".toByteArray()
private val HelloWorldLength = HelloWorld.size.toString()

private val notFound404_11 = RequestResponseBuilder().apply {
    responseLine("HTTP/1.1", 404, "Not Found")
    headerLine("Content-Length", "0")
    emptyLine()
}.build()

/**
 * This is just an example demonstrating how to create CIO low-level http server
 */
fun main(args: Array<String>) {
    val settings = HttpServerSettings()

    GlobalScope.launch {
        while (isActive) {
            cachedDateText = ZonedDateTime.now(GreenwichMeanTime).toHttpDateString()
            delay(1000)
        }
    }

    val server = GlobalScope.httpServer(settings, handler = { request: Request,
                                                              _: ByteReadChannel,
                                                              output: ByteWriteChannel,
                                                              _: CompletableDeferred<Boolean>? ->
        try {
            if (request.uri.length == 1 && request.uri[0] == '/' && request.method == HttpMethod.Get) {
                val response = RequestResponseBuilder()
                response.responseLine(request.version, 200, "OK")
                response.headerLine("Date", cachedDateText)
                response.headerLine("Content-Length", HelloWorldLength)
                response.headerLine("Content-Type", "text/plain; charset=utf-8")
                response.emptyLine()

                response.bytes(HelloWorld)
                output.writePacket(response.build())
            } else {
                output.writePacket(notFound404_11.copy())
            }

            output.close()
        } finally {
            request.release()
        }
    })

    runBlocking {
        server.rootServerJob.join()
    }
}
