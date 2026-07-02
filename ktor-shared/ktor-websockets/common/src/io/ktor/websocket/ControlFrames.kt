/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

/**
 * Maximum payload size of a WebSocket control frame (`Close`, `Ping`, `Pong`), in bytes.
 *
 * Per [RFC 6455 §5.5](https://datatracker.ietf.org/doc/html/rfc6455#section-5.5) control frames
 * must carry a payload of 125 bytes or fewer, otherwise the peer treats it as a protocol violation.
 */
internal const val MAX_CONTROL_FRAME_PAYLOAD_SIZE: Int = 125

/**
 * Maximum size of a [CloseReason] message, in UTF-8 bytes.
 *
 * A `Close` frame payload is the 2-byte close code followed by the UTF-8-encoded message, so the
 * message may occupy at most [MAX_CONTROL_FRAME_PAYLOAD_SIZE] minus the code size.
 */
internal const val MAX_CLOSE_REASON_MESSAGE_SIZE: Int = MAX_CONTROL_FRAME_PAYLOAD_SIZE - Short.SIZE_BYTES

/**
 * Validates that a control frame does not exceed [MAX_CONTROL_FRAME_PAYLOAD_SIZE].
 * Non-control frames are not checked.
 *
 * @throws IllegalArgumentException if a control frame payload is larger than [MAX_CONTROL_FRAME_PAYLOAD_SIZE].
 */
internal fun Frame.validateSize() {
    require(!frameType.controlFrame || data.size <= MAX_CONTROL_FRAME_PAYLOAD_SIZE) {
        "Control frames must not exceed $MAX_CONTROL_FRAME_PAYLOAD_SIZE bytes per RFC 6455, " +
            "but $frameType frame has ${data.size} bytes"
    }
}

/**
 * UTF-8 byte length of the character at [index], treating a valid surrogate pair (whose low half is
 * at `index + 1`) as a single 4-byte code point. Matches [String.encodeToByteArray]: a lone surrogate
 * counts as the 3-byte replacement character. A returned value of `4` therefore means the character
 * spans two code units (advance the index by 2); any other value means one (advance by 1).
 */
private fun String.utf8ByteCountAt(index: Int): Int {
    val code = this[index].code
    return when {
        code < 0x80 -> 1
        code < 0x800 -> 2
        this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate() -> 4
        else -> 3
    }
}

/**
 * Number of bytes [this] string occupies when UTF-8-encoded, computed without allocating an
 * intermediate byte array (unlike `encodeToByteArray().size`).
 */
internal fun String.utf8Size(): Int {
    var size = 0
    var index = 0
    while (index < length) {
        val bytes = utf8ByteCountAt(index)
        size += bytes
        index += if (bytes == 4) 2 else 1
    }
    return size
}

/**
 * Returns unchanged string if it shorter than [maxSize] UTF-8 bytes, otherwise drops trailing characters
 * until the UTF-8 encoding fits. Truncation happens on whole characters, so multibyte characters are never split,
 * and the result stays decodable.
 */
internal fun String.utf8Truncate(maxSize: Int): String {
    var size = 0
    var index = 0
    while (index < length) {
        val bytes = utf8ByteCountAt(index)
        if (size + bytes > maxSize) break
        size += bytes
        index += if (bytes == 4) 2 else 1
    }
    return if (index == length) this else substring(0, index)
}
