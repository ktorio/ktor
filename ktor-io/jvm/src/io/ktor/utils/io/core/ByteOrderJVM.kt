/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

public actual enum class ByteOrder(public val nioOrder: java.nio.ByteOrder) {
    BIG_ENDIAN(java.nio.ByteOrder.BIG_ENDIAN),
    LITTLE_ENDIAN(java.nio.ByteOrder.LITTLE_ENDIAN);

    public actual companion object {
        private val native: io.ktor.utils.io.core.ByteOrder = orderOf(java.nio.ByteOrder.nativeOrder())

        public fun of(nioOrder: java.nio.ByteOrder): io.ktor.utils.io.core.ByteOrder = orderOf(nioOrder)

        public actual fun nativeOrder(): io.ktor.utils.io.core.ByteOrder = native
    }
}

private fun orderOf(nioOrder: java.nio.ByteOrder): io.ktor.utils.io.core.ByteOrder =
    if (nioOrder === java.nio.ByteOrder.BIG_ENDIAN) io.ktor.utils.io.core.ByteOrder.BIG_ENDIAN else io.ktor.utils.io.core.ByteOrder.LITTLE_ENDIAN
