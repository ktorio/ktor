/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package io.ktor.network.util

import kotlinx.cinterop.*
import platform.posix.*

internal actual fun <T> unpack_sockaddr_un(
    sockaddr: sockaddr,
    block: (family: UShort, path: String) -> T
): T {
    error("Address ${sockaddr.sa_family} is not supported on watchos")
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun pack_sockaddr_un(
    family: UShort,
    path: String,
    block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit
) {
    error("Address $family is not supported on watchos")
}
