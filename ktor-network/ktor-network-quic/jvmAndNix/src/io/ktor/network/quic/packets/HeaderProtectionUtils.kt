/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UNUSED_PARAMETER")

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.tls.*

@Suppress("NOTHING_TO_INLINE")
internal object HeaderProtectionUtils {
    const val HP_FLAGS_LONG_MASK: UInt8 = 0x0Fu
    const val HP_FLAGS_SHORT_MASK: UInt8 = 0x1Fu

    /**
     * Returns a part of the header protection mask, that applies to first byte of packet's header
     *
     * @param headerMask - 0x1Fu for Short headers, 0x0Fu for Long headers
     */
    inline fun flagsHPMask(hp: Long, headerMask: UInt8): UInt8 = (hp ushr 32).toUByte() and headerMask

    /**
     * Returns a part of the header protection mask, that applies to encrypted packet number with the length of 1 byte
     */
    inline fun pnHPMask1(hp: Long): UInt8 = (hp ushr 24).toUByte()

    /**
     * Returns a part of the header protection mask, that applies to encrypted packet number with the length of 2 bytes
     */
    inline fun pnHPMask2(hp: Long): UInt16 = (hp ushr 16).toUShort()

    /**
     * Returns a part of the header protection mask, that applies to encrypted packet number with the length of 3 bytes
     */
    inline fun pnHPMask3(hp: Long): UInt32 = (hp ushr 8).toUInt() and 0x00FFFFFFu

    /**
     * Returns a part of the header protection mask, that applies to encrypted packet number with the length of 4 bytes
     */
    inline fun pnHPMask4(hp: Long): UInt32 = hp.toUInt()
}
