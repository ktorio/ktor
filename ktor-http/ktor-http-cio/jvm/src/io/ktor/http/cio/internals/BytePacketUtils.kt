/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.internals

import io.ktor.utils.io.core.*

internal fun ByteReadPacket.endsWith(data: ByteArray): Boolean {
    copy().apply {
        discard(remaining - data.size)
        return readBytes().contentEquals(data)
    }
}
