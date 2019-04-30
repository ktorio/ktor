/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import kotlinx.io.core.*
import java.util.*

internal fun makeArray(size: Int): ByteArray = buildPacket { repeat(size) { writeByte(it.toByte()) } }.readBytes()

internal fun makeString(size: Int): String = buildString { repeat(size) { append(it.toChar()) } }
    .encodeBase64()
    .take(size)

private fun String.encodeBase64(): String = Base64.getEncoder().encodeToString(toByteArray())
