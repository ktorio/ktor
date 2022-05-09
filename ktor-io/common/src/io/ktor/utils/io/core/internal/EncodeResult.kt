/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core.internal

import io.ktor.utils.io.bits.*
import kotlin.jvm.*

@JvmInline
internal value class EncodeResult(val value: Int) {
    constructor(characters: UShort, bytes: UShort) : this(characters.toInt() shl 16 or bytes.toInt())

    val characters: UShort get() = value.highShort.toUShort()
    val bytes: UShort get() = value.lowShort.toUShort()

    operator fun component1(): UShort = characters
    operator fun component2(): UShort = bytes
}
