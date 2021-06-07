/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils.tests

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.time.*

internal suspend fun tcpServerHandler(socket: Socket) {
    val input = socket.openReadChannel()
    val output = socket.openWriteChannel()

    var statusLine = input.readUTF8Line()
    val requestData = StringBuilder()
    requestData.append(statusLine).append("\n")
    while (true) {
        val line = input.readUTF8Line() ?: ""
        requestData.append(line).append("\n")

        if (line.isNotEmpty()) continue
        if (statusLine == null || !statusLine.startsWith("CONNECT ")) break

        if (handleProxyTunnel(statusLine, input, output)) {
            return
        }

        statusLine = input.readUTF8Line()
        requestData.clear()
        requestData.append(statusLine).append("\n")
    }

    val response = when (statusLine) {
        "GET http://google.com/ HTTP/1.1" -> buildResponse(HttpStatusCode.OK)
        "GET http://google.com/json HTTP/1.1" -> buildResponse(
            HttpStatusCode.OK,
            buildHeaders {
                append(HttpHeaders.ContentType, ContentType.Application.Json)
            },
            "{\"status\": \"ok\"}"
        )
        "GET /?ktor-test-tunnel HTTP/1.1" -> buildResponse(HttpStatusCode.OK, listOf("X-Proxy: yes\r\n"))
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
        "GET /errors/few-bytes HTTP/1.1" -> buildResponse(
            HttpStatusCode.OK,
            buildHeaders {
                append(HttpHeaders.ContentType, ContentType.Text.Plain)
                append(HttpHeaders.ContentLength, "100")
            },
            "Hello, world!"
        )
        else -> buildResponse(HttpStatusCode.BadRequest)
    }

    output.writeStringUtf8(response)
    output.close()

    while (!input.isClosedForRead) {
        input.readUTF8Line()
    }

    if (input is ByteChannel) {
        input.closedCause?.let { throw it }
    }
}

private suspend fun handleProxyTunnel(
    statusLine: String,
    input: ByteReadChannel,
    output: ByteWriteChannel
): Boolean {
    require(statusLine.startsWith("CONNECT "))

    output.writeStringUtf8("HTTP/1.1 200 Connection established\r\n\r\n")
    output.flush()

    val hostPort = statusLine.split(" ")[1]
    val host = hostPort.substringBefore(":")
    val port = hostPort.substringAfter(":", "80").toInt()

    when (host) {
        "localhost", "127.0.0.1", "::1" -> {
            withTimeout(30000L) {
                connectAndProcessTunnel(host, port, output, input)
            }
            return true
        }
    }

    output.writeStringUtf8("HTTP/1.1 500 Failed to connect\r\n")
    output.writeStringUtf8("X-Reason: Host is not supported\r\n\r\n")
    output.flush()

    return false
}

private suspend fun connectAndProcessTunnel(
    host: String,
    port: Int,
    output: ByteWriteChannel,
    input: ByteReadChannel
) {
    SelectorManager(Dispatchers.IO).use { selector ->
        aSocket(selector).tcp().connect(host, port).use { destination ->
            coroutineScope {
                launch {
                    destination.openReadChannel().copyAndClose(output)
                }
                launch {
                    input.copyAndClose(destination.openWriteChannel(true))
                }
            }
        }
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
