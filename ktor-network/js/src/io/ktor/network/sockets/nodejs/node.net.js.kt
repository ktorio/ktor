/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

import kotlinx.coroutines.*
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

internal actual fun JsError.toThrowable(): Throwable = unsafeCast<Throwable>()
internal actual fun Throwable.toJsError(): JsError? = unsafeCast<JsError>()

internal actual fun jsTypeOf(a: JsAny): String = kotlin.js.jsTypeOf(a)
