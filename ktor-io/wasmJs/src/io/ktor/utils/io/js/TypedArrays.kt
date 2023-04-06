/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.js

import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.coerceAtMostMaxIntOrFail
import io.ktor.utils.io.core.readFully
import org.khronos.webgl.*

private fun toJsArrayImpl(vararg x: Byte): Int8Array = js("new Int8Array(x)")

public fun ByteArray.toJsArray(): Int8Array = toJsArrayImpl(*this)

public fun Int8Array.toByteArray(): ByteArray =
    ByteArray(this.length) { this.get(it) }

/**
 * Read exactly [n] bytes to a new array buffer instance
 */
public fun ByteReadPacket.readArrayBuffer(
    n: Int = remaining.coerceAtMostMaxIntOrFail("Unable to make a new ArrayBuffer: packet is too big")
): ArrayBuffer {
    val array = ByteArray(n)
    readFully(array)
    return Int8Array(array.toJsArray()).buffer
}
