/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.streams

import kotlinx.cinterop.*
import platform.posix.*

public actual fun recv(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int
): _ssize_t = platform.posix.recv(__fd, __buf, __n.convert(), __flags).convert()

public actual fun send(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int
): _ssize_t = platform.posix.send(__fd, __buf, __n.convert(), __flags).convert()

public actual fun recvfrom(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: CValuesRef<socklen_tVar>?
): _ssize_t = platform.posix.recvfrom(
    __fd,
    __buf,
    __n.convert(),
    __flags,
    __addr,
    __addr_len
).convert()

public actual fun sendto(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: socklen_t
): _ssize_t = platform.posix.sendto(
    __fd,
    __buf,
    __n.convert(),
    __flags,
    __addr,
    __addr_len
).convert()

public actual fun read(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __nbytes: _size_t
): _ssize_t = platform.posix.read(__fd, __buf, __nbytes.convert()).convert()

public actual fun write(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __n: _size_t
): _ssize_t = platform.posix.write(__fd, __buf, __n.convert()).convert()

public actual fun fwrite(
    __ptr: CValuesRef<*>?,
    __size: _size_t,
    __nitems: _size_t,
    __stream: CValuesRef<FILE>?
): _size_t = platform.posix.fwrite(__ptr, __size.convert(), __nitems.convert(), __stream).convert()

public actual fun fread(
    __ptr: CValuesRef<*>?,
    __size: _size_t,
    __nitems: _size_t,
    __stream: CValuesRef<FILE>?
): _size_t = platform.posix.fread(__ptr, __size.convert(), __nitems.convert(), __stream).convert()

public actual val SSIZE_MAX: _ssize_t = platform.posix.SSIZE_MAX.convert()
