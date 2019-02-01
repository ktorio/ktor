package io.ktor.http.cio.websocket

/**
 * Frame types enum
 * @property controlFrame if this is control frame type
 * @property opcode - frame type id that is used to transport it
 */
enum class FrameType(val controlFrame: Boolean, val opcode: Int) {
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

    companion object {
        private val maxOpcode = values().maxBy { it.opcode }!!.opcode

        private val byOpcodeArray = Array(maxOpcode + 1) { op -> values().singleOrNull { it.opcode == op } }

        /**
         * Find [FrameType] instance by numeric [opcode]
         * @return a [FrameType] instance or `null` of the [opcode] value is not valid
         */
        operator fun get(opcode: Int): FrameType? = if (opcode in 0..maxOpcode) byOpcodeArray[opcode] else null
    }
}
