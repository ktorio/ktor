/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

import io.ktor.network.sockets.*
import org.khronos.webgl.*

// js.Error
internal external interface JsError : JsAny {
    val message: String?
}

internal expect fun JsError.toThrowable(): Throwable
internal expect fun Throwable.toJsError(): JsError?

internal external interface NodeNet : JsAny {
    fun createConnection(options: CreateConnectionOptions): Socket
    fun createServer(options: CreateServerOptions): Server
}

internal expect suspend fun loadNodeNet(): NodeNet

internal fun CreateConnectionOptions(
    remoteAddress: SocketAddress,
    socketOptions: SocketOptions.TCPClientSocketOptions
): CreateConnectionOptions = when (remoteAddress) {
    is InetSocketAddress -> TcpCreateConnectionOptions {
        host = remoteAddress.hostname
        port = remoteAddress.port
        noDelay = socketOptions.noDelay
        timeout = when (socketOptions.socketTimeout) {
            Long.MAX_VALUE -> Int.MAX_VALUE
            else -> socketOptions.socketTimeout.toInt()
        }
        keepAlive = socketOptions.keepAlive
    }

    is UnixSocketAddress -> IpcCreateConnectionOptions {
        path = remoteAddress.path
        timeout = when (socketOptions.socketTimeout) {
            Long.MAX_VALUE -> Int.MAX_VALUE
            else -> socketOptions.socketTimeout.toInt()
        }
    }
}

internal external interface CreateConnectionOptions : JsAny {
    var timeout: Int?
    var allowHalfOpen: Boolean?
}

private fun TcpCreateConnectionOptions(
    block: TcpCreateConnectionOptions.() -> Unit
): TcpCreateConnectionOptions = createJsObject(block)

internal external interface TcpCreateConnectionOptions : CreateConnectionOptions {
    var port: Int
    var host: String?

    var localAddress: String?
    var localPort: Int?
    var family: Int? // ip stack
    var noDelay: Boolean?
    var keepAlive: Boolean?
}

private fun IpcCreateConnectionOptions(
    block: IpcCreateConnectionOptions.() -> Unit
): IpcCreateConnectionOptions = createJsObject(block)

internal external interface IpcCreateConnectionOptions : CreateConnectionOptions {
    var path: String
}

@Suppress("ktlint:standard:value-parameter-comment")
internal external interface Socket : JsAny {
    val localAddress: String
    val localPort: Int

    val remoteAddress: String
    val remotePort: Int

    fun write(buffer: Uint8Array): Boolean

    fun destroy(error: JsError?)

    // sends FIN
    fun end(callback: () -> Unit)

    fun on(event: String /* "close" */, listener: (hadError: Boolean) -> Unit)
    fun on(event: String /* "connect", "end", "timeout",  */, listener: () -> Unit)
    fun on(event: String /* "data" */, listener: (data: Uint8Array) -> Unit)
    fun on(event: String /* "error" */, listener: (error: JsError) -> Unit)
}

internal fun Socket.onClose(block: (hadError: Boolean) -> Unit): Unit = on("close", block)
internal fun Socket.onConnect(block: () -> Unit): Unit = on("connect", block)
internal fun Socket.onEnd(block: () -> Unit): Unit = on("end", block)
internal fun Socket.onTimeout(block: () -> Unit): Unit = on("timeout", block)
internal fun Socket.onData(block: (data: Uint8Array) -> Unit): Unit = on("data", block)
internal fun Socket.onError(block: (error: JsError) -> Unit): Unit = on("error", block)

internal fun CreateServerOptions(
    block: CreateServerOptions.() -> Unit
): CreateServerOptions = createJsObject(block)

internal external interface CreateServerOptions : JsAny {
    var allowHalfOpen: Boolean?
    var keepAlive: Boolean?
    var noDelay: Boolean?
}

@Suppress("ktlint:standard:value-parameter-comment")
internal external interface Server : JsAny {
    fun address(): ServerLocalAddressInfo?
    fun listen(options: ServerListenOptions)

    // stop accepting new connections
    fun close()

    fun on(event: String /* "close", "listening" */, listener: () -> Unit)
    fun on(event: String /* "connection" */, listener: (socket: Socket) -> Unit)
    fun on(event: String /* "error" */, listener: (error: JsError) -> Unit)
}

internal fun Server.onClose(block: () -> Unit): Unit = on("close", block)
internal fun Server.onListening(block: () -> Unit): Unit = on("listening", block)
internal fun Server.onConnection(block: (socket: Socket) -> Unit): Unit = on("connection", block)
internal fun Server.onError(block: (error: JsError) -> Unit): Unit = on("error", block)

internal fun ServerListenOptions(localAddress: SocketAddress?): ServerListenOptions = ServerListenOptions {
    when (localAddress) {
        is InetSocketAddress -> {
            port = localAddress.port
            host = localAddress.hostname
        }

        is UnixSocketAddress -> {
            path = localAddress.path
        }

        null -> {
            host = "0.0.0.0"
            port = 0
        }
    }
}

private fun ServerListenOptions(
    block: ServerListenOptions.() -> Unit
): ServerListenOptions = createJsObject(block)

internal external interface ServerListenOptions : JsAny {
    var port: Int?
    var host: String?
    var path: String?
}

internal external interface ServerLocalAddressInfo : JsAny

internal external interface TcpServerLocalAddressInfo : ServerLocalAddressInfo {
    val address: String
    val family: String
    val port: Int
}

internal fun ServerLocalAddressInfo.toSocketAddress(): SocketAddress = when (jsTypeOf(this)) {
    "string" -> {
        UnixSocketAddress(unsafeCast<JsString>().toString())
    }

    else -> {
        val info = unsafeCast<TcpServerLocalAddressInfo>()
        InetSocketAddress(info.address, info.port)
    }
}

internal expect fun jsTypeOf(a: JsAny): String

private fun <T : JsAny> createJsObject(): T = js("({})")
private fun <T : JsAny> createJsObject(block: T.() -> Unit): T = createJsObject<T>().apply(block)
