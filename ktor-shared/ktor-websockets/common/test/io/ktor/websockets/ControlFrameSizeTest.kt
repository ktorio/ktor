/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websockets

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.MAX_CLOSE_REASON_MESSAGE_SIZE
import io.ktor.websocket.MAX_CONTROL_FRAME_PAYLOAD_SIZE
import io.ktor.websocket.utf8Size
import io.ktor.websocket.utf8Truncate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ControlFrameSizeTest {

    @Test
    fun closeReasonAcceptsMessageAtLimit() {
        val message = "a".repeat(MAX_CLOSE_REASON_MESSAGE_SIZE) // 123 ASCII bytes
        val reason = CloseReason(CloseReason.Codes.NORMAL, message)
        assertEquals(message, reason.message)

        // The resulting close frame occupies the control-frame limit exactly.
        assertEquals(MAX_CONTROL_FRAME_PAYLOAD_SIZE, Frame.Close(reason).data.size)
    }

    @Test
    fun closeReasonRejectsTooLongMessage() {
        val message = "a".repeat(MAX_CLOSE_REASON_MESSAGE_SIZE + 1)
        assertFailsWith<IllegalArgumentException> {
            CloseReason(CloseReason.Codes.NORMAL, message)
        }
    }

    @Test
    fun closeReasonRejectsTooLongUnicodeMessage() {
        // Each 'я' is 2 UTF-8 bytes, so 62 of them = 124 bytes > limit.
        val message = "я".repeat(62)
        assertFailsWith<IllegalArgumentException> {
            CloseReason(CloseReason.Codes.NORMAL, message)
        }
    }

    @Test
    fun controlFramesRejectOversizePayload() {
        val tooBig = ByteArray(MAX_CONTROL_FRAME_PAYLOAD_SIZE + 1)
        assertFailsWith<IllegalArgumentException> { Frame.Ping(tooBig) }
        assertFailsWith<IllegalArgumentException> { Frame.Pong(tooBig) }
        assertFailsWith<IllegalArgumentException> { Frame.Close(tooBig) }
    }

    @Test
    fun controlFramesAcceptPayloadAtLimit() {
        val atLimit = ByteArray(MAX_CONTROL_FRAME_PAYLOAD_SIZE)
        assertEquals(MAX_CONTROL_FRAME_PAYLOAD_SIZE, Frame.Ping(atLimit).data.size)
        assertEquals(MAX_CONTROL_FRAME_PAYLOAD_SIZE, Frame.Pong(atLimit).data.size)
        assertEquals(MAX_CONTROL_FRAME_PAYLOAD_SIZE, Frame.Close(atLimit).data.size)
    }

    @Test
    fun dataFramesAreNotSizeLimited() {
        val big = ByteArray(MAX_CONTROL_FRAME_PAYLOAD_SIZE + 1000)
        assertEquals(big.size, Frame.Text(true, big).data.size)
        assertEquals(big.size, Frame.Binary(true, big).data.size)
    }

    @Test
    fun truncationFitsWithinLimit() {
        val truncated = "a".repeat(500).utf8Truncate(10)
        assertEquals(10, truncated.encodeToByteArray().size)
        // A truncated reason must be constructible as a valid close reason.
        assertEquals(12, Frame.Close(CloseReason(1000, truncated)).data.size)
    }

    @Test
    fun truncationDoesNotSplitMultibyteCharacters() {
        // 122 ASCII bytes + a 2-byte character would be 124 bytes; the trailing character must be dropped whole.
        val message = "a".repeat(4) + "я"
        val truncated = message.utf8Truncate(5)

        assertEquals("a".repeat(4), truncated)
        assertTrue(truncated.encodeToByteArray().size <= MAX_CLOSE_REASON_MESSAGE_SIZE)
    }

    @Test
    fun utf8SizeMatchesEncoder() {
        // Covers 1-, 2-, 3-byte characters and a 4-byte surrogate pair (😀) for well-formed text.
        val samples = listOf("", "ascii", "я".repeat(3), "日本語", "a😀b", "mix: aя日😀")
        for (sample in samples) {
            sample.encodeToByteArray()
            assertEquals(sample.encodeToByteArray().size, sample.utf8Size(), "utf8Size mismatch for \"$sample\"")
        }
    }

    @Test
    fun utf8SizeNeverUnderestimatesForLoneSurrogate() {
        // A lone surrogate is malformed; the platform encoder's replacement-byte count is not fixed.
        // utf8Size must never underestimate, so the close-frame size guarantee always holds.
        val loneSurrogate = "\uD83D"
        assertTrue(loneSurrogate.utf8Size() >= loneSurrogate.encodeToByteArray().size)
    }

    @Test
    fun truncationOfMultibyteOnlyMessageStaysDecodable() {
        val truncated = "я".repeat(200).utf8Truncate(MAX_CLOSE_REASON_MESSAGE_SIZE)
        val bytes = truncated.encodeToByteArray()
        assertTrue(bytes.size <= MAX_CLOSE_REASON_MESSAGE_SIZE)
        // No replacement characters: re-decoding yields the same string (no split multibyte char).
        assertEquals(truncated, bytes.decodeToString())
        assertTrue(truncated.all { it == 'я' })
    }
}
