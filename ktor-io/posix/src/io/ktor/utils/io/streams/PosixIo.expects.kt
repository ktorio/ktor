/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.streams

// Fixme KT-48574

import kotlinx.cinterop.*
import platform.posix.*

public typealias _size_t = size_t // TODO: why?
public typealias _ssize_t = ssize_t // TODO: why?

public typealias socklen_tVar = UIntVarOf<UInt>
public typealias socklen_t = UInt

public expect fun recv(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int
): _ssize_t

public expect fun send(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int
): _ssize_t

public expect fun recvfrom(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: CValuesRef<socklen_tVar>?
): _ssize_t

public expect fun sendto(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: socklen_t
): _ssize_t

public expect fun read(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __nbytes: _size_t
): _ssize_t

public expect fun write(
    __fd: Int,
    __buf: CValuesRef<*>?,
    __n: _size_t
): _ssize_t

public expect fun fwrite(
    __ptr: CValuesRef<*>?,
    __size: _size_t,
    __nitems: _size_t,
    __stream: CValuesRef<FILE>?
): _size_t

public expect fun fread(
    __ptr: CValuesRef<*>?,
    __size: _size_t,
    __nitems: _size_t,
    __stream: CValuesRef<FILE>?
): _size_t

public expect val SSIZE_MAX: _ssize_t
