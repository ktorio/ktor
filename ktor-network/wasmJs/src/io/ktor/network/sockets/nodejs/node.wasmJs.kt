/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.nodejs

internal actual fun JsError.toThrowable(): Throwable = Error(message)
internal actual fun Throwable.toJsError(): JsError? = jsError(message)

private fun jsError(message: String?): JsError = js("(new Error(message))")

internal actual fun jsTypeOf(a: JsAny): String = js("(typeof a)")

// modules

internal actual suspend fun loadNodeNet(): NodeNet = nodeNet
internal actual suspend fun loadNodeDgram(): NodeDgram = nodeDgram

private val nodeNet: NodeNet by lazy {
    loadNodeNetModule() ?: throw UnsupportedOperationException(
        "Module node:net is not available. Please verify that you are using Node.js"
    )
}

private val nodeDgram: NodeDgram by lazy {
    loadNodeDgramModule() ?: throw UnsupportedOperationException(
        "Module node:dgram is not available. Please verify that you are using Node.js"
    )
}

@JsFun(
    "((module) => () => module)(((typeof process !== 'undefined') && process.release.name === 'node')" +
        " ? await import(/* webpackIgnore: true */'node:net') : null)"
)
private external fun loadNodeNetModule(): NodeNet?

@JsFun(
    "((module) => () => module)(((typeof process !== 'undefined') && process.release.name === 'node')" +
        " ? await import(/* webpackIgnore: true */'node:dgram') : null)"
)
private external fun loadNodeDgramModule(): NodeDgram?
