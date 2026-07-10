/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.decode
import kotlinx.io.InternalIoApi
import kotlinx.io.Source
import kotlinx.io.readString
import kotlin.math.min

@OptIn(InternalIoApi::class)
public actual fun Source.readText(charset: Charset, max: Int): String {
    if (charset == Charsets.UTF_8) {
        if (max == Int.MAX_VALUE) return readString()
        val count = min(buffer.size, max.toLong())
        return readString(count)
    }

    return charset.newDecoder().decode(this, max)
}
