/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket.internals

import io.ktor.utils.io.core.*
import kotlinx.io.*
import kotlinx.io.Buffer

internal fun Buffer.endsWith(data: ByteArray): Boolean {
    peek().apply {
        discard(size - data.size)
        return readByteArray().contentEquals(data)
    }
}
