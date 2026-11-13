/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.lang.invoke.MethodHandle

internal actual fun sleepMillis(milliseconds: Long) {
    Thread.sleep(milliseconds)
}

// The socket calls Linux's ReservedPort makes, through FFM: the JDK's sockets cannot bind without listening.
internal actual class ReservedPort actual constructor() : AutoCloseable {
    private val fd: Int = SOCKET.invokeExact(AF_INET, SOCK_STREAM, 0) as Int
    actual val port: Int

    init {
        check(fd >= 0) { "socket failed" }
        port = Arena.ofConfined().use { arena ->
            val on = arena.allocate(JAVA_INT)
            on.set(JAVA_INT, 0, 1)
            setsockopt(fd, SO_REUSEADDR, on)
            setsockopt(fd, SO_REUSEPORT, on)

            val address = arena.allocate(SOCKADDR_IN_SIZE.toLong())
            address.set(JAVA_SHORT, 0, AF_INET.toShort())
            val length = arena.allocate(JAVA_INT)
            length.set(JAVA_INT, 0, SOCKADDR_IN_SIZE)
            if ((BIND.invokeExact(fd, address, SOCKADDR_IN_SIZE) as Int) != 0 ||
                (GETSOCKNAME.invokeExact(fd, address, length) as Int) != 0
            ) {
                close()
                throw IllegalStateException("Could not reserve a port")
            }

            // sin_port is in network order
            val p = address.get(JAVA_SHORT, 2).toInt() and 0xFFFF
            ((p and 0xFF) shl 8) or ((p ushr 8) and 0xFF)
        }
    }

    actual override fun close() {
        CLOSE.invokeExact(fd) as Int
    }

    private companion object {
        const val AF_INET = 2
        const val SOCK_STREAM = 1
        const val SOL_SOCKET = 1
        const val SO_REUSEADDR = 2
        const val SO_REUSEPORT = 15
        const val SOCKADDR_IN_SIZE = 16

        private val linker = Linker.nativeLinker()

        private fun bind(name: String, descriptor: FunctionDescriptor): MethodHandle =
            linker.downcallHandle(linker.defaultLookup().find(name).orElseThrow(), descriptor)

        val SOCKET = bind("socket", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT))
        val SETSOCKOPT =
            bind("setsockopt", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT))
        val BIND = bind("bind", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT))
        val GETSOCKNAME = bind("getsockname", FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS))
        val CLOSE = bind("close", FunctionDescriptor.of(JAVA_INT, JAVA_INT))

        fun setsockopt(fd: Int, name: Int, value: MemorySegment) {
            SETSOCKOPT.invokeExact(fd, SOL_SOCKET, name, value, 4) as Int
        }
    }
}
