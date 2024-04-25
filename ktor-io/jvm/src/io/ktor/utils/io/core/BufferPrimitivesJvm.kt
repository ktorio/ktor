package io.ktor.utils.io.core

import kotlinx.io.*
import kotlinx.io.Buffer
import java.nio.*

/**
 * Write [source] buffer content moving its position.
 */
@Suppress("DEPRECATION")
public fun Buffer.writeByteBuffer(source: ByteBuffer) {
    transferFrom(source)
}
