/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

import kotlin.jvm.*

@JvmInline
internal value class ConnectionID(val value: ByteArray) {
    val size get() = value.size

    companion object {
        val EMPTY = ConnectionID(byteArrayOf())
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ByteArray.asCID() = ConnectionID(this)

internal infix fun ConnectionID.neq(other: ConnectionID): Boolean {
    return !eq(other)
}

internal infix fun ConnectionID.eq(other: ConnectionID): Boolean {
    return value.contentEquals(other.value)
}
