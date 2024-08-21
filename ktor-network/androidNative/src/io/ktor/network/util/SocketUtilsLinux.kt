/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import io.ktor.network.interop.*
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_inet_ntop(
    family: Int,
    src: CPointer<*>?,
    dst: CPointer<ByteVar>?,
    size: UInt
): CPointer<ByteVar>? = inet_ntop(family, src, dst, size.convert())

internal actual fun <T> unpack_sockaddr_un(
    sockaddr: sockaddr,
    block: (family: UShort, path: String) -> T
): T {
    error("Address ${sockaddr.sa_family} is not supported on ios")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun pack_sockaddr_un(
    family: UShort,
    path: String,
    block: (address: CPointer<sockaddr>, size: UInt) -> Unit
) {
    error("Address $family is not supported on ios")
}
