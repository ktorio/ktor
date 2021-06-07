/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.websocket

/**
 * Frame types enum
 * @property controlFrame if this is control frame type
 * @property opcode - frame type id that is used to transport it
 */
public enum class FrameType(public val controlFrame: Boolean, public val opcode: Int) {
    /**
     * Regular application level text frame
     */
    TEXT(false, 1),

    /**
     * Regular application level binary frame
     */
    BINARY(false, 2),

    /**
     * Low level close frame type
     */
    CLOSE(true, 8),

    /**
     * Low level ping frame type
     */
    PING(true, 9),

    /**
     * Low level pong frame type
     */
    PONG(true, 0xa);

    public companion object {
        private val maxOpcode = values().maxByOrNull { it.opcode }!!.opcode

        private val byOpcodeArray = Array(maxOpcode + 1) { op -> values().singleOrNull { it.opcode == op } }

        /**
         * Find [FrameType] instance by numeric [opcode]
         * @return a [FrameType] instance or `null` of the [opcode] value is not valid
         */
        public operator fun get(opcode: Int): FrameType? = if (opcode in 0..maxOpcode) byOpcodeArray[opcode] else null
    }
}
