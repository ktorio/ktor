/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*

suspend fun proxyHandler(socket: Socket) {
    val input = socket.openReadChannel()
    val output = socket.openWriteChannel()

    val statusLine = input.readUTF8Line()
    while (true) {
        val line = input.readUTF8Line() ?: ""
        if (line.isEmpty()) {
            break
        }
    }

    val response = when (statusLine) {
        "GET http://google.com/ HTTP/1.1" -> buildResponse(HttpStatusCode.OK)
        "GET http://google.com/json HTTP/1.1" -> buildResponse(
            HttpStatusCode.OK, buildHeaders {
                append(HttpHeaders.ContentType, ContentType.Application.Json)
            }, "{\"status\": \"ok\"}"
        )
        else -> buildResponse(HttpStatusCode.BadRequest)
    }

    output.writeStringUtf8(response)
    output.close()

    while (!input.isClosedForRead) {
        input.readUTF8Line()
    }
}

private fun buildResponse(
    status: HttpStatusCode,
    headers: Headers = Headers.Empty,
    body: String = "proxy"
): String = buildString {
    append("HTTP/1.1 ${status.value} ${status.description}\r\n")
    append("Connection: close\r\n")
    headers.forEach { key, values ->
        append("$key: ${values.joinToString()}\r\n")
    }
    append("\r\n")
    append(body)
}
