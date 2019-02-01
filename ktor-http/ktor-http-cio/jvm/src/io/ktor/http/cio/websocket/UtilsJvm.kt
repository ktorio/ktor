@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("UtilsKt")

package io.ktor.http.cio.websocket

import java.nio.*

internal fun ByteBuffer.xor(other: ByteBuffer) {
    val bb = slice()
    val mask = other.slice()
    val maskSize = mask.remaining()

    for (i in 0 until bb.remaining()) {
        bb.put(i, bb.get(i) xor mask[i % maskSize])
    }
}

