/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.streams

import kotlinx.cinterop.*
import platform.posix.*

public actual val SSIZE_MAX: _ssize_t = platform.posix.SSIZE_MAX.convert()

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual typealias KX_SOCKADDR_LEN = Int

@Suppress("ACTUAL_TYPE_ALIAS_NOT_TO_CLASS", "ACTUAL_WITHOUT_EXPECT")
public actual typealias KX_SOCKADDR_LENVar = IntVar

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual typealias KX_SOCKET = ULong
public actual fun kx_internal_is_non_blocking(fileDescriptor: FileDescriptor): Int = 0

public actual fun recv(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int
): _ssize_t = platform.posix.recv(
    __fd,
    __buf as CValuesRef<ByteVar>?, // TODO: Is it safe to do so?
    __n.convert(),
    __flags
).convert()

public actual fun send(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int
): _ssize_t = platform.posix.send(
    __fd,
    __buf as CValuesRef<ByteVar>?, // TODO: Is it safe to do so?
    __n.convert(),
    __flags
).convert()

public actual fun recvfrom(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: CValuesRef<KX_SOCKADDR_LENVar>?
): _ssize_t = platform.posix.recvfrom(
    __fd,
    __buf as CValuesRef<ByteVar>?, // TODO: Is it safe to do so?
    __n.convert(),
    __flags,
    __addr,
    __addr_len as CValuesRef<IntVar> // TODO: Is it safe to do so?
).convert()

//    memScoped {
//
//    val addr_len: IntVar?
//    if (__addr_len != null) {
//        addr_len = alloc()
//        addr_len.value = __addr_len.getPointer(this).pointed.value.toInt()
//    } else {
//        addr_len = null
//    }
//
//}

public actual fun sendto(
    __fd: KX_SOCKET,
    __buf: CValuesRef<*>?,
    __n: _size_t,
    __flags: Int,
    __addr: CValuesRef<sockaddr>?,
    __addr_len: KX_SOCKADDR_LEN
): _ssize_t = platform.posix.sendto(
    __fd,
    __buf as CValuesRef<ByteVar>?, // TODO: Is it safe to do so?
    __n.convert(),
    __flags,
    __addr,
    __addr_len.convert()
).convert()

public actual fun read(
    __fd: FileDescriptor,
    __buf: CValuesRef<*>?,
    __nbytes: _size_t
): _ssize_t = platform.posix.read(__fd, __buf, __nbytes.convert()).convert()

public actual fun write(
    __fd: FileDescriptor,
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

// TODO: Move to test sourceset

public actual fun socket(
    __domain: Int,
    __type: Int,
    __protocol: Int
): KX_SOCKET = platform.posix.socket(__domain, __type, __protocol)

public actual fun close_socket(socket: KX_SOCKET) {
    closesocket(socket)
}

public actual fun connect(
    __fd: KX_SOCKET,
    __addr: CValuesRef<sockaddr>?,
    __len: KX_SOCKADDR_LEN
): Int = platform.posix.connect(__fd, __addr, __len)

public actual fun set_no_delay(socket: KX_SOCKET) {
    setsockopt(socket, IPPROTO_TCP, TCP_NODELAY, "\u0001", sizeOf<IntVar>().convert())
}
