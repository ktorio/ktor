/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.streams

import kotlinx.cinterop.*
import platform.posix.*

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual typealias KX_SOCKADDR_LEN = UInt

@Suppress("ACTUAL_TYPE_ALIAS_NOT_TO_CLASS", "ACTUAL_WITHOUT_EXPECT")
public actual typealias KX_SOCKADDR_LENVar = UIntVar

public actual typealias KX_SOCKET = Int

/**
 *  From sockets.def:
 *   static inline int kx_internal_is_non_blocking(int fd) {
 *       int flags = fcntl(fd, F_GETFL, 0);
 *       return flags & O_NONBLOCK;
 *   }
 */
public actual fun kx_internal_is_non_blocking(fileDescriptor: FileDescriptor): Int {
    val flags = fcntl(fileDescriptor, F_GETFL, 0)
    return flags and O_NONBLOCK
}

// TODO: Move to test sourceset

public actual fun socket(
    __domain: Int,
    __type: Int,
    __protocol: Int
): KX_SOCKET = platform.posix.socket(__domain, __type, __protocol)

public actual fun close_socket(socket: KX_SOCKET) {
    close(socket)
}

public actual fun connect(
    __fd: KX_SOCKET,
    __addr: CValuesRef<sockaddr>?,
    __len: KX_SOCKADDR_LEN
): Int = platform.posix.connect(__fd, __addr, __len)


public actual fun set_no_delay(socket: KX_SOCKET): Unit = memScoped {
    // TODO: should we allocate all the time this integer?
    val one = alloc<IntVar> { value = 1 }
    setsockopt(socket, IPPROTO_TCP, TCP_NODELAY, one.ptr, sizeOf<IntVar>().convert())
}
