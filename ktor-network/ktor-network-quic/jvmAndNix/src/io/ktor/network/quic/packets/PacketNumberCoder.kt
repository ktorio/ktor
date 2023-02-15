/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.consts.*

/**
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-sample-packet-number-decodi)
 */
internal fun decodePacketNumber(
    largestPn: Long,
    truncatedPn: UInt32,
    pnLen: UInt32,
): Long {
    val pnNBits = (pnLen.toInt() + 1) * 8
    val expectedPn = largestPn + 1L
    val pnWin = 1L shl pnNBits
    val pnHWin = pnWin / 2
    val pnMask = pnWin - 1

    val candidatePN = (expectedPn and pnMask.inv()) or truncatedPn.toLong()

    return when {
        candidatePN <= expectedPn - pnHWin && candidatePN < POW_2_62 - pnWin -> candidatePN + pnWin
        candidatePN > expectedPn + pnHWin && candidatePN >= pnWin -> candidatePN - pnWin
        else -> candidatePN
    }
}
