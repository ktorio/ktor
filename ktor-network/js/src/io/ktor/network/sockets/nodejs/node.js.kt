/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

import kotlinx.coroutines.*
import kotlin.js.Promise

internal actual fun JsError.toThrowable(): Throwable = unsafeCast<Throwable>()
internal actual fun Throwable.toJsError(): JsError? = unsafeCast<JsError>()

internal actual fun jsTypeOf(a: JsAny): String = kotlin.js.jsTypeOf(a)

// modules

internal actual suspend fun loadNodeNet(): NodeNet = nodeNetPromise.await()
    ?: throw UnsupportedOperationException("Module node:net is not available. Please verify that you are using Node.js")

internal actual suspend fun loadNodeDgram(): NodeDgram = nodeDgramPromise.await()
    ?: throw UnsupportedOperationException(
        "Module node:dgram is not available. Please verify that you are using Node.js"
    )

private val nodeNetPromise: Promise<NodeNet?> by lazy(::loadNodeNetModule)
private val nodeDgramPromise: Promise<NodeDgram?> by lazy(::loadNodeGramModule)

@JsFun(
    "moduleName => ((typeof process !== 'undefined') && process.release.name === 'node')" +
        " ? import(/* webpackIgnore: true */'node:net') : Promise.resolve(null)"
)
private external fun loadNodeNetModule(): Promise<NodeNet?>

@JsFun(
    "moduleName => ((typeof process !== 'undefined') && process.release.name === 'node')" +
        " ? import(/* webpackIgnore: true */'node:dgram') : Promise.resolve(null)"
)
private external fun loadNodeGramModule(): Promise<NodeDgram?>
