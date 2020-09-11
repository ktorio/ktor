/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*

public suspend fun proxyHandler(socket: Socket) {
    val input = socket.openReadChannel()
    val output = socket.openWriteChannel()

    val statusLine = input.readUTF8Line()
    val requestData = StringBuilder()
    requestData.append(statusLine).append("\n")
    while (true) {
        val line = input.readUTF8Line() ?: ""
        requestData.append(line).append("\n")
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
        "GET /headers-merge HTTP/1.1" -> buildResponse(
            HttpStatusCode.OK,
            buildHeaders {
                append(HttpHeaders.ContentType, ContentType.Text.Plain)
            },
            requestData.toString()
        )
        "GET /wrong-value HTTP/1.1" -> buildResponse(
            HttpStatusCode.OK,
            listOf("${HttpHeaders.SetCookie}: ___utmvazauvysSB=kDu\u0001xSkE; path=/; Max-Age=900\r\n")
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
): String {
    val headersList: List<String> = headers.toMap().entries.toList().map { (key, values) ->
        "$key: ${values.joinToString(",")}\r\n"
    }

    return buildResponse(status, headersList, body)
}

private fun buildResponse(
    status: HttpStatusCode,
    headers: List<String>,
    body: String = "proxy"
): String = buildString {
    append("HTTP/1.1 ${status.value} ${status.description}\r\n")
    append("Connection: close\r\n")
    headers.forEach {
        append(it)
    }
    append("\r\n")
    append(body)
}
