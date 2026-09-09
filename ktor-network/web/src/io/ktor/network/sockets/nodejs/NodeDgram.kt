/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.SocketOptions
import org.khronos.webgl.*

internal external interface NodeDgram : JsAny {
    fun createSocket(options: DgramCreateSocketOptions): DgramSocket
}

internal fun DgramCreateSocketOptions(
    socketOptions: SocketOptions.UDPSocketOptions
): DgramCreateSocketOptions = DgramCreateSocketOptions {
    type = "udp4"
    reuseAddr = socketOptions.reuseAddress
    socketOptions.receiveBufferSize.takeIf { it > 0 }?.let { recvBufferSize = it }
    socketOptions.sendBufferSize.takeIf { it > 0 }?.let { sendBufferSize = it }
}

private fun DgramCreateSocketOptions(
    block: DgramCreateSocketOptions.() -> Unit
): DgramCreateSocketOptions = createJsObject(block)

internal external interface DgramCreateSocketOptions : JsAny {
    var type: String // "udp4" or "udp6"
    var reuseAddr: Boolean?
    var recvBufferSize: Int?
    var sendBufferSize: Int?
}

@Suppress("ktlint:standard:value-parameter-comment")
internal external interface DgramSocket : JsAny {
    fun address(): DgramAddressInfo
    fun remoteAddress(): DgramAddressInfo

    fun bind(port: Int?, address: String?)
    fun connect(port: Int, address: String?)

    fun send(
        msg: Uint8Array,
        offset: Int,
        length: Int,
        port: Int?,
        address: String?,
        callback: (error: JsError?) -> Unit
    )

    fun close()

    fun setBroadcast(flag: Boolean)

    fun on(event: String /* "message" */, listener: (msg: Uint8Array, rinfo: DgramRemoteInfo) -> Unit)
    fun on(event: String /* "listening", "close", "connect" */, listener: () -> Unit)
    fun on(event: String /* "error" */, listener: (error: JsError) -> Unit)
}

internal fun DgramSocket.onMessage(block: (msg: Uint8Array, rinfo: DgramRemoteInfo) -> Unit): Unit =
    on("message", block)

internal fun DgramSocket.onListening(block: () -> Unit): Unit = on("listening", block)
internal fun DgramSocket.onClose(block: () -> Unit): Unit = on("close", block)
internal fun DgramSocket.onConnect(block: () -> Unit): Unit = on("connect", block)
internal fun DgramSocket.onError(block: (error: JsError) -> Unit): Unit = on("error", block)

internal external interface DgramAddressInfo : JsAny {
    val address: String
    val family: String
    val port: Int
}

internal fun DgramAddressInfo.toSocketAddress(): SocketAddress {
    return InetSocketAddress(address, port)
}

internal external interface DgramRemoteInfo : JsAny {
    val address: String
    val family: String
    val port: Int
    val size: Int
}

internal fun DgramRemoteInfo.toSocketAddress(): SocketAddress {
    return InetSocketAddress(address, port)
}
