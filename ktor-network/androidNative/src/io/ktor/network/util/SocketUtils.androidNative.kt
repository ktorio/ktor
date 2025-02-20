/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

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

@OptIn(ExperimentalForeignApi::class)
internal actual fun <T> unpack_sockaddr_un(
    sockaddr: sockaddr,
    block: (family: UShort, path: String) -> T
): T {
    val address = sockaddr.ptr.reinterpret<sockaddr_un>().pointed
    return block(address.sun_family.convert(), address.sun_path.toKString())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun pack_sockaddr_un(
    family: UShort,
    path: String,
    block: (address: CPointer<sockaddr>, size: UInt) -> Unit
) {
    cValue<sockaddr_un> {
        strcpy(sun_path, path)
        sun_family = family.convert()

        block(ptr.reinterpret(), sizeOf<sockaddr_un>().convert())
    }
}
