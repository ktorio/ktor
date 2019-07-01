@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package io.ktor.utils.io.core

import java.nio.*

fun Output.writeFully(bb: ByteBuffer) {
    val l = bb.limit()

    writeWhile { chunk ->
        val size = minOf(bb.remaining(), chunk.writeRemaining)
        bb.limit(bb.position() + size)
        chunk.writeFully(bb)
        bb.limit(l)

        bb.hasRemaining()
    }
}
