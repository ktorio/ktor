/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ClassName", "FunctionName")

package io.ktor.network.quic.frames

/**
 * QUIC version 1 frame types and corresponding values.
 * todo FRAME_ENCODING_ERROR for varint bigger than byte
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-frame-types-and-formats)
 */
internal enum class FrameType_v1(val typeValue: Byte) {
    PADDING(0x00),
    PING(0x01),

    /** ACK Frames */
    ACK(0x02),
    ACK_ECN(0x03),

    RESET_STREAM(0x04),
    STOP_SENDING(0x05),
    CRYPTO(0x06),
    NEW_TOKEN(0x07),

    /**
     * STREAM frames of 0b00001XXX form, where XXX are three bits:
     * 0x04 - OFF bit, 0x02 - LEN bit, 0x01 - FIN bit
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-stream-frames)
     */
    STREAM(0x08),
    STREAM_FIN(0x09),
    STREAM_LEN(0x0A),
    STREAM_LEN_FIN(0x0B),
    STREAM_OFF(0x0C),
    STREAM_OFF_FIN(0x0D),
    STREAM_OFF_LEN(0x0E),
    STREAM_OFF_LEN_FIN(0x0F),

    MAX_DATA(0x10),
    MAX_STREAM_DATA(0x11),

    /** MAX_STREAMS frames */
    MAX_STREAMS_BIDIRECTIONAL(0x12),
    MAX_STREAMS_UNIDIRECTIONAL(0x13),

    DATA_BLOCKED(0x14),
    STREAM_DATA_BLOCKED(0x15),

    /** STREAMS_BLOCKED frames */
    STREAMS_BLOCKED_BIDIRECTIONAL(0x16),
    STREAMS_BLOCKED_UNIDIRECTIONAL(0x17),

    NEW_CONNECTION_ID(0x18),
    RETIRE_CONNECTION_ID(0x19),
    PATH_CHALLENGE(0x1A),
    PATH_RESPONSE(0x1B),

    /** CONNECTION_CLOSE */
    CONNECTION_CLOSE_TRANSPORT_ERR(0x1C),
    CONNECTION_CLOSE_APP_ERR(0x1D),

    HANDSHAKE_DONE(0x1E),
    ;

    companion object {
        private val array = FrameType_v1.values()

        fun fromByte(byte: Byte): FrameType_v1? {
            return when {
                byte > 0x1E -> null
                else -> array[byte.toInt()]
            }
        }
    }
}
