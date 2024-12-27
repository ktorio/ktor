/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package io.ktor.network.util

import kotlinx.cinterop.*
import platform.linux.inet_ntop
import platform.linux.sockaddr_un
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_inet_ntop(
    family: Int,
    src: CPointer<*>?,
    dst: CPointer<ByteVar>?,
    size: socklen_t
): CPointer<ByteVar>? = inet_ntop(family, src, dst, size)

@OptIn(ExperimentalForeignApi::class)
internal actual fun <T> unpack_sockaddr_un(
    sockaddr: sockaddr,
    block: (family: UShort, path: String) -> T
): T {
    val address = sockaddr.ptr.reinterpret<sockaddr_un>().pointed
    return block(address.sun_family, address.sun_path.toKString())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun pack_sockaddr_un(
    family: UShort,
    path: String,
    block: (address: CPointer<sockaddr>, size: socklen_t) -> Unit
) {
    cValue<sockaddr_un> {
        strcpy(sun_path, path)
        sun_family = family

        block(ptr.reinterpret(), sizeOf<sockaddr_un>().convert())
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_recvfrom(
    __fd: Int,
    __buf: CValuesRef<ByteVar>?,
    __n: UInt,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: CPointer<UIntVar>?
): Int {
    return recvfrom(__fd, __buf, __n.convert(), __flags, __addr, __addr_len).convert()
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_accept(__fd: Int, __addr: CValuesRef<sockaddr>?, __addr_len: CPointer<UIntVar>?): Int {
    return accept(__fd, __addr, __addr_len)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_getsockopt(
    __fd: Int,
    __level: Int,
    __optname: Int,
    __optval: CPointer<*>?,
    __optlen: CPointer<UIntVar>?
): Int {
    return getsockopt(__fd, __level, __optname, __optval, __optlen)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_getsockname(
    __fd: Int,
    __addr: CValuesRef<sockaddr>?,
    __len: CPointer<UIntVar>?
): Int {
    return getsockname(__fd.convert(), __addr, __len?.reinterpret())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun ktor_getpeername(
    __fd: Int,
    __addr: CValuesRef<sockaddr>?,
    __len: CPointer<UIntVar>?
): Int {
    return getpeername(__fd.convert(), __addr, __len?.reinterpret())
}
