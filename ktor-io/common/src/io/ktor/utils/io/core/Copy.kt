package io.ktor.utils.io.core

/**
 * Copy all bytes to the [output].
 * Depending on actual input and output implementation it could be zero-copy or copy byte per byte.
 * All regular types such as [ByteReadPacket], [BytePacketBuilder], [Input] and [Output]
 * are always optimized so no bytes will be copied.
 */
public fun Input.copyTo(output: Output): Long {
    var copied = 0L
    do {
        val head = stealAll()
        if (head == null) {
            if (prepareRead(1) == null) break
            continue
        }

        copied += head.remainingAll()
        output.appendChain(head)
    } while (true)

    return copied
}
