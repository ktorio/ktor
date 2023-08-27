/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.util

fun zeroByteArray(size: Int): ByteArray = ByteArray(size) { 0x00 }

fun zeroByteArray(size: Long): ByteArray = ByteArray(size.toInt()) { 0x00 }
