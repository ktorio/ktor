/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmMultifileClass
@file:JvmName("StringsKt")

package io.ktor.utils.io.core

import io.ktor.utils.io.charsets.Charset
import kotlinx.io.InternalIoApi
import kotlinx.io.Source
import kotlinx.io.readString

@OptIn(InternalIoApi::class)
public actual fun Source.readText(charset: Charset, max: Int): String =
    when (max) {
        Int.MAX_VALUE -> readString(charset)
        else -> readString(minOf(buffer.size, max.toLong()), charset)
    }
