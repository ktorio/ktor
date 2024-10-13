/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

import io.ktor.network.sockets.*
import org.khronos.webgl.*

internal actual fun nodeNet(): NodeNet? = js("eval('require')('node:net')").unsafeCast<NodeNet?>()

internal actual fun TcpCreateConnectionOptions(block: TcpCreateConnectionOptions.() -> Unit): TcpCreateConnectionOptions =
    createObject(block)

internal actual fun IpcCreateConnectionOptions(block: IpcCreateConnectionOptions.() -> Unit): IpcCreateConnectionOptions =
    createObject(block)

internal actual fun CreateServerOptions(block: CreateServerOptions.() -> Unit): CreateServerOptions =
    createObject(block)

internal actual fun ServerListenOptions(block: ServerListenOptions.() -> Unit): ServerListenOptions =
    createObject(block)

private fun <T> createObject(block: T.() -> Unit): T = js("{}").unsafeCast<T>().apply(block)

internal actual fun JsError.toThrowable(): Throwable = unsafeCast<Throwable>()
internal actual fun Throwable.toJsError(): JsError? = unsafeCast<JsError>()

internal actual fun ByteArray.toJsBuffer(fromIndex: Int, toIndex: Int): JsBuffer {
    return unsafeCast<Int8Array>().subarray(fromIndex, toIndex).unsafeCast<JsBuffer>()
}

internal actual fun JsBuffer.toByteArray(): ByteArray {
    return Int8Array(unsafeCast<ArrayBuffer>()).unsafeCast<ByteArray>()
}

internal actual fun ServerLocalAddressInfo.toSocketAddress(): SocketAddress {
    if (jsTypeOf(this) == "string") return UnixSocketAddress(unsafeCast<String>())
    val info = unsafeCast<TcpServerLocalAddressInfo>()
    return InetSocketAddress(info.address, info.port)
}
