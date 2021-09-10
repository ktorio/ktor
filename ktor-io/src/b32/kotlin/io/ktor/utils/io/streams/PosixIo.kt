/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.streams

import kotlinx.cinterop.*
import platform.posix.*

public actual val SSIZE_MAX: _ssize_t = platform.posix.SSIZE_MAX

public actual fun recv(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int
): _ssize_t = platform.posix.recv(__fd, __buf, __n, __flags)

public actual fun send(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int
): _ssize_t = platform.posix.send(__fd, __buf, __n, __flags)

public actual fun recvfrom(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: CValuesRef<KX_SOCKADDR_LENVar>?
): _ssize_t = platform.posix.recvfrom(__fd, __buf, __n, __flags, __addr, __addr_len)

public actual fun sendto(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: KX_SOCKADDR_LEN
): _ssize_t = platform.posix.sendto(__fd, __buf, __n, __flags, __addr, __addr_len)

public actual fun read(
    __fd: FileDescriptor,
    __buf: CValuesRef<*>?,
    __nbytes: _size_t
): _ssize_t = platform.posix.read(__fd, __buf, __nbytes)

public actual fun write(
    __fd: FileDescriptor,
    __buf: CValuesRef<*>?,
    __n: _size_t
): _ssize_t = platform.posix.write(__fd, __buf, __n)

public actual fun fwrite(
    __ptr: CValuesRef<*>?,
    __size: _size_t,
    __nitems: _size_t,
    __stream: CValuesRef<FILE>?
): _size_t = platform.posix.fwrite(__ptr, __size, __nitems, __stream)

public actual fun fread(
    __ptr: CValuesRef<*>?,
    __size: _size_t,
    __nitems: _size_t,
    __stream: CValuesRef<FILE>?
): _size_t = platform.posix.fread(__ptr, __size, __nitems, __stream)
