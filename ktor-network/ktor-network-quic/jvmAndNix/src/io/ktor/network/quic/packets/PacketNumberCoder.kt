/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.util.*

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
    val pnHWin = pnWin ushr 1
    val pnMask = pnWin - 1

    // The incoming packet number should be greater than
    // expectedPn - pnHWin and less than or equal to
    // expectedPn + pnHWin
    //
    // This means we cannot just strip the trailing bits from
    // expected_pn and add the truncated_pn because that might
    // yield a value outside the window.
    //
    // The following code calculates a candidate value and
    // makes sure it's within the packet number window.
    // Note the extra checks to prevent overflow and underflow.
    val candidatePN = (expectedPn and pnMask.inv()) or truncatedPn.toLong()

    return when {
        candidatePN <= expectedPn - pnHWin && candidatePN < POW_2_62 - pnWin -> candidatePN + pnWin
        candidatePN > expectedPn + pnHWin && candidatePN >= pnWin -> candidatePN - pnWin
        else -> candidatePN
    }
}

/**
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-sample-packet-number-encodi)
 */
internal fun getPacketNumberLength(
    fullPn: Long,
    largestAcked: Long,
): UInt8 {
    // The number of bits must be at least one more
    // than the base-2 logarithm of the number of contiguous
    // unacknowledged packet numbers, including the new packet.

    val numUnacked: Long = when (largestAcked) {
        -1L -> fullPn + 1
        else -> fullPn - largestAcked
    }

    /*
     * It is algorithm from RFC, i.e.:
     * ```
     * min_bits = log(num_unacked, 2) + 1
     * num_bytes = ceil(min_bits / 8)
     * ```
     * but without the `log` function
     */
    return when {
        numUnacked <= POW_2_07 -> 1u
        numUnacked <= POW_2_15 -> 2u
        numUnacked <= POW_2_23 -> 3u
        else -> 4u
    }
}
