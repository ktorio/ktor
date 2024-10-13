/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

import io.ktor.network.sockets.*

internal external interface JsError // js.Error

internal expect fun JsError.toThrowable(): Throwable
internal expect fun Throwable.toJsError(): JsError?

internal external interface JsBuffer // Int8Array

internal expect fun ByteArray.toJsBuffer(fromIndex: Int, toIndex: Int): JsBuffer
internal expect fun JsBuffer.toByteArray(): ByteArray


internal external interface NodeNet {
    fun createConnection(options: CreateConnectionOptions): Socket
    fun createServer(options: CreateServerOptions): Server
}

internal expect fun nodeNet(): NodeNet?

internal val nodeNet by lazy {
    requireNotNull(runCatching { nodeNet() }.getOrNull()) {
        "Node.js net module is not available. Please verify that you are using Node.js"
    }
}

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
            else           -> socketOptions.socketTimeout.toInt()
        }
        keepAlive = socketOptions.keepAlive
    }

    is UnixSocketAddress -> IpcCreateConnectionOptions {
        path = remoteAddress.path
        timeout = when (socketOptions.socketTimeout) {
            Long.MAX_VALUE -> Int.MAX_VALUE
            else           -> socketOptions.socketTimeout.toInt()
        }
    }
}

internal external interface CreateConnectionOptions {
    var timeout: Int?
    var allowHalfOpen: Boolean?
}

internal expect fun TcpCreateConnectionOptions(block: TcpCreateConnectionOptions.() -> Unit): TcpCreateConnectionOptions

internal external interface TcpCreateConnectionOptions : CreateConnectionOptions {
    var port: Int
    var host: String?

    var localAddress: String?
    var localPort: Int?
    var family: Int? // ip stack
    var noDelay: Boolean?
    var keepAlive: Boolean?
}

internal expect fun IpcCreateConnectionOptions(block: IpcCreateConnectionOptions.() -> Unit): IpcCreateConnectionOptions

internal external interface IpcCreateConnectionOptions : CreateConnectionOptions {
    var path: String
}

internal external interface Socket {
    val localAddress: String
    val localPort: Int

    val remoteAddress: String
    val remotePort: Int

    fun write(buffer: JsBuffer): Boolean

    fun destroy(error: JsError?)

    // sends FIN
    fun end()

    fun on(event: String /* "close" */, listener: (hadError: Boolean) -> Unit)
    fun on(event: String /* "connect", "drain", "end", "timeout",  */, listener: () -> Unit)
    fun on(event: String /* "data" */, listener: (data: JsBuffer) -> Unit)
    fun on(event: String /* "error" */, listener: (error: JsError) -> Unit)
}

internal expect fun CreateServerOptions(block: CreateServerOptions.() -> Unit): CreateServerOptions

internal external interface CreateServerOptions {
    var allowHalfOpen: Boolean?
    var keepAlive: Boolean?
    var noDelay: Boolean?
}

internal external interface Server {
    fun address(): ServerLocalAddressInfo?
    fun listen(options: ServerListenOptions)

    // stop accepting new connections
    fun close()

    fun on(event: String /* "close", "listening" */, listener: () -> Unit)
    fun on(event: String /* "connection" */, listener: (socket: Socket) -> Unit)
    fun on(event: String /* "error" */, listener: (error: JsError) -> Unit)
    fun on(event: String /* "drop" */, listener: (drop: ServerConnectionDrop) -> Unit)
}

internal external interface ServerConnectionDrop

internal fun ServerListenOptions(localAddress: SocketAddress?): ServerListenOptions = ServerListenOptions {
    when (localAddress) {
        is InetSocketAddress -> {
            port = localAddress.port
            host = localAddress.hostname
        }

        is UnixSocketAddress -> {
            path = localAddress.path
        }

        null                 -> {
            host = "0.0.0.0"
            port = 0
        }
    }
}

internal expect fun ServerListenOptions(block: ServerListenOptions.() -> Unit): ServerListenOptions

internal external interface ServerListenOptions {
    var port: Int?
    var host: String?
    var path: String?
}

internal external interface ServerLocalAddressInfo

internal external interface TcpServerLocalAddressInfo : ServerLocalAddressInfo {
    val address: String
    val family: String
    val port: Int
}

internal expect fun ServerLocalAddressInfo.toSocketAddress(): SocketAddress

