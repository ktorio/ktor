/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

import io.ktor.network.sockets.*
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Promise

internal actual suspend fun loadNodeNet(): NodeNet = nodeNetPromise.await()
    ?: throw UnsupportedOperationException("Module node:net is not available. Please verify that you are using Node.js")

private val nodeNetPromise: Promise<NodeNet?> by lazy {
    loadNodeModule(nodeNetModuleName())
}

// Keep the module name behind a function so browser bundlers don't resolve `node:net` statically.
private fun nodeNetModuleName() = "node:net"

@JsFun(
    "moduleName => ((typeof process !== 'undefined') && process.release.name === 'node')" +
        " ? import(moduleName) : Promise.resolve(null)"
)
private external fun loadNodeModule(moduleName: String): Promise<NodeNet?>

internal actual fun TcpCreateConnectionOptions(
    block: TcpCreateConnectionOptions.() -> Unit
): TcpCreateConnectionOptions = createObject(block)

internal actual fun IpcCreateConnectionOptions(
    block: IpcCreateConnectionOptions.() -> Unit
): IpcCreateConnectionOptions = createObject(block)

internal actual fun CreateServerOptions(
    block: CreateServerOptions.() -> Unit
): CreateServerOptions = createObject(block)

internal actual fun ServerListenOptions(
    block: ServerListenOptions.() -> Unit
): ServerListenOptions = createObject(block)

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
