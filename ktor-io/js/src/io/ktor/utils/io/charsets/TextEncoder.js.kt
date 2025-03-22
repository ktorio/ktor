/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import org.khronos.webgl.*

internal external class TextEncoder {
    val encoding: String

    fun encode(input: String): Uint8Array
}
