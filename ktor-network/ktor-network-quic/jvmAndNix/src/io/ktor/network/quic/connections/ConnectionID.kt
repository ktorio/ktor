/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

internal typealias ConnectionID = ByteArray

internal infix fun ConnectionID?.neq(other: ConnectionID?): Boolean {
    return !eq(other)
}

internal infix fun ConnectionID?.eq(other: ConnectionID?): Boolean {
    if (this == null && other == null) return true
    if (this == null || other == null) return false

    if (size != other.size) {
        return false
    }
    var i = 0
    while (i < size) {
        if (this[i] != other[i]) {
            return false
        }
        i++
    }
    return true
}
