/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.streams

import kotlinx.cinterop.*
import platform.posix.*

public actual val SSIZE_MAX: _ssize_t = platform.posix.SSIZE_MAX.convert()

public actual fun recv(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int
): _ssize_t = platform.posix.recv(__fd, __buf, __n.convert(), __flags).convert()

public actual fun send(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int
): _ssize_t = platform.posix.send(__fd, __buf, __n.convert(), __flags).convert()

public actual fun recvfrom(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: CValuesRef<KX_SOCKADDR_LENVar>?
): _ssize_t = platform.posix.recvfrom(
    __fd,
    __buf,
    __n.convert(),
    __flags,
    __addr,
    __addr_len
).convert()

public actual fun sendto(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: KX_SOCKADDR_LEN
): _ssize_t = platform.posix.sendto(
    __fd,
    __buf,
    __n.convert(),
    __flags,
    __addr,
    __addr_len as socklen_t
).convert()
