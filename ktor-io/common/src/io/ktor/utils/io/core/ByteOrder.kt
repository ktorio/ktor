/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

public expect enum class ByteOrder {
    BIG_ENDIAN,
    LITTLE_ENDIAN;

    public companion object {
        public fun nativeOrder(): ByteOrder
    }
}
