/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlinx.cinterop.*
import platform.windows.*

@OptIn(ExperimentalUnsignedTypes::class)
internal actual fun secureRandom(bytes: ByteArray) {
    val result = bytes.toUByteArray().usePinned { pinned ->
        BCryptGenRandom(
            null,
            pinned.addressOf(0),
            bytes.size.convert(),
            BCRYPT_USE_SYSTEM_PREFERRED_RNG
        )
    }
    if (result < 0) {
        error("Can't generate random values using BCryptGenRandom: $result")
    }
}
