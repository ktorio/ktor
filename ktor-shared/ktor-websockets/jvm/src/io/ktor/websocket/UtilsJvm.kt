/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("UtilsKt")

package io.ktor.websocket

import java.nio.*

internal fun ByteBuffer.xor(other: ByteBuffer) {
    val bb = slice()
    val mask = other.slice()
    val maskSize = mask.remaining()

    for (i in 0 until bb.remaining()) {
        bb.put(i, bb.get(i) xor mask[i % maskSize])
    }
}

internal actual val OUTGOING_CHANNEL_CAPACITY: Int
    get() = System.getProperty("io.ktor.websocket.outgoingChannelCapacity")?.toInt() ?: 8
