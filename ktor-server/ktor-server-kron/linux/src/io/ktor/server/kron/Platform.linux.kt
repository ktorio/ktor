/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.SO_REUSEPORT
import platform.posix.bind
import platform.posix.errno
import platform.posix.getsockname
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.usleep

internal actual fun sleepMillis(milliseconds: Long) {
    usleep((milliseconds * 1000).convert())
}

/**
 * A port the kernel picks, held by a socket that binds it with SO_REUSEPORT and never listens: every reactor's
 * listener can bind it too, nothing else can take it until [close], and no connection is ever queued on it.
 */
internal actual class ReservedPort actual constructor() : AutoCloseable {
    private val fd = socket(AF_INET, SOCK_STREAM, 0)
    actual val port: Int

    init {
        check(fd >= 0) { "socket failed with errno $errno" }
        port = memScoped {
            val on = alloc<IntVar>().apply { value = 1 }
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, on.ptr, sizeOf<IntVar>().convert())
            setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, on.ptr, sizeOf<IntVar>().convert())

            val address = alloc<sockaddr_in>()
            address.sin_family = AF_INET.convert()
            val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
            if (bind(fd, address.ptr.reinterpret(), length.value) != 0 ||
                getsockname(fd, address.ptr.reinterpret(), length.ptr) != 0
            ) {
                val e = errno
                platform.posix.close(fd)
                throw IllegalStateException("Could not reserve a port: errno $e")
            }

            // sin_port is in network order
            val p = address.sin_port.toInt()
            ((p and 0xFF) shl 8) or ((p ushr 8) and 0xFF)
        }
    }

    actual override fun close() {
        platform.posix.close(fd)
    }
}
