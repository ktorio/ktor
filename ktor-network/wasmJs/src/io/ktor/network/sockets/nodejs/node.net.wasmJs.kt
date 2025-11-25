/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

import io.ktor.network.sockets.*
import org.khronos.webgl.*

@JsFun(
    """
    (globalThis.module = (typeof process !== 'undefined') && (process.release.name === 'node') ?
        await import(/* webpackIgnore: true */'node:module') : void 0, () => {})
"""
)
internal external fun persistModule()

@JsFun(
    """() => { 
    const importMeta = import.meta;
    return globalThis.module.default.createRequire(importMeta.url);
}"""
)
internal external fun getRequire(): JsAny

@JsFun("(require) => require('node:net')")
internal external fun nodeNetRequire(require: JsAny): NodeNet?

private val require = persistModule().let { getRequire() }

internal actual fun nodeNet(): NodeNet? = nodeNetRequire(require)

internal actual typealias ExternalAny = JsAny

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

internal actual fun JsError.toThrowable(): Throwable = Error(message)
internal actual fun Throwable.toJsError(): JsError? = jsError(message)

private fun jsError(message: String?): JsError = js("(new Error(message))")

internal actual fun ByteArray.toJsBuffer(fromIndex: Int, toIndex: Int): JsBuffer {
    val array = Int8Array(toIndex - fromIndex)
    repeat(array.length) { index ->
        array[index] = this[fromIndex + index]
    }
    return justCast(array)
}

internal actual fun JsBuffer.toByteArray(): ByteArray {
    val array = justCast<Uint8Array>(this)
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
