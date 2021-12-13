/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import kotlinx.cinterop.*
import platform.linux.*
import platform.posix.*

internal fun getAddressInfo(
    hostname: String,
    portInfo: Int
): List<NativeSocketAddress> = memScoped {
    val hints: CValue<addrinfo> = cValue {
        ai_family = AF_UNSPEC
        ai_socktype = SOCK_STREAM
        ai_flags = AI_PASSIVE or AI_NUMERICSERV
        ai_protocol = 0
    }

    val result = alloc<CPointerVar<addrinfo>>()
    val code = getaddrinfo(hostname, portInfo.toString(), hints, result.ptr)
    defer { freeaddrinfo(result.value) }

    when (code) {
        0 -> return result.pointed.toIpList()
        EAI_NONAME -> error("Bad hostname: $hostname")
//      EAI_ADDRFAMILY -> error("Bad address family")
        EAI_AGAIN -> error("Try again")
        EAI_BADFLAGS -> error("Bad flags")
//      EAI_BADHINTS -> error("Bad hint")
        EAI_FAIL -> error("FAIL")
        EAI_FAMILY -> error("Bad family")
//      EAI_MAX -> error("max reached")
        EAI_MEMORY -> error("OOM")
//      EAI_NODATA -> error("NO DATA")
        EAI_OVERFLOW -> error("OVERFLOW")
//      EAI_PROTOCOL -> error("PROTOCOL ERROR")
        EAI_SERVICE -> error("SERVICE ERROR")
        EAI_SOCKTYPE -> error("SOCKET TYPE ERROR")
        EAI_SYSTEM -> error("SYSTEM ERROR")
        else -> error("Unknown error: $code")
    }
}

internal fun getLocalAddress(descriptor: Int): NativeSocketAddress = memScoped {
    val address = alloc<sockaddr_storage>()
    val length: UIntVarOf<UInt> = alloc()
    length.value = sizeOf<sockaddr_storage>().convert()

    getsockname(descriptor, address.ptr.reinterpret(), length.ptr).check()

    return@memScoped address.reinterpret<sockaddr>().toNativeSocketAddress()
}

internal fun getRemoteAddress(descriptor: Int): NativeSocketAddress = memScoped {
    val address = alloc<sockaddr_storage>()
    val length: UIntVarOf<UInt> = alloc()
    length.value = sizeOf<sockaddr_storage>().convert()

    getpeername(descriptor, address.ptr.reinterpret(), length.ptr).check()

    return@memScoped address.reinterpret<sockaddr>().toNativeSocketAddress()
}

internal fun addrinfo?.toIpList(): List<NativeSocketAddress> {
    var current: addrinfo? = this
    val result = mutableListOf<NativeSocketAddress>()

    while (current != null) {
        result += current.ai_addr!!.pointed.toNativeSocketAddress()
        current = current.ai_next?.pointed
    }

    return result
}

internal fun sockaddr.toNativeSocketAddress(): NativeSocketAddress = when (sa_family.toInt()) {
    AF_INET -> {
        val address = ptr.reinterpret<sockaddr_in>().pointed
        NativeIPv4SocketAddress(address.sin_family, address.sin_addr, ntohs(address.sin_port).toInt())
    }
    AF_INET6 -> {
        val address = ptr.reinterpret<sockaddr_in6>().pointed
        NativeIPv6SocketAddress(
            address.sin6_family,
            address.sin6_addr,
            ntohs(address.sin6_port).toInt(),
            address.sin6_flowinfo,
            address.sin6_scope_id
        )
    }
    AF_UNIX -> {
        val address = ptr.reinterpret<sockaddr_un>().pointed
        NativeUnixSocketAddress(
            address.sun_family,
            address.sun_path.toKString(),
        )
    }
    else -> error("Unknown address family $sa_family")
}
