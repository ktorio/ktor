/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.bytes

import io.ktor.network.quic.connections.QUICConnectionID
import io.ktor.network.quic.errors.QUICTransportError
import io.ktor.network.quic.util.POW_2_06
import io.ktor.network.quic.util.POW_2_14
import io.ktor.network.quic.util.POW_2_30
import io.ktor.network.quic.util.POW_2_62

/**
 * Object that holding maximum sizes for parameters of frames
 */
internal object PayloadSize {
    const val FRAME_TYPE_SIZE = 1
    const val APP_ERROR_SIZE = 8
    const val PATH_CHALLENGE_DATA = 8
    const val STATELESS_RESET_TOKEN = 16

    const val LONG_SIZE = 8

    const val RETRY_PACKET_INTEGRITY_TAG_LENGTH = 16

    /**
     * Length of sample ciphertext used for header protection
     */
    const val HP_SAMPLE_LENGTH = 16

    /**
     * Length of the header that gets appended during encryption
     */
    const val ENCRYPTION_HEADER_LENGTH = 16

    const val HEADER_FLAGS_LENGTH = 1
    const val LONG_HEADER_LENGTH_FIELD_MAX_LENGTH = 4
    const val HEADER_PACKET_NUMBER_MAX_LENGTH = 4
    const val LONG_HEADER_VERSION_LENGTH = 4

    fun ofVarInt(varInt: Long): Int {
        return when {
            varInt < POW_2_06 -> 1
            varInt < POW_2_14 -> 2
            varInt < POW_2_30 -> 4
            varInt < POW_2_62 -> 8
            else -> error("Var int can not be greater than $POW_2_62")
        }
    }

    fun ofVarInt(varInt: Int): Int {
        return when {
            varInt < POW_2_06 -> 1
            varInt < POW_2_14 -> 2
            varInt < POW_2_30 -> 4
            else -> 8
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun ofError(error: QUICTransportError) = error.expectedSize

    @Suppress("NOTHING_TO_INLINE")
    inline fun ofByteArray(byteArray: ByteArray): Int = byteArray.size

    @Suppress("NOTHING_TO_INLINE")
    inline fun ofByteArrayWithLength(byteArray: ByteArray): Int = ofVarInt(byteArray.size) + byteArray.size

    @Suppress("NOTHING_TO_INLINE")
    inline fun ofByteArrayWithLength(byteArraySize: Int): Int = ofVarInt(byteArraySize) + byteArraySize

    fun ofConnectionID(id: QUICConnectionID): Int = ofVarInt(id.size) + ofByteArray(id.value)
}
