/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

import io.ktor.network.sockets.*
import org.khronos.webgl.*

internal actual fun nodeNet(): NodeNet? = js("eval('require')('node:net')")

internal actual fun TcpCreateConnectionOptions(block: TcpCreateConnectionOptions.() -> Unit): TcpCreateConnectionOptions =
    createObject(block)

internal actual fun IpcCreateConnectionOptions(block: IpcCreateConnectionOptions.() -> Unit): IpcCreateConnectionOptions =
    createObject(block)

internal actual fun CreateServerOptions(block: CreateServerOptions.() -> Unit): CreateServerOptions =
    createObject(block)

internal actual fun ServerListenOptions(block: ServerListenOptions.() -> Unit): ServerListenOptions =
    createObject(block)

// TODO[whyoleg] how to convert exceptions?
internal actual fun JsError.toThrowable(): Throwable = Error(this.toString())
internal actual fun Throwable.toJsError(): JsError? = null

internal actual fun ByteArray.toJsBuffer(fromIndex: Int, toIndex: Int): JsBuffer {
    val array = Int8Array(toIndex - fromIndex)
    repeat(array.length) { index ->
        array[index] = this[fromIndex + index]
    }
    return justCast(array)
}

internal actual fun JsBuffer.toByteArray(): ByteArray {
    val array = Int8Array(justCast<ArrayBuffer>(this))
    val bytes = ByteArray(array.length)

    repeat(array.length) { index ->
        bytes[index] = array[index]
    }
    return bytes
}

internal actual fun ServerLocalAddressInfo.toSocketAddress(): SocketAddress {
    if (jsTypeOf(justCast(this)) == "string") return UnixSocketAddress(justCast<JsString>(this).toString())
    val info = justCast<TcpServerLocalAddressInfo>(this)
    return InetSocketAddress(info.address, info.port)
}

private fun jsTypeOf(obj: JsAny): String = js("(typeof obj)")

private fun createJsObject(): JsAny = js("({})")

private fun <T : JsAny> createObject(block: T.() -> Unit): T = createJsObject().unsafeCast<T>().apply(block)

// overcomes the issue that expect declarations are not extending `JsAny`
@Suppress("UNCHECKED_CAST")
private fun <T> justCast(obj: Any): T = obj as T
