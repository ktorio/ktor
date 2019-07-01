package io.ktor.utils.io.core

import io.ktor.utils.io.charsets.*

/**
 * Create an instance of [String] from the specified [bytes] range starting at [offset] and bytes [length]
 * interpreting characters in the specified [charset].
 */
@Suppress("NOTHING_TO_INLINE", "FunctionName")
actual inline fun String(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String =
        java.lang.String(bytes, offset, length, charset) as String


internal actual fun String.getCharsInternal(dst: CharArray, dstOffset: Int) {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    (this as java.lang.String).getChars(0, length, dst, dstOffset)
}
