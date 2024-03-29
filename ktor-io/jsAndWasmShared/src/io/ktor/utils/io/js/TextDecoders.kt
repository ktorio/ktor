package io.ktor.utils.io.js

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

@Deprecated(
    "Use readText with charset instead",
    ReplaceWith(
        "readText(Charsets.forName(encoding), max)",
        "io.ktor.utils.io.core.readText",
        "io.ktor.utils.io.charsets.Charset"
    )
)
public fun ByteReadPacket.readText(encoding: String, max: Int = Int.MAX_VALUE): String =
    readText(Charsets.forName(encoding), max)

@Deprecated(
    "Use readText with charset instead",
    ReplaceWith(
        "readText(out, Charsets.forName(encoding), max)",
        "io.ktor.utils.io.core.readText",
        "io.ktor.utils.io.charsets.Charset"
    )
)
public fun ByteReadPacket.readText(encoding: String = "UTF-8", out: Appendable, max: Int = Int.MAX_VALUE): Int {
    return readText(out, Charsets.forName(encoding), max)
}

internal inline fun <R> decodeWrap(block: () -> R): R {
    try {
        return block()
    } catch (t: Throwable) {
        throw MalformedInputException("Failed to decode bytes: ${t.message ?: "no cause provided"}")
    }
}
