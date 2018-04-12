package io.ktor.http.cio.websocket

/**
 * Websocket close reason
 * @property code - close reason code as per RFC 6455, recommended to be one of [CloseReason.Codes]
 */
data class CloseReason(val code: Short, val message: String) {
    constructor(code: Codes, message: String) : this(code.code, message)

    /**
     * A enum value for this [code]
     */
    val knownReason: Codes?
        get() = Codes.byCode(code)

    override fun toString(): String {
        return "CloseReason(reason=${knownReason ?: code}, message=$message)"
    }

    /**
     * Standard close reason codes
     *
     * see https://tools.ietf.org/html/rfc6455#section-7.4 for list of codes
     */
    enum class Codes(val code: Short) {
        NORMAL(1000),
        GOING_AWAY(1001),
        PROTOCOL_ERROR(1002),
        CANNOT_ACCEPT(1003),
        NOT_CONSISTENT(1007),
        VIOLATED_POLICY(1008),
        TOO_BIG(1009),
        NO_EXTENSION(1010),
        UNEXPECTED_CONDITION(1011),
        SERVICE_RESTART(1012),
        TRY_AGAIN_LATER(1013);

        companion object {
            private val byCodeMap = values().associateBy { it.code }

            /**
             * Get enum value by close reason code
             * @return enum instance or null if [code] is not in standard
             */
            fun byCode(code: Short) = byCodeMap[code]
        }
    }
}