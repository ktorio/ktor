/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

private val LOCAL_HOSTS = listOf("localhost", "127.0.0.1", "::1")
private const val HOST_UNDER_PROXY = "google.com"

internal suspend fun tcpServerHandler(socket: Socket) {
    val input = socket.openReadChannel()
    val output = socket.openWriteChannel()

    var statusLine = input.readUTF8Line()
    val requestData = StringBuilder()
    requestData.append(statusLine).append("\n")
    while (true) {
        val line = input.readUTF8Line().orEmpty()
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

    output.writeProxyResponse(statusLine, requestData.toString())

    while (!input.isClosedForRead) {
        input.readUTF8Line()
    }

    input.closedCause?.let { throw it }
}

private suspend fun handleProxyTunnel(
    statusLine: String,
    input: ByteReadChannel,
    output: ByteWriteChannel
): Boolean {
    require(statusLine.startsWith("CONNECT "))

    val hostPort = statusLine.split(" ")[1]
    val host = hostPort.substringBefore(":")
    val port = hostPort.substringAfter(":", "80").toInt()

    when (host) {
        in LOCAL_HOSTS -> {
            connectAndProcessTunnel(host, port, output, input) {
                output.writeStringUtf8("HTTP/1.1 200 Connection established\r\n\r\n")
                output.flush()
            }
            return true
        }
    }

    output.writeStringUtf8("HTTP/1.1 500 Failed to connect\r\n")
    output.writeStringUtf8("X-Reason: Host is not supported\r\n\r\n")
    output.flush()

    return false
}

/**
 * SOCKS5 proxy server handler for testing.
 * Supports SOCKS5 protocol with:
 * - No authentication
 * - CONNECT command only
 * - IPv4 addresses and domain names
 */
internal suspend fun socksServerHandler(socket: Socket) {
    val input = socket.openReadChannel()
    val output = socket.openWriteChannel()

    try {
        if (!Socks.handleGreeting(input, output)) return
        val address = Socks.readConnectionRequest(input) ?: return
        Socks.connectToDestination(address, input, output)
    } catch (e: Exception) {
        println("SOCKS proxy error: ${e.message}")
        e.printStackTrace()
    }
}

private object Socks {
    private const val VERSION_5 = 0x05.toByte()
    private const val METHOD_NO_AUTH = 0x00.toByte()
    private const val COMMAND_CONNECT = 0x01.toByte()
    private const val ADDRESS_IPV4 = 0x01.toByte()
    private const val ADDRESS_DOMAIN_NAME = 0x03.toByte()

    private const val STATUS_REQUEST_GRANTED = 0x00.toByte()
    private const val STATUS_CONNECTION_REFUSED = 0x05.toByte()

    /**
     * Handles SOCKS5 greeting and authentication negotiation.
     *
     * Client sends: [version(1)] [nmethods(1)] [methods(1-255)]
     * Server responds: [version(1)] [method(1)]
     */
    suspend fun handleGreeting(input: ByteReadChannel, output: ByteWriteChannel): Boolean {
        if (!validateVersion(input.readByte())) return false

        val nmethods = input.readByte().toInt() and 0xFF
        val methods = ByteArray(nmethods)
        input.readFully(methods, 0, nmethods)

        output.writeByte(VERSION_5)
        output.writeByte(METHOD_NO_AUTH)
        output.flush()

        return true
    }

    /**
     * Reads and parses SOCKS5 connection request.
     *
     * Request format: [version(1)] [command(1)] [reserved(1)] [address_type(1)] [address(variable)] [port(2)]
     */
    suspend fun readConnectionRequest(input: ByteReadChannel): ConnectionAddress? {
        if (!validateVersion(input.readByte())) return null

        val command = input.readByte()
        if (command != COMMAND_CONNECT) {
            println("SOCKS: Unsupported command: $command")
            return null
        }

        input.readByte() // Reserved byte (ignored)
        return input.readConnectionAddress()
    }

    private fun validateVersion(version: Byte): Boolean {
        val isValid = version == VERSION_5
        if (!isValid) println("SOCKS: Invalid version $version")
        return isValid
    }

    /**
     * Connects to the destination and tunnels data between client and destination.
     */
    suspend fun connectToDestination(
        address: ConnectionAddress,
        input: ByteReadChannel,
        output: ByteWriteChannel
    ) {
        try {
            if (address.host in LOCAL_HOSTS) {
                connectAndProcessTunnel(address.host, address.port, output, input) {
                    output.sendSocksResponse(STATUS_REQUEST_GRANTED, address)
                }
            } else {
                output.sendSocksResponse(STATUS_REQUEST_GRANTED, address)
                handleProxiedHttpRequest(input, output)
            }
        } catch (e: Exception) {
            println("SOCKS: Failed to connect to ${address.host}:${address.port}: ${e.message}")
            output.sendSocksResponse(STATUS_CONNECTION_REFUSED, address)
        }
    }

    /**
     * Sends SOCKS5 response to client.
     *
     * Response format: [version(1)] [reply(1)] [reserved(1)] [address_type(1)] [address(variable)] [port(2)]
     */
    private suspend fun ByteWriteChannel.sendSocksResponse(replyCode: Byte, address: ConnectionAddress) {
        writeByte(VERSION_5)
        writeByte(replyCode)
        writeByte(0x00) // Reserved
        writeConnectionAddress(address)
        flush()
    }

    /**
     * Handles proxied HTTP requests for testing purposes.
     * Uses the shared buildProxyResponse function.
     */
    private suspend fun handleProxiedHttpRequest(input: ByteReadChannel, output: ByteWriteChannel) {
        val requestLine = input.readUTF8Line() ?: return

        // Skip headers until empty line
        while (true) {
            val line = input.readUTF8Line().orEmpty()
            if (line.isEmpty()) break
        }

        output.writeProxyResponse(requestLine)
    }

    private suspend fun ByteReadChannel.readConnectionAddress(): ConnectionAddress? {
        val addressType = readByte()
        val host = when (addressType) {
            ADDRESS_IPV4 -> {
                val addr = ByteArray(4)
                readFully(addr, 0, 4)
                addr.joinToString(".") { (it.toInt() and 0xFF).toString() }
            }

            ADDRESS_DOMAIN_NAME -> {
                val length = readByte().toInt() and 0xFF
                val domainBytes = ByteArray(length)
                readFully(domainBytes, 0, length)
                domainBytes.decodeToString()
            }

            else -> {
                println("SOCKS: Unsupported address type: $addressType")
                return null
            }
        }

        val port = readShort().toInt() and 0xFFFF
        return ConnectionAddress(addressType, host, port)
    }

    private suspend fun ByteWriteChannel.writeConnectionAddress(address: ConnectionAddress) {
        writeByte(address.addressType)
        when (address.addressType) {
            ADDRESS_IPV4 -> {
                val parts = address.host.split(".")
                parts.forEach { writeByte(it.toInt().toByte()) }
            }

            ADDRESS_DOMAIN_NAME -> {
                writeByte(address.host.length.toByte())
                writeStringUtf8(address.host)
            }
        }

        writeShort(address.port.toShort())
    }

    data class ConnectionAddress(
        val addressType: Byte,
        val host: String,
        val port: Int
    )
}

/**
 * Builds HTTP proxy response based on the request line.
 * Shared between HTTP proxy and SOCKS proxy handlers.
 */
private suspend fun ByteWriteChannel.writeProxyResponse(statusLine: String?, requestData: String = "") {
    val response = when (statusLine) {
        "GET http://$HOST_UNDER_PROXY/ HTTP/1.1", "GET / HTTP/1.1" -> buildResponse(HttpStatusCode.OK)
        "GET http://$HOST_UNDER_PROXY/json HTTP/1.1", "GET /json HTTP/1.1" -> buildResponse(
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
            requestData
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

    writeStringUtf8(response)
    flushAndClose()
}

private suspend fun connectAndProcessTunnel(
    host: String,
    port: Int,
    output: ByteWriteChannel,
    input: ByteReadChannel,
    onConnected: suspend () -> Unit,
) = withTimeout(30.seconds) {
    SelectorManager(Dispatchers.IO).use { selector ->
        aSocket(selector).tcp().connect(host, port).use { destination ->
            onConnected()
            coroutineScope {
                launch {
                    destination.openReadChannel().copyAndClose(output)
                }
                launch {
                    input.copyAndClose(destination.openWriteChannel(autoFlush = true))
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
