/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.js.JsAny
import kotlin.js.Promise

public expect fun ByteArray.toJsArray(): Int8Array
public expect fun Int8Array.toByteArray(): ByteArray

internal expect suspend fun Promise<ArrayBuffer>.awaitBuffer(): ArrayBuffer

public expect class Int8Array
public expect class ArrayBuffer : JsAny

internal expect fun DataView(buffer: ArrayBuffer): DataView

internal expect open class DataView {
    val buffer: ArrayBuffer
    val byteOffset: Int
    val byteLength: Int
    fun getInt8(byteOffset: Int): Byte
    fun getUint8(byteOffset: Int): Byte
}
