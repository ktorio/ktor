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
internal enum class FrameType_v1(val typeValue: UByte) {
    PADDING(0x00u),
    PING(0x01u),

    /** ACK Frames */
    ACK(0x02u),
    ACK_ECN(0x03u),

    RESET_STREAM(0x04u),
    STOP_SENDING(0x05u),
    CRYPTO(0x06u),
    NEW_TOKEN(0x07u),

    /**
     * STREAM frames of 0b00001XXX form, where XXX are three bits:
     * 0x04 - OFF bit, 0x02 - LEN bit, 0x01 - FIN bit
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-stream-frames)
     */
    STREAM(0x08u),
    STREAM_FIN(0x09u),
    STREAM_LEN(0x0Au),
    STREAM_LEN_FIN(0x0Bu),
    STREAM_OFF(0x0Cu),
    STREAM_OFF_FIN(0x0Du),
    STREAM_OFF_LEN(0x0Eu),
    STREAM_OFF_LEN_FIN(0x0Fu),

    MAX_DATA(0x10u),
    MAX_STREAM_DATA(0x11u),

    /** MAX_STREAMS frames */
    MAX_STREAMS_BIDIRECTIONAL(0x12u),
    MAX_STREAMS_UNIDIRECTIONAL(0x13u),

    DATA_BLOCKED(0x14u),
    STREAM_DATA_BLOCKED(0x15u),

    /** STREAMS_BLOCKED frames */
    STREAMS_BLOCKED_BIDIRECTIONAL(0x16u),
    STREAMS_BLOCKED_UNIDIRECTIONAL(0x17u),

    NEW_CONNECTION_ID(0x18u),
    RETIRE_CONNECTION_ID(0x19u),
    PATH_CHALLENGE(0x1Au),
    PATH_RESPONSE(0x1Bu),

    /** CONNECTION_CLOSE */
    CONNECTION_CLOSE_TRANSPORT_ERR(0x1Cu),
    CONNECTION_CLOSE_APP_ERR(0x1Du),

    HANDSHAKE_DONE(0x1Eu),
    ;

    companion object {
        private val array = FrameType_v1.values()

        fun fromByte(byte: UByte): FrameType_v1? {
            return when {
                byte > 0x1Eu -> null
                else -> array[byte.toInt()]
            }
        }
    }
}
