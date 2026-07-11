/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

/**
 * A well-formed string (no unpaired surrogates) encodes identically through `String.getBytes`,
 * which uses intrinsic-accelerated paths, so validation is only needed up front.
 * Malformed input falls back to the throwing encoder to keep the failure behavior.
 */
internal actual fun encodeUtf8ToByteArray(text: String): ByteArray {
    var index = 0
    val length = text.length
    while (index < length) {
        val character = text[index]
        if (character.isSurrogate()) {
            if (character.isHighSurrogate() && index + 1 < length && text[index + 1].isLowSurrogate()) {
                index += 2
                continue
            }
            return text.encodeToByteArray(throwOnInvalidSequence = true)
        }
        index++
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    return (text as java.lang.String).getBytes(Charsets.UTF_8)
}
